package org.synqoria.content;

import static org.synqoria.content.Db.m;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;

/** M30-S03/S04 clinical rule packages: authoring, validation, independent approval and revocation. */
@Service
public class PackageService {
 private final Db db;
 public PackageService(Db db){this.db=db;}

 public record GoldenCase(String name,Map<String,Object> facts,Map<String,Object> expected){}
 public record Dependency(String type,String key){}
 public record PackageRequest(String packageKey,Map<String,Object> definition,String sourceAttribution,List<Dependency> dependsOn,
   Instant effectiveFrom,Instant effectiveTo,List<GoldenCase> goldenCases){}
 public record Transition(long expectedVersion){}
 public record ReviewRequest(long expectedVersion,String role,boolean approve,String reason,int validForDays){}
 public record RevokeRequest(String reason){}
 public record EvaluateRequest(Map<String,Object> facts){}

 /** Canonical JSON (sorted keys) so equal definitions always hash equally. */
 String canon(Object o){
  if(o instanceof Map<?,?> mp){
   StringBuilder b=new StringBuilder("{");
   boolean first=true;
   for(Object k:new TreeSet<>(mp.keySet().stream().map(String::valueOf).toList())){
    if(!first)b.append(',');first=false;
    b.append(db.encode(k)).append(':').append(canon(mp.get(k)));
   }
   return b.append('}').toString();
  }
  if(o instanceof List<?> l){StringBuilder b=new StringBuilder("[");for(int i=0;i<l.size();i++){if(i>0)b.append(',');b.append(canon(l.get(i)));}return b.append(']').toString();}
  if(o instanceof Number n)return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
  return db.encode(o);
 }

