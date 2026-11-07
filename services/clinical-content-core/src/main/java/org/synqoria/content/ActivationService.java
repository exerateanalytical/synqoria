package org.synqoria.content;

import static org.synqoria.content.Db.m;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;

/**
 * M30-S05 activation and rollback. Nothing is in force until an ACTIVE row exists; activation is atomic
 * (one transaction, one unique ACTIVE row per subject) and only reachable through independent approvals.
 */
@Service
public class ActivationService {
 private final Db db;
 public ActivationService(Db db){this.db=db;}

 public record ActivateRequest(String subjectType,UUID subjectId,UUID expectedActiveId){}
 public record RollbackRequest(long expectedVersion,String reason){}

 /** Everything that blocks (or later invalidates) activation of one subject, computed without writing. */
 @SuppressWarnings("unchecked")
 private Candidate assess(Actor a,String type,UUID id){
  Instant now=db.clock.instant();
  List<String> blockers=new ArrayList<>();
  String key;UUID author;Map<String,Object> detail=new LinkedHashMap<>();
  switch(type){
   case "RULE_PACKAGE"->{
    Map<String,Object> p=db.one("SELECT * FROM synqoria_content.rule_packages WHERE id=?",id);
    key=(String)p.get("packageKey");author=(UUID)p.get("authorId");
    detail.put("versionNo",p.get("versionNo"));detail.put("definitionHash",p.get("definitionHash"));
    if(!"APPROVED".equals(p.get("status")))blockers.add("NOT_APPROVED");
    if(Instant.parse((String)p.get("effectiveFrom")).isAfter(now))blockers.add("NOT_YET_EFFECTIVE");
    if(p.get("effectiveTo")!=null&&!Instant.parse((String)p.get("effectiveTo")).isAfter(now))blockers.add("EXPIRED");
    List<Map<String,Object>> aps=db.rows("SELECT role,expires_at FROM synqoria_content.approvals WHERE package_id=? AND decision='APPROVE' AND revoked_at IS NULL AND definition_hash=?",id,p.get("definitionHash"));
    Set<String> live=new TreeSet<>(),expired=new TreeSet<>();
    for(Map<String,Object> ap:aps)(Instant.parse((String)ap.get("expiresAt")).isAfter(now)?live:expired).add((String)ap.get("role"));
    if(!live.containsAll(List.of("CLINICAL","POLICY"))){blockers.add(expired.isEmpty()?"APPROVALS_INCOMPLETE":"APPROVAL_EXPIRED");}
    for(Map<String,Object> d:(List<Map<String,Object>>)p.get("dependsOn"))
     if(db.rows("SELECT 1 FROM synqoria_content.code_systems cs JOIN synqoria_content.code_system_versions v ON v.code_system_id=cs.id AND v.status='PUBLISHED' WHERE cs.system_uri=?",d.get("key")).isEmpty())
      blockers.add("DEPENDENCY_NOT_ACTIVE:"+d.get("key"));
    List<Map<String,Object>> cur=db.rows("SELECT a.id,p.version_no FROM synqoria_content.activations a JOIN synqoria_content.rule_packages p ON p.id=a.subject_id WHERE a.subject_type='RULE_PACKAGE' AND a.subject_key=? AND a.status='ACTIVE'",key);
    if(!cur.isEmpty()&&((Number)cur.getFirst().get("versionNo")).intValue()>=((Number)p.get("versionNo")).intValue())blockers.add("NOT_NEWER_THAN_ACTIVE");
    if(!cur.isEmpty()){
     Map<String,Object> old=db.one("SELECT definition FROM synqoria_content.rule_packages WHERE id=(SELECT subject_id FROM synqoria_content.activations WHERE id=?)",cur.getFirst().get("id"));
     Set<String> before=ruleIds((Map<String,Object>)old.get("definition")),after=ruleIds((Map<String,Object>)p.get("definition"));
     Set<String> added=new TreeSet<>(after);added.removeAll(before);Set<String> removed=new TreeSet<>(before);removed.removeAll(after);
     detail.put("rulesAdded",added);detail.put("rulesRemoved",removed);
    }
   }
   case "FORMULARY"->{
    Map<String,Object> f=db.one("SELECT * FROM synqoria_content.formulary_entries WHERE id=?",id);
    key=(String)f.get("entryKey");author=(UUID)f.get("authorId");detail.put("versionNo",f.get("versionNo"));
    if(!"APPROVED".equals(f.get("status")))blockers.add("NOT_APPROVED");
   }
   case "CODE_SYSTEM"->{
    Map<String,Object> v=db.one("SELECT v.*,cs.system_uri,cs.licensing_class,cs.license_ref FROM synqoria_content.code_system_versions v JOIN synqoria_content.code_systems cs ON cs.id=v.code_system_id WHERE v.id=?",id);
    key=(String)v.get("systemUri");author=(UUID)v.get("importedBy");detail.put("versionLabel",v.get("versionLabel"));
    if(!"PUBLISHED".equals(v.get("status")))blockers.add("NOT_PUBLISHED");
    if("LICENSED_RESTRICTED".equals(v.get("licensingClass"))&&v.get("licenseRef")==null)blockers.add("LICENSE_MISSING");
   }
   default->throw new Violation("invalid_subject_type");
  }
  if(a.id().equals(author))blockers.add("ACTIVATOR_IS_AUTHOR");
  List<Map<String,Object>> cur=db.rows("SELECT id,subject_id,version FROM synqoria_content.activations WHERE subject_type=? AND subject_key=? AND status='ACTIVE'",type,key);
  return new Candidate(type,key,id,author,cur.isEmpty()?null:cur.getFirst(),blockers,detail);
 }
 record Candidate(String type,String key,UUID id,UUID author,Map<String,Object> current,List<String> blockers,Map<String,Object> detail){}
 @SuppressWarnings("unchecked")
 private static Set<String> ruleIds(Map<String,Object> def){
  Set<String> out=new TreeSet<>();
  for(Object r:(List<Object>)def.get("rules"))out.add(String.valueOf(((Map<String,Object>)r).get("id")));
  return out;
 }

