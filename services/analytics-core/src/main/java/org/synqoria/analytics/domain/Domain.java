package org.synqoria.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M28 domain: permissions, ingestion rules, the metric engine with disclosure control, and CSV safety. No I/O. */
public final class Domain {
 private Domain(){}

 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }

 public static final class Perm {
  private Perm(){}
  public static final String SOURCE_MANAGE="m28:source_manage", SOURCE_VERIFY="m28:source_verify", INGEST="m28:ingest", QUARANTINE_MANAGE="m28:quarantine_manage",
   METRIC_MANAGE="m28:metric_manage", METRIC_APPROVE="m28:metric_approve", COMPUTE="m28:compute", REBUILD="m28:rebuild",
   READ_OPERATIONS="m28:read_operations", READ_CLINICAL="m28:read_clinical", READ_FINANCIAL="m28:read_financial", CELL_AGGREGATE_READ="m28:cell_aggregate_read",
   REPORT_MANAGE="m28:report_manage", REPORT_APPROVE="m28:report_approve", REPORT_RUN="m28:report_run", REPORT_READ="m28:report_read", SCHEDULE_MANAGE="m28:schedule_manage",
   EXPORT_REQUEST="m28:export_request", EXPORT_APPROVE="m28:export_approve", EXPORT_DOWNLOAD="m28:export_download";
  public static final Set<String> ALL=Set.of(SOURCE_MANAGE,SOURCE_VERIFY,INGEST,QUARANTINE_MANAGE,METRIC_MANAGE,METRIC_APPROVE,COMPUTE,REBUILD,READ_OPERATIONS,READ_CLINICAL,READ_FINANCIAL,
   CELL_AGGREGATE_READ,REPORT_MANAGE,REPORT_APPROVE,REPORT_RUN,REPORT_READ,SCHEDULE_MANAGE,EXPORT_REQUEST,EXPORT_APPROVE,EXPORT_DOWNLOAD);
  public static final Set<String> CHECKER=Set.of(SOURCE_VERIFY,METRIC_APPROVE,REPORT_APPROVE,EXPORT_APPROVE);
  public static String readFor(String domain){
   return switch(domain){case "OPERATIONS"->READ_OPERATIONS;case "CLINICAL"->READ_CLINICAL;case "FINANCIAL"->READ_FINANCIAL;default->throw new Violation("invalid_domain");};
  }
 }
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
  public boolean has(String p){return grants.contains(p);}
 }

 private static final Pattern KEY=Pattern.compile("[a-z][a-z0-9_.-]{1,47}");
 private static final Pattern FIELD=Pattern.compile("[a-z][a-z0-9_]{0,39}");
 public static boolean validKey(String k){return k!=null&&KEY.matcher(k).matches();}
 public static boolean validField(String f){return f!=null&&FIELD.matcher(f).matches();}
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static <E extends Enum<E>> E parse(Class<E> t,String v,String field){
  try{return Enum.valueOf(t,v);}catch(RuntimeException e){throw new Violation("invalid_"+field);}
 }

 // ================================================================== ingestion validation
 /** Problems with one event against its source contract. Empty => may be applied. */
 public static List<String> validateEvent(Collection<String> requiredFields,Map<String,Object> payload,Instant occurredAt,Instant now,long maxLatenessMinutes){
  TreeSet<String> p=new TreeSet<>();
  if(payload==null||payload.isEmpty())return List.of("EMPTY_PAYLOAD");
  for(String f:requiredFields)if(payload.get(f)==null)p.add("MISSING_FIELD:"+f);
  for(var e:payload.entrySet()){
   if(!validField(e.getKey()))p.add("INVALID_FIELD_NAME");
   Object v=e.getValue();
   if(v instanceof Double||v instanceof Float)p.add("FLOAT_NOT_ALLOWED:"+e.getKey());      // amounts must be integers or decimal strings
   if(v instanceof Map||v instanceof List)p.add("NESTED_VALUE_NOT_ALLOWED:"+e.getKey());
  }
  if(occurredAt==null)p.add("MISSING_OCCURRED_AT");
  else if(occurredAt.isAfter(now.plusSeconds(300)))p.add("OCCURRED_IN_FUTURE");
  return List.copyOf(p);
 }
 public static boolean isLate(Instant occurredAt,Instant now,long maxLatenessMinutes){
  return occurredAt.plusSeconds(maxLatenessMinutes*60).isBefore(now);
 }

 // ================================================================== metric definitions
 private static final Set<String> OPS=Set.of("eq","neq","in","gt","gte","lt","lte","exists");
 @SuppressWarnings("unchecked")
 private static Map<String,Object> mapOf(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:null;}

 /** Declarative metric formula check; returns every problem. */
 @SuppressWarnings("unchecked")
 public static List<String> validateMetric(Map<String,Object> def,String domain,String unit,int minCell){
  TreeSet<String> e=new TreeSet<>();
  if(def==null)return List.of("MISSING_DEFINITION");
  if(!(def.get("schemaVersion") instanceof Number n)||n.intValue()!=1)e.add("UNSUPPORTED_SCHEMA_VERSION");
  if(!List.of("OPERATIONS","CLINICAL","FINANCIAL").contains(domain))e.add("INVALID_DOMAIN");
  if(!"FINANCIAL".equals(domain)&&minCell<5)e.add("MIN_CELL_TOO_SMALL");
  if(!List.of("COUNT","RATIO","PERCENT","AMOUNT","MINUTES").contains(unit))e.add("INVALID_UNIT");
  Map<String,Object> num=mapOf(def.get("numerator")),den=mapOf(def.get("denominator"));
  if(num==null)e.add("MISSING_NUMERATOR");else checkTerm(num,"NUMERATOR",e);
  if(den!=null)checkTerm(den,"DENOMINATOR",e);
  if(den==null&&(unit.equals("RATIO")||unit.equals("PERCENT")))e.add("RATE_REQUIRES_DENOMINATOR");
  if(def.get("groupBy")!=null&&(!(def.get("groupBy") instanceof String g)||!validField(g)))e.add("INVALID_GROUP_BY");
  if(def.get("currencyField")!=null&&(!(def.get("currencyField") instanceof String c)||!validField(c)))e.add("INVALID_CURRENCY_FIELD");
  if("AMOUNT".equals(unit)&&def.get("currencyField")==null)e.add("AMOUNT_REQUIRES_CURRENCY_FIELD");       // never sum across currencies
  if(def.get("requireFields") instanceof List<?> rf){if(rf.size()>20||rf.stream().anyMatch(x->!(x instanceof String s)||!validField(s)))e.add("INVALID_REQUIRE_FIELDS");}
  else if(def.get("requireFields")!=null)e.add("INVALID_REQUIRE_FIELDS");
  if(!(def.get("source") instanceof String s)||!validKey(s))e.add("INVALID_SOURCE");
  return List.copyOf(e);
 }
 @SuppressWarnings("unchecked")
 private static void checkTerm(Map<String,Object> t,String label,Set<String> e){
  String agg=String.valueOf(t.get("agg"));
  if(!agg.equals("COUNT")&&!agg.equals("SUM"))e.add("INVALID_AGG:"+label);
  if(agg.equals("SUM")&&!(t.get("field") instanceof String f&&validField(f)))e.add("SUM_REQUIRES_FIELD:"+label);
  if(t.get("where")!=null){
   if(!(t.get("where") instanceof List<?> w)||w.size()>20){e.add("INVALID_WHERE:"+label);return;}
   for(Object o:w){
    Map<String,Object> c=mapOf(o);
    if(c==null||!(c.get("field") instanceof String f)||!validField(f)||!(c.get("op") instanceof String op)||!OPS.contains(op)){e.add("INVALID_CONDITION:"+label);continue;}
    if(!op.equals("exists")&&!c.containsKey("value"))e.add("MISSING_VALUE:"+label);
    if(op.equals("in")&&!(c.get("value") instanceof List<?>))e.add("IN_REQUIRES_LIST:"+label);
   }
  }
 }

 // ================================================================== metric engine
 public record Fact(String entity,Instant occurredAt,Map<String,Object> data){}
 public record GroupResult(String dimension,String currency,String state,BigDecimal numerator,BigDecimal denominator,BigDecimal value,int cohort,int unknown){}

 static BigDecimal toDecimal(Object o){
  if(o instanceof BigDecimal b)return b;
  if(o instanceof Integer||o instanceof Long||o instanceof Short)return BigDecimal.valueOf(((Number)o).longValue());
  if(o instanceof String s&&s.matches("-?\\d{1,15}(\\.\\d{1,6})?"))return new BigDecimal(s);
  return null;                                  // doubles and junk are unknown, never silently coerced
 }
 private static Boolean cond(Map<String,Object> c,Map<String,Object> data){
  Object actual=data.get((String)c.get("field"));String op=(String)c.get("op");
  if(op.equals("exists"))return actual!=null;
  if(actual==null)return null;
  Object ex=c.get("value");
  return switch(op){
   case "eq"->cmp(actual,ex)==0;case "neq"->cmp(actual,ex)!=0;
   case "in"->((List<?>)ex).stream().anyMatch(x->cmp(actual,x)==0);
   default->{BigDecimal a=toDecimal(actual),b=toDecimal(ex);if(a==null||b==null)yield false;int k=a.compareTo(b);
    yield switch(op){case "gt"->k>0;case "gte"->k>=0;case "lt"->k<0;default->k<=0;};}
  };
 }
 private static int cmp(Object a,Object b){
  BigDecimal x=toDecimal(a),y=toDecimal(b);
  if(x!=null&&y!=null)return x.compareTo(y);
  return String.valueOf(a).equals(String.valueOf(b))?0:1;
 }
 @SuppressWarnings("unchecked")
 private static boolean matches(Map<String,Object> term,Map<String,Object> data){
  if(term==null||term.get("where")==null)return true;
  for(Map<String,Object> c:(List<Map<String,Object>>)term.get("where")){Boolean b=cond(c,data);if(b==null||!b)return false;}
  return true;
 }
 private static BigDecimal contribution(Map<String,Object> term,Map<String,Object> data){
  if("COUNT".equals(term.get("agg")))return BigDecimal.ONE;
  return toDecimal(data.get((String)term.get("field")));
 }

 /**
  * Computes one metric for a reporting period. Rules: facts outside the period are ignored; facts lacking a required field or with
  * an unusable amount are counted as UNKNOWN (never as zero); groups below minCell are SUPPRESSED, and when exactly one group of a
  * partition is suppressed the next smallest is also suppressed so it cannot be recovered by subtraction; no facts => NO_DATA.
  */
 @SuppressWarnings("unchecked")
 public static List<GroupResult> compute(Map<String,Object> def,Collection<Fact> facts,LocalDate from,LocalDate to,ZoneId zone,int minCell){
  Map<String,Object> num=mapOf(def.get("numerator")),den=mapOf(def.get("denominator"));
  String groupBy=(String)def.get("groupBy"),curField=(String)def.get("currencyField");
  List<String> require=def.get("requireFields") instanceof List<?> l?(List<String>)l:List.of();
  int unknown=0;
  Map<String,List<Fact>> groups=new TreeMap<>();
  int inPeriod=0;
  for(Fact f:facts){
   LocalDate d=f.occurredAt().atZone(zone).toLocalDate();
   if(d.isBefore(from)||d.isAfter(to))continue;
   inPeriod++;
   boolean ok=require.stream().noneMatch(r->f.data().get(r)==null)&&(groupBy==null||f.data().get(groupBy)!=null)&&(curField==null||f.data().get(curField)!=null);
   if(ok&&"SUM".equals(num.get("agg"))&&matches(num,f.data())&&toDecimal(f.data().get((String)num.get("field")))==null)ok=false;
   if(ok&&den!=null&&"SUM".equals(den.get("agg"))&&matches(den,f.data())&&toDecimal(f.data().get((String)den.get("field")))==null)ok=false;
   if(!ok){unknown++;continue;}
   String dim=groupBy==null?"ALL":String.valueOf(f.data().get(groupBy));
   String cur=curField==null?"":String.valueOf(f.data().get(curField));
   groups.computeIfAbsent(dim+"\u0000"+cur,k->new ArrayList<>()).add(f);
  }
  if(inPeriod==0||groups.isEmpty())return List.of(new GroupResult("ALL",null,"NO_DATA",null,null,null,0,unknown));
  List<GroupResult> out=new ArrayList<>();
  Map<String,List<Fact>> totalByCur=new TreeMap<>();
  for(var g:groups.entrySet()){
   String[] k=g.getKey().split("\u0000",-1);
   out.add(result(num,den,g.getValue(),k[0],k[1].isEmpty()?null:k[1],unknown,minCell));
   if(groupBy!=null)totalByCur.computeIfAbsent(k[1],x->new ArrayList<>()).addAll(g.getValue());
  }
  if(groupBy!=null)
   for(var t:totalByCur.entrySet())out.add(result(num,den,t.getValue(),"ALL",t.getKey().isEmpty()?null:t.getKey(),unknown,minCell));
  // complementary suppression within each currency partition: one hidden cell must not be derivable from the total
  if(groupBy!=null&&den==null){
   Set<String> curs=new TreeSet<>();out.forEach(r->curs.add(r.currency()==null?"":r.currency()));
   for(String c:curs){
    List<Integer> idx=new ArrayList<>();
    for(int i=0;i<out.size();i++)if(!out.get(i).dimension().equals("ALL")&&Objects.equals(out.get(i).currency()==null?"":out.get(i).currency(),c))idx.add(i);
    long hidden=idx.stream().filter(i->out.get(i).state().equals("SUPPRESSED")).count();
    if(hidden==1){
     idx.stream().filter(i->!out.get(i).state().equals("SUPPRESSED")).min(Comparator.comparingInt(i->out.get(i).cohort())).ifPresent(i->{
      GroupResult r=out.get(i);out.set(i,new GroupResult(r.dimension(),r.currency(),"SUPPRESSED",null,null,null,r.cohort(),r.unknown()));});
    }
   }
  }
  return out;
 }
 private static GroupResult result(Map<String,Object> num,Map<String,Object> den,List<Fact> fs,String dim,String cur,int unknown,int minCell){
  int cohort=fs.size();
  if(cohort<minCell)return new GroupResult(dim,cur,"SUPPRESSED",null,null,null,cohort,unknown);
  BigDecimal n=BigDecimal.ZERO,d=den==null?null:BigDecimal.ZERO;
  for(Fact f:fs){
   if(matches(num,f.data()))n=n.add(contribution(num,f.data()));
   if(den!=null&&matches(den,f.data()))d=d.add(contribution(den,f.data()));
  }
  BigDecimal v=den==null?n:(d.signum()==0?null:n.divide(d,6,RoundingMode.HALF_EVEN));
  return new GroupResult(dim,cur,unknown>0?"INCOMPLETE":"OK",n,d,v,cohort,unknown);
 }

 /** Cross-facility aggregate inside ONE cell: needs at least minFacilities contributing, otherwise nothing is published. */
 public record Part(UUID facility,BigDecimal numerator,BigDecimal denominator){}
 public static Optional<GroupResult> combine(Collection<Part> parts,int minFacilities,boolean rate){
  Set<UUID> fac=new HashSet<>();BigDecimal n=BigDecimal.ZERO,d=BigDecimal.ZERO;
  for(Part p:parts){if(p.numerator()==null)continue;fac.add(p.facility());n=n.add(p.numerator());if(p.denominator()!=null)d=d.add(p.denominator());}
  if(fac.size()<minFacilities)return Optional.empty();
  BigDecimal v=rate?(d.signum()==0?null:n.divide(d,6,RoundingMode.HALF_EVEN)):n;
  return Optional.of(new GroupResult("ALL",null,"OK",n,rate?d:null,v,fac.size(),0));
 }

 // ================================================================== CSV safety
 /** Spreadsheet formula-injection defence plus RFC-4180 quoting. */
 public static String csvCell(String v){
  String s=v==null?"":v;
  if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;
  if(s.contains(",")||s.contains("\"")||s.contains("\n")||s.contains("\r"))s="\""+s.replace("\"","\"\"")+"\"";
  return s;
 }
}