 private Map<String,Object> lock(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_content.rule_packages WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 Map<String,Object> pkg(UUID id){return db.one("SELECT * FROM synqoria_content.rule_packages WHERE id=?",id);}

 public Reply create(Actor a,UUID cmd,PackageRequest r){
  a.require(Perm.PKG_AUTHOR);
  return db.command(a,cmd,"PKG_CREATE",r,()->{
   if(r==null||r.packageKey()==null||!r.packageKey().matches("[A-Za-z0-9._-]{1,64}")||r.definition()==null||r.effectiveFrom()==null||
      r.sourceAttribution()==null||r.sourceAttribution().isBlank()||r.sourceAttribution().length()>500)throw new Violation("invalid_package");
   if(r.effectiveTo()!=null&&!r.effectiveTo().isAfter(r.effectiveFrom()))throw new Violation("invalid_effective_window");
   if(r.dependsOn()!=null&&(r.dependsOn().size()>20||r.dependsOn().stream().anyMatch(d->d==null||!"CODE_SYSTEM".equals(d.type())||!Domain.validUri(d.key()))))throw new Violation("invalid_dependencies");
   List<GoldenCase> cases=r.goldenCases()==null?List.of():r.goldenCases();
   if(cases.size()>200||cases.stream().anyMatch(c->c==null||c.name()==null||c.name().isBlank()||c.name().length()>120||c.facts()==null||c.expected()==null))throw new Violation("invalid_golden_cases");
   if(db.encode(r.definition()).length()>400_000)throw new Violation("definition_too_large");
   if(!db.rows("SELECT 1 FROM synqoria_content.rule_packages WHERE package_key=? AND status='DRAFT'",r.packageKey()).isEmpty())throw new Violation("duplicate_record");
   int next=db.jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM synqoria_content.rule_packages WHERE package_key=?",Integer.class,r.packageKey());
   List<Map<String,Object>> prev=db.rows("SELECT id FROM synqoria_content.rule_packages WHERE package_key=? AND status IN ('VALIDATED','IN_REVIEW','APPROVED','REVOKED','RETIRED') ORDER BY version_no DESC LIMIT 1",r.packageKey());
   UUID id=UUID.randomUUID();
   String hash=Domain.sha256(canon(r.definition()));
   db.jdbc.update("INSERT INTO synqoria_content.rule_packages(id,cell_id,facility_id,package_key,version_no,status,definition,definition_hash,source_attribution,depends_on,"+
    "effective_from,effective_to,previous_id,author_id,version) VALUES (?,?,?,?,?,'DRAFT',?::jsonb,?,?,?::jsonb,?,?,?,?,1)",id,a.cell(),a.facility(),r.packageKey(),next,
    db.encode(r.definition()),hash,r.sourceAttribution().strip(),db.encode(r.dependsOn()==null?List.of():r.dependsOn()),Timestamp.from(r.effectiveFrom()),
    r.effectiveTo()==null?null:Timestamp.from(r.effectiveTo()),prev.isEmpty()?null:prev.getFirst().get("id"),a.id());
   for(GoldenCase c:cases)
    db.jdbc.update("INSERT INTO synqoria_content.golden_cases(id,cell_id,facility_id,package_id,case_name,facts,expected) VALUES (?,?,?,?,?,?::jsonb,?::jsonb)",
     UUID.randomUUID(),a.cell(),a.facility(),id,c.name().strip(),db.encode(c.facts()),db.encode(c.expected()));
   db.audit(a,cmd,"PACKAGE",id,"CREATE",null,"DRAFT",1,m("key",r.packageKey(),"versionNo",next,"hash",hash));
   return new Reply(201,pkg(id));
  });
 }

 /** Expected output shape: {"FIRED":[ids],"INDETERMINATE":[ids]}; every other rule must be NOT_FIRED. */
 @SuppressWarnings("unchecked")
 private List<String> compare(Map<String,Object> def,GoldenCaseRow c){
  List<String> diffs=new ArrayList<>();
  Map<Verdict,Set<String>> got=new EnumMap<>(Verdict.class);
  for(Verdict v:Verdict.values())got.put(v,new TreeSet<>());
  for(Advice adv:Domain.evaluate(def,c.facts))got.get(adv.verdict()).add(adv.ruleId());
  for(Verdict v:List.of(Verdict.FIRED,Verdict.INDETERMINATE)){
   Object e=c.expected.get(v.name());
   Set<String> want=e instanceof List<?> l?new TreeSet<>(l.stream().map(String::valueOf).toList()):new TreeSet<>();
   if(!want.equals(got.get(v)))diffs.add(c.name+":"+v+" expected "+want+" got "+got.get(v));
  }
  return diffs;
 }
 record GoldenCaseRow(String name,Map<String,Object> facts,Map<String,Object> expected){}
 @SuppressWarnings("unchecked")
 private List<GoldenCaseRow> cases(UUID pkg){
  List<GoldenCaseRow> out=new ArrayList<>();
  for(Map<String,Object> r:db.rows("SELECT case_name,facts,expected FROM synqoria_content.golden_cases WHERE package_id=? ORDER BY case_name",pkg))
   out.add(new GoldenCaseRow((String)r.get("caseName"),(Map<String,Object>)r.get("facts"),(Map<String,Object>)r.get("expected")));
  return out;
 }

 @SuppressWarnings("unchecked")
 public Reply validate(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PKG_VALIDATE);
  return db.command(a,cmd,"PKG_VALIDATE|"+id,t,()->{
   Map<String,Object> p=lock(id,t.expectedVersion());
   if(!"DRAFT".equals(p.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> def=(Map<String,Object>)p.get("definition");
   List<String> schema=Domain.validateDefinition(def);
   List<String> goldenDiffs=new ArrayList<>(),integrity=new ArrayList<>(),deps=new ArrayList<>();
   List<Map<String,Object>> regression=new ArrayList<>();
   List<GoldenCaseRow> cs=cases(id);
   if(cs.isEmpty())goldenDiffs.add("NO_GOLDEN_CASES");
   if(schema.isEmpty()){
    for(GoldenCaseRow c:cs)goldenDiffs.addAll(compare(def,c));
    // dependency compatibility: every declared code system must have a published version
    for(Map<String,Object> d:(List<Map<String,Object>>)p.get("dependsOn"))
     if(db.rows("SELECT 1 FROM synqoria_content.code_systems cs JOIN synqoria_content.code_system_versions v ON v.code_system_id=cs.id AND v.status='PUBLISHED' WHERE cs.system_uri=?",d.get("key")).isEmpty())
      deps.add("DEPENDENCY_UNAVAILABLE:"+d.get("key"));
    Set<String> declared=new HashSet<>();
    for(Map<String,Object> d:(List<Map<String,Object>>)p.get("dependsOn"))declared.add((String)d.get("key"));
    // mapping/terminology integrity: referenced codes exist, are ACTIVE, and belong to a declared dependency
    for(Map<String,String> ref:Domain.codeRefs(def)){
     if(!declared.contains(ref.get("system"))){integrity.add("UNDECLARED_SYSTEM:"+ref.get("system"));continue;}
     List<Map<String,Object>> c=db.rows("SELECT c.status FROM synqoria_content.concepts c JOIN synqoria_content.code_system_versions v ON v.id=c.version_id JOIN synqoria_content.code_systems cs ON cs.id=v.code_system_id "+
      "WHERE cs.system_uri=? AND c.code=? AND v.status='PUBLISHED' ORDER BY v.effective_from DESC LIMIT 1",ref.get("system"),ref.get("code"));
     if(c.isEmpty())integrity.add("CODE_UNKNOWN:"+ref.get("code"));
     else if(!"ACTIVE".equals(c.getFirst().get("status")))integrity.add("CODE_NOT_ACTIVE:"+ref.get("code"));
    }
    // regression: replay the PREVIOUS version's golden cases through the new definition
    if(p.get("previousId")!=null){
     Map<String,Object> prev=pkg((UUID)p.get("previousId"));
     Map<String,Object> prevDef=(Map<String,Object>)prev.get("definition");
     for(GoldenCaseRow c:cases((UUID)prev.get("id"))){
      Set<String> before=new TreeSet<>(),after=new TreeSet<>();
      Domain.evaluate(prevDef,c.facts).forEach(x->before.add(x.ruleId()+"="+x.verdict()));
      Domain.evaluate(def,c.facts).forEach(x->after.add(x.ruleId()+"="+x.verdict()));
      if(!before.equals(after)){
       Set<String> removed=new TreeSet<>(before);removed.removeAll(after);Set<String> added=new TreeSet<>(after);added.removeAll(before);
       regression.add(m("case",c.name,"removed",removed,"added",added));
      }
     }
    }
   }
   boolean pass=schema.isEmpty()&&goldenDiffs.isEmpty()&&integrity.isEmpty()&&deps.isEmpty();
   Map<String,Object> report=m("schemaErrors",schema,"goldenFailures",goldenDiffs,"integrityErrors",integrity,"dependencyErrors",deps,
    "regressionChanges",regression,"goldenCaseCount",cs.size(),"previousVersionId",p.get("previousId")==null?null:p.get("previousId").toString());
   String hash=(String)p.get("definitionHash");
   db.jdbc.update("INSERT INTO synqoria_content.validation_runs(id,cell_id,facility_id,package_id,definition_hash,outcome,report,run_by) VALUES (?,?,?,?,?,?,?::jsonb,?)",
    UUID.randomUUID(),a.cell(),a.facility(),id,hash,pass?"PASSED":"FAILED",db.encode(report),a.id());
   String to=pass?"VALIDATED":"VALIDATION_FAILED";
   db.jdbc.update("UPDATE synqoria_content.rule_packages SET status=?,validated_hash=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",to,pass?hash:null,id);
   db.audit(a,cmd,"PACKAGE",id,"VALIDATE","DRAFT",to,t.expectedVersion()+1,m("outcome",pass?"PASSED":"FAILED","regressions",regression.size()));
   Map<String,Object> out=new LinkedHashMap<>(pkg(id));out.put("report",report);
   return new Reply(200,out);
  });
 }

 public Reply submit(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PKG_AUTHOR);
  return db.command(a,cmd,"PKG_SUBMIT|"+id,t,()->{
   Map<String,Object> p=lock(id,t.expectedVersion());
   if(!PackageStatus.valueOf((String)p.get("status")).canMoveTo(PackageStatus.IN_REVIEW))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_content.rule_packages SET status='IN_REVIEW',version=version+1,updated_at=clock_timestamp() WHERE id=?",id);
   db.audit(a,cmd,"PACKAGE",id,"SUBMIT","VALIDATED","IN_REVIEW",t.expectedVersion()+1,m());
   return new Reply(200,pkg(id));
  });
 }

 /** One review per distinct, independent reviewer; CLINICAL and POLICY sign-offs are both required. */
 public Reply review(Actor a,UUID cmd,UUID id,ReviewRequest r){
  return db.command(a,cmd,"PKG_REVIEW|"+id,r,()->{
   String role=r.role();
   if("CLINICAL".equals(role))a.require(Perm.REVIEW_CLINICAL);
   else if("POLICY".equals(role))a.require(Perm.REVIEW_POLICY);
   else throw new Violation("invalid_role");
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw new Violation("reason_required");
   if(r.validForDays()<1||r.validForDays()>365)throw new Violation("invalid_validity");
   Map<String,Object> p=lock(id,r.expectedVersion());
   if(!"IN_REVIEW".equals(p.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(p.get("authorId")))throw new Violation("reviewer_must_be_independent");
   if(!db.rows("SELECT 1 FROM synqoria_content.approvals WHERE package_id=? AND revoked_at IS NULL AND (role=? OR reviewer_id=?)",id,role,a.id()).isEmpty())
    throw new Violation("duplicate_record");
   Instant now=db.clock.instant();
   UUID aid=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.approvals(id,cell_id,facility_id,package_id,definition_hash,role,decision,reviewer_id,reason,decided_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
    aid,a.cell(),a.facility(),id,p.get("definitionHash"),role,r.approve()?"APPROVE":"REJECT",a.id(),r.reason().strip(),Timestamp.from(now),Timestamp.from(now.plus(Duration.ofDays(r.validForDays()))));
   String to="IN_REVIEW";
   if(!r.approve())to="REJECTED";
   else if(db.jdbc.queryForObject("SELECT count(DISTINCT role) FROM synqoria_content.approvals WHERE package_id=? AND decision='APPROVE' AND revoked_at IS NULL",Integer.class,id)==2)to="APPROVED";
   db.jdbc.update("UPDATE synqoria_content.rule_packages SET status=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",to,id);
   db.audit(a,cmd,"PACKAGE",id,"REVIEW_"+role+(r.approve()?"_APPROVE":"_REJECT"),"IN_REVIEW",to,r.expectedVersion()+1,m("approvalId",aid.toString()));
   return new Reply(200,pkg(id));
  });
 }

 /** Revocation is final for the package version and suspends any live activation (fail-safe: nothing active). */
 public Reply revoke(Actor a,UUID cmd,UUID approvalId,RevokeRequest r){
  a.require(Perm.APPROVAL_REVOKE);
  return db.command(a,cmd,"APPROVAL_REVOKE|"+approvalId,r,()->{
   if(r==null||r.reason()==null||r.reason().isBlank()||r.reason().length()>500)throw new Violation("reason_required");
   List<Map<String,Object>> ap=db.rows("SELECT * FROM synqoria_content.approvals WHERE id=?",approvalId);
   if(ap.isEmpty())throw new Violation("not_found");
   UUID pid=(UUID)ap.getFirst().get("packageId");
   Map<String,Object> p=db.one("SELECT * FROM synqoria_content.rule_packages WHERE id=? FOR UPDATE",pid);
   ap=db.rows("SELECT * FROM synqoria_content.approvals WHERE id=? FOR UPDATE",approvalId);
   if(ap.getFirst().get("revokedAt")!=null)throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_content.approvals SET revoked_at=clock_timestamp(),revoked_by=?,revoke_reason=? WHERE id=?",a.id(),r.reason().strip(),approvalId);
   db.audit(a,cmd,"APPROVAL",approvalId,"REVOKE",null,"REVOKED",1,m("packageId",pid.toString()));
   PackageStatus from=PackageStatus.valueOf((String)p.get("status"));
   if(from.canMoveTo(PackageStatus.REVOKED)){
    db.jdbc.update("UPDATE synqoria_content.rule_packages SET status='REVOKED',version=version+1,updated_at=clock_timestamp() WHERE id=?",pid);
    db.audit(a,cmd,"PACKAGE",pid,"REVOKE",from.name(),"REVOKED",((Number)p.get("version")).longValue()+1,m());
    for(Map<String,Object> act:db.rows("SELECT id,version FROM synqoria_content.activations WHERE subject_id=? AND status='ACTIVE' FOR UPDATE",pid)){
     db.jdbc.update("UPDATE synqoria_content.activations SET status='SUSPENDED',end_reason='approval revoked',version=version+1 WHERE id=?",act.get("id"));
     db.audit(a,cmd,"ACTIVATION",(UUID)act.get("id"),"SUSPEND","ACTIVE","SUSPENDED",((Number)act.get("version")).longValue()+1,m("reason","approval revoked"));
    }
   }
   return new Reply(200,pkg(pid));
  });
 }

 /** Advisory dry evaluation of a validated definition against caller-supplied facts. Writes nothing clinical. */
 @SuppressWarnings("unchecked")
 public Map<String,Object> evaluate(Actor a,UUID id,EvaluateRequest r){
  a.require(Perm.PKG_VALIDATE);
  if(r==null||r.facts()==null||db.encode(r.facts()).length()>64_000)throw new Violation("invalid_facts");
  return db.scoped(a,()->{
   Map<String,Object> p=pkg(id);
   List<Advice> adv=Domain.evaluate((Map<String,Object>)p.get("definition"),r.facts());
   return m("advisoryOnly",true,"packageId",id.toString(),"definitionHash",p.get("definitionHash"),
    "results",adv.stream().map(x->m("ruleId",x.ruleId(),"verdict",x.verdict().name(),"code",x.code(),"severity",x.severity())).toList());
  });
 }

 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.PKG_READ);
  return db.scoped(a,()->{
   Map<String,Object> p=new LinkedHashMap<>(pkg(id));
   p.put("goldenCases",db.rows("SELECT case_name,facts,expected FROM synqoria_content.golden_cases WHERE package_id=? ORDER BY case_name",id));
   p.put("validationRuns",db.rows("SELECT id,definition_hash,outcome,report,run_by,run_at FROM synqoria_content.validation_runs WHERE package_id=? ORDER BY run_at DESC",id));
   p.put("approvals",db.rows("SELECT id,role,decision,reviewer_id,reason,decided_at,expires_at,revoked_at,revoked_by,revoke_reason FROM synqoria_content.approvals WHERE package_id=? ORDER BY decided_at",id));
   p.put("history",db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,occurred_at FROM synqoria_content.audit_events WHERE aggregate_type='PACKAGE' AND aggregate_id=? ORDER BY occurred_at,event_id",id));
   return p;
  });
 }
 public Map<String,Object> list(Actor a,String status,String key,int page,int size){
  a.require(Perm.PKG_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(PackageStatus.class,status,"status");
  if(key!=null&&!key.matches("[A-Za-z0-9._-]{1,64}"))throw new Violation("invalid_query");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(status!=null){w.append(" AND status=?");args.add(status);}
   if(key!=null){w.append(" AND package_key=?");args.add(key);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_content.rule_packages"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT id,package_key,version_no,status,definition_hash,source_attribution,effective_from,effective_to,author_id,version,updated_at FROM synqoria_content.rule_packages"+w+
    " ORDER BY package_key,version_no DESC LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
}
