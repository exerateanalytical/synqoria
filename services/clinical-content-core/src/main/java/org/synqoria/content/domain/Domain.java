package org.synqoria.content.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M30 domain: permissions, state machines, import validation and the advisory rule engine. */
public final class Domain {
 private Domain(){}

 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }
 /** Rejection whose evidence (e.g. a rejected import record) must commit before the error is returned. */
 public static final class CommittedRejection extends Violation {
  public CommittedRejection(String code,List<String> details){super(code,details);}
 }

 public static final class Perm {
  private Perm(){}
  public static final String TERM_READ="m30:terminology_read", TERM_IMPORT="m30:terminology_import",
   MAP_PROPOSE="m30:mapping_propose", MAP_APPROVE="m30:mapping_approve",
   FORM_READ="m30:formulary_read", FORM_EDIT="m30:formulary_edit", FORM_APPROVE="m30:formulary_approve",
   PKG_READ="m30:package_read", PKG_AUTHOR="m30:package_author", PKG_VALIDATE="m30:package_validate",
   REVIEW_CLINICAL="m30:review_clinical", REVIEW_POLICY="m30:review_policy", APPROVAL_REVOKE="m30:approval_revoke",
   ACT_READ="m30:activation_read", ACT_MANAGE="m30:activation_manage", ROLLBACK="m30:rollback";
  public static final Set<String> ALL=Set.of(TERM_READ,TERM_IMPORT,MAP_PROPOSE,MAP_APPROVE,FORM_READ,FORM_EDIT,FORM_APPROVE,
   PKG_READ,PKG_AUTHOR,PKG_VALIDATE,REVIEW_CLINICAL,REVIEW_POLICY,APPROVAL_REVOKE,ACT_READ,ACT_MANAGE,ROLLBACK);
  /** Institutional clinical authority only; content editors and importers must never hold these. */
  public static final Set<String> CLINICAL_ONLY=Set.of(MAP_APPROVE,FORM_APPROVE,REVIEW_CLINICAL,REVIEW_POLICY,APPROVAL_REVOKE);
 }
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
 }

 public enum PackageStatus{
  DRAFT,VALIDATED,VALIDATION_FAILED,IN_REVIEW,APPROVED,REJECTED,REVOKED,RETIRED;
  public boolean canMoveTo(PackageStatus n){
   return switch(this){
    case DRAFT->n==VALIDATED||n==VALIDATION_FAILED;
    case VALIDATED->n==IN_REVIEW;
    case IN_REVIEW->n==APPROVED||n==REJECTED||n==REVOKED;
    case APPROVED->n==REVOKED||n==RETIRED;
    default->false;
   };
  }
 }
 public enum FormularyStatus{
  DRAFT,SUBMITTED,APPROVED,REJECTED,SUPERSEDED,WITHDRAWN;
  public boolean canMoveTo(FormularyStatus n){
   return switch(this){
    case DRAFT->n==SUBMITTED||n==WITHDRAWN;
    case SUBMITTED->n==APPROVED||n==REJECTED;
    case APPROVED->n==SUPERSEDED||n==WITHDRAWN;
    default->false;
   };
  }
 }

 private static final Pattern CODE=Pattern.compile("[A-Za-z0-9._\\-]{1,64}");
 private static final Pattern URI=Pattern.compile("[a-z][a-z0-9+.\\-]*:[A-Za-z0-9._~:/%\\-]{2,255}");
 public static boolean validCode(String c){return c!=null&&CODE.matcher(c).matches();}
 public static boolean validUri(String c){return c!=null&&URI.matcher(c).matches();}
 public static <E extends Enum<E>> E parse(Class<E> t,String v,String field){
  try{return Enum.valueOf(t,v);}catch(RuntimeException e){throw new Violation("invalid_"+field);}
 }
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 // ------------------------------------------------------------------ code-system import validation
 public record ImportConcept(String code,String status,String displayEn,String displayFr,String parent,List<String> synonyms){}
 public static final int MAX_CONCEPTS=50_000;

 /** Canonical content checksum: SHA-256 over concepts sorted by code, one pipe-delimited line each. */
 public static String contentChecksum(Collection<ImportConcept> concepts){
  List<ImportConcept> sorted=new ArrayList<>(concepts);
  sorted.sort(Comparator.comparing(ImportConcept::code));
  List<String> lines=new ArrayList<>();
  for(ImportConcept c:sorted)
   lines.add(c.code()+"|"+c.status()+"|"+c.displayEn()+"|"+(c.displayFr()==null?"":c.displayFr())+"|"+(c.parent()==null?"":c.parent()));
  return sha256(String.join("\n",lines));
 }
 /** All problems, not just the first: schema, duplicates, dangling or cyclic parents, statuses, silent removals. */
 public static List<String> validateImport(Collection<ImportConcept> concepts,Set<String> previousCodes){
  List<String> errors=new ArrayList<>();
  if(concepts==null||concepts.isEmpty())return List.of("EMPTY_IMPORT");
  if(concepts.size()>MAX_CONCEPTS)return List.of("IMPORT_TOO_LARGE");
  Map<String,ImportConcept> byCode=new HashMap<>();
  for(ImportConcept c:concepts){
   if(!validCode(c.code())){errors.add("INVALID_CODE");continue;}
   if(byCode.putIfAbsent(c.code(),c)!=null)errors.add("DUPLICATE_CODE:"+c.code());
   if(!List.of("ACTIVE","INACTIVE","DEPRECATED").contains(c.status()))errors.add("INVALID_STATUS:"+c.code());
   if(c.displayEn()==null||c.displayEn().isBlank()||c.displayEn().length()>500)errors.add("INVALID_DISPLAY_EN:"+c.code());
   if(c.displayFr()!=null&&c.displayFr().length()>500)errors.add("INVALID_DISPLAY_FR:"+c.code());
   if(c.synonyms()!=null&&(c.synonyms().size()>50||c.synonyms().stream().anyMatch(s->s==null||s.isBlank()||s.length()>300)))
    errors.add("INVALID_SYNONYM:"+c.code());
  }
  for(ImportConcept c:byCode.values()){
   if(c.parent()==null)continue;
   if(!byCode.containsKey(c.parent())){errors.add("DANGLING_PARENT:"+c.code());continue;}
   Set<String> seen=new HashSet<>();String cur=c.code();
   while(cur!=null&&byCode.containsKey(cur)){
    if(!seen.add(cur)){errors.add("CYCLE:"+c.code());break;}
    cur=byCode.get(cur).parent();
   }
  }
  if(previousCodes!=null)
   for(String old:previousCodes)if(!byCode.containsKey(old))errors.add("REMOVED_WITHOUT_DEPRECATION:"+old);
  Collections.sort(errors);
  return errors;
 }

 // ------------------------------------------------------------------ advisory rule engine
 public enum Verdict{FIRED,NOT_FIRED,INDETERMINATE}
 public record Advice(String ruleId,Verdict verdict,String code,String severity){}
 private static final Set<String> OPS=Set.of("eq","neq","gt","gte","lt","lte","in","contains","exists");
 private static final Set<String> SEVERITIES=Set.of("INFO","WARNING","CRITICAL");
 private static final Pattern FIELD=Pattern.compile("[a-z][a-z0-9_]{0,40}(\\.[a-z][a-z0-9_]{0,40}){0,4}");
 private static final Pattern RULE_ID=Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]{0,63}");
 public static final int MAX_RULES=500;

 @SuppressWarnings("unchecked")
 private static List<Map<String,Object>> listOfMaps(Object o){
  if(!(o instanceof List<?> l))return null;
  List<Map<String,Object>> out=new ArrayList<>();
  for(Object x:l){if(!(x instanceof Map<?,?>))return null;out.add((Map<String,Object>)x);}
  return out;
 }
 @SuppressWarnings("unchecked")
 private static Map<String,Object> mapOf(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:null;}

 /** Declarative schema check. Returns every problem; empty means structurally valid. */
 public static List<String> validateDefinition(Map<String,Object> def){
  List<String> e=new ArrayList<>();
  if(def==null)return List.of("MISSING_DEFINITION");
  if(!(def.get("schemaVersion") instanceof Number n)||n.intValue()!=1)e.add("UNSUPPORTED_SCHEMA_VERSION");
  var rules=listOfMaps(def.get("rules"));
  if(rules==null||rules.isEmpty()){e.add("NO_RULES");return e;}
  if(rules.size()>MAX_RULES){e.add("TOO_MANY_RULES");return e;}
  Set<String> ids=new HashSet<>();
  for(Map<String,Object> r:rules){
   if(!(r.get("id") instanceof String s)||!RULE_ID.matcher(s).matches()){e.add("INVALID_RULE_ID");continue;}
   if(!ids.add(s))e.add("DUPLICATE_RULE_ID:"+s);
   Map<String,Object> when=mapOf(r.get("when"));
   if(when==null||when.containsKey("all")==when.containsKey("any"))e.add("INVALID_WHEN:"+s);
   else{
    var conds=listOfMaps(when.containsKey("all")?when.get("all"):when.get("any"));
    if(conds==null||conds.isEmpty()||conds.size()>50)e.add("INVALID_CONDITIONS:"+s);
    else for(Map<String,Object> c:conds){
     if(!(c.get("field") instanceof String f)||!FIELD.matcher(f).matches())e.add("INVALID_FIELD:"+s);
     if(!(c.get("op") instanceof String op)||!OPS.contains(op))e.add("INVALID_OP:"+s);
     else if(!op.equals("exists")&&!c.containsKey("value"))e.add("MISSING_VALUE:"+s);
     else if(op.equals("in")&&!(c.get("value") instanceof List<?>))e.add("IN_REQUIRES_LIST:"+s);
     else if(List.of("gt","gte","lt","lte").contains(op)&&!(c.get("value") instanceof Number))e.add("NUMERIC_OP_REQUIRES_NUMBER:"+s);
    }
   }
   Map<String,Object> adv=mapOf(r.get("advise"));
   if(adv==null||!(adv.get("code") instanceof String c)||!validCode(c)||!SEVERITIES.contains(adv.get("severity")))e.add("INVALID_ADVICE:"+s);
   else{
    Map<String,Object> text=mapOf(adv.get("text"));
    if(text==null||!(text.get("en") instanceof String en)||en.isBlank()||!(text.get("fr") instanceof String fr)||fr.isBlank())
     e.add("ADVICE_TEXT_REQUIRES_EN_AND_FR:"+s);
   }
  }
  Collections.sort(e);
  return e;
 }

 private static Object lookup(Map<String,Object> facts,String path){
  Object cur=facts;
  for(String part:path.split("\\.")){
   if(!(cur instanceof Map<?,?> m)||!m.containsKey(part))return null;
   cur=m.get(part);
  }
  return cur;
 }
 private static int cmp(Object a,Object b){
  if(a instanceof Number x&&b instanceof Number y)return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()));
  return Objects.equals(a,b)?0:1;
 }
 /** Three-valued leaf: an absent fact is INDETERMINATE (null), never silently false. */
 private static Boolean leaf(Map<String,Object> c,Map<String,Object> facts){
  Object actual=lookup(facts,(String)c.get("field"));
  String op=(String)c.get("op");
  if(op.equals("exists"))return actual!=null;
  if(actual==null)return null;
  Object expected=c.get("value");
  switch(op){
   case "eq": return cmp(actual,expected)==0;
   case "neq": return cmp(actual,expected)!=0;
   case "gt": case "gte": case "lt": case "lte": {
    if(!(actual instanceof Number))return false;
    int k=cmp(actual,expected);
    return switch(op){case "gt"->k>0;case "gte"->k>=0;case "lt"->k<0;default->k<=0;};
   }
   case "in": for(Object x:(List<?>)expected)if(cmp(actual,x)==0)return true; return false;
   case "contains": if(!(actual instanceof List<?> l))return false; for(Object x:l)if(cmp(x,expected)==0)return true; return false;
   default: throw new IllegalStateException(op);
  }
 }
 /** Deterministic pure function of (definition, facts); results ordered by rule id. Advisory only. */
 public static List<Advice> evaluate(Map<String,Object> def,Map<String,Object> facts){
  List<String> problems=validateDefinition(def);
  if(!problems.isEmpty())throw new Violation("invalid_definition",problems);
  List<Advice> out=new ArrayList<>();
  for(Map<String,Object> r:listOfMaps(def.get("rules"))){
   Map<String,Object> when=mapOf(r.get("when"));
   boolean all=when.containsKey("all");
   boolean sawNull=false,anyTrue=false,anyFalse=false;
   for(Map<String,Object> c:listOfMaps(all?when.get("all"):when.get("any"))){
    Boolean b=leaf(c,facts);
    if(b==null)sawNull=true;else if(b)anyTrue=true;else anyFalse=true;
   }
   Verdict v;
   if(all)v=anyFalse?Verdict.NOT_FIRED:sawNull?Verdict.INDETERMINATE:Verdict.FIRED;
   else v=anyTrue?Verdict.FIRED:sawNull?Verdict.INDETERMINATE:Verdict.NOT_FIRED;
   Map<String,Object> adv=mapOf(r.get("advise"));
   out.add(new Advice((String)r.get("id"),v,(String)adv.get("code"),(String)adv.get("severity")));
  }
  out.sort(Comparator.comparing(Advice::ruleId));
  return out;
 }
 /** Terminology references the package depends on: [{system, code}]. */
 public static List<Map<String,String>> codeRefs(Map<String,Object> def){
  List<Map<String,String>> out=new ArrayList<>();
  var l=listOfMaps(def==null?null:def.get("codeRefs"));
  if(l==null)return out;
  for(Map<String,Object> m:l)out.add(Map.of("system",String.valueOf(m.get("system")),"code",String.valueOf(m.get("code"))));
  return out;
 }
}
