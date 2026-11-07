package org.synqoria.content;

import static org.synqoria.content.Db.m;

import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;

/**
 * M30-S02 formulary reference entries. These describe which products/forms/routes are permitted; they never
 * create, alter or dispense a prescription (ordering and dispensing stay with their own modules).
 */
@Service
public class FormularyService {
 private final Db db;
 public FormularyService(Db db){this.db=db;}

 public record EntryRequest(String entryKey,UUID productVersionId,String productCode,String formulation,String route,
   String permittedUse,List<String> restrictions){}
 public record UpdateRequest(long expectedVersion,String formulation,String route,String permittedUse,List<String> restrictions){}
 public record Transition(long expectedVersion){}
 public record Decision(long expectedVersion,boolean approve,String note){}

 private void validate(String formulation,String route,String use,List<String> restrictions){
  if(formulation==null||formulation.isBlank()||formulation.length()>80)throw new Violation("invalid_formulation");
  if(route==null||!route.matches("[A-Z0-9_]{2,40}"))throw new Violation("invalid_route");
  if(use==null||use.isBlank()||use.length()>500)throw new Violation("invalid_permitted_use");
  if(restrictions!=null&&(restrictions.size()>20||restrictions.stream().anyMatch(x->x==null||x.isBlank()||x.length()>200)))throw new Violation("invalid_restrictions");
 }
 private Map<String,Object> lock(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_content.formulary_entries WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private Map<String,Object> entry(UUID id){return db.one("SELECT * FROM synqoria_content.formulary_entries WHERE id=?",id);}

 public Reply create(Actor a,UUID cmd,EntryRequest r){
  a.require(Perm.FORM_EDIT);
  return db.command(a,cmd,"FORM_CREATE",r,()->{
   if(r==null||r.entryKey()==null||!r.entryKey().matches("[A-Za-z0-9._-]{1,64}")||r.productVersionId()==null||!Domain.validCode(r.productCode()))throw new Violation("invalid_entry");
   validate(r.formulation(),r.route(),r.permittedUse(),r.restrictions());
   List<Map<String,Object>> p=db.rows("SELECT c.status FROM synqoria_content.concepts c JOIN synqoria_content.code_system_versions v ON v.id=c.version_id "+
    "WHERE c.version_id=? AND c.code=? AND v.status='PUBLISHED'",r.productVersionId(),r.productCode());
   if(p.isEmpty())throw new Violation("concept_not_found");
   if(!"ACTIVE".equals(p.getFirst().get("status")))throw new Violation("product_not_active");
   if(!db.rows("SELECT 1 FROM synqoria_content.formulary_entries WHERE entry_key=? AND status IN ('DRAFT','SUBMITTED')",r.entryKey()).isEmpty())throw new Violation("duplicate_record");
   int next=db.jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM synqoria_content.formulary_entries WHERE entry_key=?",Integer.class,r.entryKey());
   List<Map<String,Object>> approved=db.rows("SELECT id FROM synqoria_content.formulary_entries WHERE entry_key=? AND status='APPROVED'",r.entryKey());
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_content.formulary_entries(id,cell_id,facility_id,entry_key,version_no,product_version_id,product_code,formulation,route,permitted_use,"+
    "restrictions,status,supersedes_id,author_id,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,1)",id,a.cell(),a.facility(),r.entryKey(),next,r.productVersionId(),r.productCode(),
    r.formulation().strip(),r.route(),r.permittedUse().strip(),(r.restrictions()==null?List.<String>of():r.restrictions()).toArray(new String[0]),
    approved.isEmpty()?null:approved.getFirst().get("id"),a.id());
   db.audit(a,cmd,"FORMULARY",id,"CREATE",null,"DRAFT",1,m("entryKey",r.entryKey(),"versionNo",next));
   return new Reply(201,entry(id));
  });
 }
 public Reply update(Actor a,UUID cmd,UUID id,UpdateRequest r){
  a.require(Perm.FORM_EDIT);
  return db.command(a,cmd,"FORM_UPDATE|"+id,r,()->{
   Map<String,Object> e=lock(id,r.expectedVersion());
   if(!"DRAFT".equals(e.get("status")))throw new Violation("invalid_transition");
   if(!a.id().equals(e.get("authorId")))throw new Violation("permission_denied");
   validate(r.formulation(),r.route(),r.permittedUse(),r.restrictions());
   db.jdbc.update("UPDATE synqoria_content.formulary_entries SET formulation=?,route=?,permitted_use=?,restrictions=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
    r.formulation().strip(),r.route(),r.permittedUse().strip(),(r.restrictions()==null?List.<String>of():r.restrictions()).toArray(new String[0]),id);
   db.audit(a,cmd,"FORMULARY",id,"UPDATE","DRAFT","DRAFT",r.expectedVersion()+1,m());
   return new Reply(200,entry(id));
  });
 }
 public Reply submit(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.FORM_EDIT);
  return db.command(a,cmd,"FORM_SUBMIT|"+id,t,()->move(a,cmd,id,t.expectedVersion(),FormularyStatus.SUBMITTED,"SUBMIT",null));
 }
 public Reply withdraw(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.FORM_EDIT);
  return db.command(a,cmd,"FORM_WITHDRAW|"+id,t,()->move(a,cmd,id,t.expectedVersion(),FormularyStatus.WITHDRAWN,"WITHDRAW",null));
 }
 private Reply move(Actor a,UUID cmd,UUID id,long expected,FormularyStatus to,String action,String note){
  Map<String,Object> e=lock(id,expected);
  FormularyStatus from=FormularyStatus.valueOf((String)e.get("status"));
  if(!from.canMoveTo(to))throw new Violation("invalid_transition");
  db.jdbc.update("UPDATE synqoria_content.formulary_entries SET status=?,decision_note=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",to.name(),note,id);
  db.audit(a,cmd,"FORMULARY",id,action,from.name(),to.name(),expected+1,m());
  return new Reply(200,entry(id));
 }
 /** Independent clinical decision. Approval supersedes the previous approved version atomically. */
 public Reply decide(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.FORM_APPROVE);
  return db.command(a,cmd,"FORM_DECIDE|"+id,d,()->{
   Map<String,Object> e=lock(id,d.expectedVersion());
   if(!"SUBMITTED".equals(e.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(e.get("authorId")))throw new Violation("approver_must_be_independent");
   if(d.note()!=null&&d.note().length()>300)throw new Violation("invalid_note");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   if(d.approve()){
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_content.formulary_entries WHERE entry_key=? AND status='APPROVED' FOR UPDATE",e.get("entryKey"))){
     db.jdbc.update("UPDATE synqoria_content.formulary_entries SET status='SUPERSEDED',version=version+1,updated_at=clock_timestamp() WHERE id=?",old.get("id"));
     db.audit(a,cmd,"FORMULARY",(UUID)old.get("id"),"SUPERSEDE","APPROVED","SUPERSEDED",((Number)old.get("version")).longValue()+1,m("by",id.toString()));
    }
   }
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_content.formulary_entries SET status=?,approver_id=?,approved_at=clock_timestamp(),decision_note=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
    to,a.id(),d.note(),id);
   db.audit(a,cmd,"FORMULARY",id,d.approve()?"APPROVE":"REJECT","SUBMITTED",to,d.expectedVersion()+1,m());
   return new Reply(200,entry(id));
  });
 }
 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.FORM_READ);
  return db.scoped(a,()->{
   Map<String,Object> e=new LinkedHashMap<>(entry(id));
   e.put("history",db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,occurred_at FROM synqoria_content.audit_events "+
    "WHERE aggregate_type='FORMULARY' AND aggregate_id=? ORDER BY occurred_at,event_id",id));
   return e;
  });
 }
 public Map<String,Object> list(Actor a,String status,String q,int page,int size){
  a.require(Perm.FORM_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(FormularyStatus.class,status,"status");
  if(q!=null&&q.length()>100)throw new Violation("invalid_query");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(status!=null){w.append(" AND f.status=?");args.add(status);}
   if(q!=null&&!q.isBlank()){String like="%"+q.strip().toLowerCase().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    w.append(" AND (lower(f.entry_key) LIKE ? OR lower(f.product_code) LIKE ? OR lower(c.display_en) LIKE ?)");args.add(like);args.add(like);args.add(like);}
   String from=" FROM synqoria_content.formulary_entries f JOIN synqoria_content.concepts c ON c.version_id=f.product_version_id AND c.code=f.product_code";
   long total=db.jdbc.queryForObject("SELECT count(*)"+from+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT f.*,c.display_en AS product_display_en,c.display_fr AS product_display_fr"+from+w+" ORDER BY f.entry_key,f.version_no DESC LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
}