 /** Serialises all activation changes for one subject in one facility (transaction-scoped advisory lock). */
 private void lockSubject(Actor a,String type,String key){
  db.jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},a.cell()+"|"+a.facility()+"|"+type+"|"+key);
 }

 /** Dry run: the same checks as activation, nothing written. */
 public Map<String,Object> impact(Actor a,String type,UUID id){
  a.require(Perm.ACT_READ);
  return db.scoped(a,()->{
   Candidate c=assess(a,type,id);
   return m("subjectType",type,"subjectKey",c.key(),"subjectId",id.toString(),"canActivate",c.blockers().isEmpty(),"blockers",c.blockers(),
    "currentlyActive",c.current()==null?null:c.current().get("subjectId").toString(),"changes",c.detail(),"dryRun",true);
  });
 }

 public Reply activate(Actor a,UUID cmd,ActivateRequest r){
  a.require(Perm.ACT_MANAGE);
  return db.command(a,cmd,"ACTIVATE",r,()->{
   if(r==null||r.subjectType()==null||r.subjectId()==null)throw new Violation("invalid_activation");
   // lock the subject and the current activation first so concurrent activations serialize
   Candidate c=assess(a,r.subjectType(),r.subjectId());
   lockSubject(a,c.type(),c.key());
   c=assess(a,r.subjectType(),r.subjectId());
   UUID currentId=c.current()==null?null:(UUID)c.current().get("id");
   if(!Objects.equals(currentId,r.expectedActiveId()))throw new Violation("activation_conflict");
   if(!c.blockers().isEmpty()){
    if(c.blockers().contains("ACTIVATOR_IS_AUTHOR"))throw new Violation("activator_must_be_independent",c.blockers());
    String first=c.blockers().getFirst();
    if(first.startsWith("APPROVAL")||first.equals("NOT_APPROVED"))throw new Violation(first.equals("APPROVAL_EXPIRED")?"approval_expired":"approvals_incomplete",c.blockers());
    if(first.startsWith("DEPENDENCY"))throw new Violation("dependency_not_active",c.blockers());
    throw new Violation("activation_conflict",c.blockers());
   }
   long prevVersion=0;
   if(currentId!=null){
    prevVersion=((Number)c.current().get("version")).longValue();
    db.jdbc.update("UPDATE synqoria_content.activations SET status='SUPERSEDED',end_reason='superseded by newer activation',version=version+1 WHERE id=?",currentId);
    db.audit(a,cmd,"ACTIVATION",currentId,"SUPERSEDE","ACTIVE","SUPERSEDED",prevVersion+1,m());
   }
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.activations(id,cell_id,facility_id,subject_type,subject_key,subject_id,status,activated_by,previous_id,impact,version) VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?::jsonb,1)",
    id,a.cell(),a.facility(),c.type(),c.key(),c.id(),a.id(),currentId,db.encode(c.detail()));
   if(c.type().equals("RULE_PACKAGE")){/* the package row stays APPROVED; "active" is only the activation record */}
   db.audit(a,cmd,"ACTIVATION",id,"ACTIVATE",null,"ACTIVE",1,m("type",c.type(),"key",c.key(),"subjectId",c.id().toString()));
   return new Reply(201,db.one("SELECT * FROM synqoria_content.activations WHERE id=?",id));
  });
 }

 /** Rollback re-pins the previous version through a NEW activation row; the failed one is kept as history. */
 public Reply rollback(Actor a,UUID cmd,UUID activationId,RollbackRequest r){
  a.require(Perm.ROLLBACK);
  return db.command(a,cmd,"ROLLBACK|"+activationId,r,()->{
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("reason_required");
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_content.activations WHERE id=? FOR UPDATE",activationId);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> cur=rows.getFirst();
   lockSubject(a,(String)cur.get("subjectType"),(String)cur.get("subjectKey"));
   rows=db.rows("SELECT * FROM synqoria_content.activations WHERE id=? FOR UPDATE",activationId);
   cur=rows.getFirst();
   if(((Number)cur.get("version")).longValue()!=r.expectedVersion())throw new Violation("stale_version");
   if(!"ACTIVE".equals(cur.get("status")))throw new Violation("invalid_transition");
   if(cur.get("previousId")==null)throw new Violation("nothing_to_roll_back");
   Map<String,Object> prev=db.one("SELECT * FROM synqoria_content.activations WHERE id=?",cur.get("previousId"));
   Candidate target=assess(a,(String)prev.get("subjectType"),(UUID)prev.get("subjectId"));
   List<String> blockers=new ArrayList<>(target.blockers());
   blockers.removeIf(x->x.equals("NOT_NEWER_THAN_ACTIVE")||x.equals("ACTIVATOR_IS_AUTHOR"));
   // fail-safe: if the retained version is no longer eligible, change nothing and say why
   if(!blockers.isEmpty())throw new Violation("nothing_to_roll_back",blockers);
   db.jdbc.update("UPDATE synqoria_content.activations SET status='ROLLED_BACK',end_reason=?,version=version+1 WHERE id=?",r.reason().strip(),activationId);
   db.audit(a,cmd,"ACTIVATION",activationId,"ROLLBACK","ACTIVE","ROLLED_BACK",r.expectedVersion()+1,m("reason",r.reason().strip()));
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.activations(id,cell_id,facility_id,subject_type,subject_key,subject_id,status,activated_by,previous_id,impact,version) VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?::jsonb,1)",
    id,a.cell(),a.facility(),prev.get("subjectType"),prev.get("subjectKey"),prev.get("subjectId"),a.id(),activationId,db.encode(m("rollbackOf",activationId.toString())));
   db.audit(a,cmd,"ACTIVATION",id,"REPIN","ROLLED_BACK","ACTIVE",1,m("restored",prev.get("subjectId").toString()));
   return new Reply(201,db.one("SELECT * FROM synqoria_content.activations WHERE id=?",id));
  });
 }

 public Map<String,Object> active(Actor a){
  a.require(Perm.ACT_READ);
  return db.scoped(a,()->m("items",db.rows("SELECT * FROM synqoria_content.activations WHERE status='ACTIVE' ORDER BY subject_type,subject_key")));
 }
 public Map<String,Object> history(Actor a,String subjectKey,int page,int size){
  a.require(Perm.ACT_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=subjectKey==null?"":" WHERE subject_key=?";
   Object[] base=subjectKey==null?new Object[0]:new Object[]{subjectKey};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_content.activations"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT * FROM synqoria_content.activations"+w+" ORDER BY activated_at DESC,id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
}
