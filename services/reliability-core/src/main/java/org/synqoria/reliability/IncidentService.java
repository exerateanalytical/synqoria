package org.synqoria.reliability;

import static org.synqoria.reliability.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;

/** Incident records with a fixed timeline, acknowledgement SLA, escalation and independently verified recovery. */
@Service
public class IncidentService {
 final Db db;
 public IncidentService(Db db){this.db=db;}
 public record Declare(String title,String severity,String serviceKey,String impact){}
 public record Ack(long expectedVersion){}
 public record Assign(long expectedVersion,UUID ownerId){}
 public record Note(String kind,String text){}
 public record Escalate(long expectedVersion,String reason){}
 public record StatusChange(long expectedVersion,String to,String text){}
 public record Resolve(long expectedVersion,String verificationEvidence){}

 static final String INC="SELECT i.id,i.number,i.title,i.severity,i.status,s.service_key,i.impact,i.owner_id,i.escalation_level,i.created_by,i.declared_at,i.acknowledged_by,i.acknowledged_at,i.mitigated_at,"+
  "i.verified_by,i.verification_evidence,i.resolved_at,i.version FROM synqoria_reliability.incidents i LEFT JOIN synqoria_reliability.services s ON s.id=i.service_id";
 Map<String,Object> view(UUID id,Instant now){
  Map<String,Object> r=new LinkedHashMap<>(db.one(INC+" WHERE i.id=?",id));
  r.put("ackOverdue",Domain.ackOverdue((String)r.get("severity"),(String)r.get("status"),Instant.parse((String)r.get("declaredAt")),now));
  r.put("ackSlaMinutes",Domain.ackSlaMinutes((String)r.get("severity")));
  r.put("timeline",db.rows("SELECT kind,text,actor_id,at FROM synqoria_reliability.incident_timeline WHERE incident_id=? ORDER BY at,id",id));
  return r;
 }
 void line(Actor a,UUID incident,String kind,String text){
  db.jdbc.update("INSERT INTO synqoria_reliability.incident_timeline(id,cell_id,facility_id,incident_id,kind,text,actor_id,at) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),a.cell(),a.facility(),incident,kind,text,a.id(),Timestamp.from(db.clock.instant()));
 }
 Map<String,Object> lock(UUID id,long expected){
  Map<String,Object> i=db.rows("SELECT * FROM synqoria_reliability.incidents WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(((Number)i.get("version")).longValue()!=expected)throw new Violation("stale_version");
  return i;
 }
 public Reply declare(Actor a,UUID cmd,Declare d){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(d);
  if(d.title()==null||d.title().isBlank()||d.title().length()>200||!Set.of("SEV1","SEV2","SEV3","SEV4").contains(d.severity())||d.impact()==null||d.impact().isBlank()||d.impact().length()>500)throw new Violation("invalid_incident");
  return db.command(a,cmd,"INCIDENT_DECLARE",d,()->{
   UUID service=null;
   if(d.serviceKey()!=null)service=db.rows("SELECT id FROM synqoria_reliability.services WHERE service_key=?",d.serviceKey()).stream().findFirst().map(x->(UUID)x.get("id")).orElseThrow(()->new Violation("not_found"));
   UUID id=UUID.randomUUID();Instant now=db.clock.instant();
   long number=db.jdbc.queryForObject("SELECT nextval('synqoria_reliability.incident_number')",Long.class);
   db.jdbc.update("INSERT INTO synqoria_reliability.incidents(id,cell_id,facility_id,number,title,severity,status,service_id,impact,created_by,declared_at,version) VALUES (?,?,?,?,?,?,'OPEN',?,?,?,?,1)",
    id,a.cell(),a.facility(),number,d.title().trim(),d.severity(),service,d.impact().trim(),a.id(),Timestamp.from(now));
   line(a,id,"DECLARED",d.title().trim());
   db.audit(a,cmd,"INCIDENT",id,"DECLARE",null,"OPEN",1,m("severity",d.severity()));
   return new Reply(201,view(id,now));
  });
 }
 public Reply acknowledge(Actor a,UUID cmd,UUID id,Ack k){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(k);
  return db.command(a,cmd,"INCIDENT_ACK|"+id,k,()->{
   Map<String,Object> i=lock(id,k.expectedVersion());
   if(!"OPEN".equals(i.get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_reliability.incidents SET status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=?,owner_id=COALESCE(owner_id,?),version=version+1 WHERE id=?",a.id(),Timestamp.from(db.clock.instant()),a.id(),id);
   line(a,id,"ACK","Acknowledged");
   db.audit(a,cmd,"INCIDENT",id,"ACK","OPEN","ACKNOWLEDGED",k.expectedVersion()+1,m());
   return new Reply(200,view(id,db.clock.instant()));
  });
 }
 public Reply assign(Actor a,UUID cmd,UUID id,Assign s){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(s);
  if(s.ownerId()==null)throw new Violation("invalid_incident");
  return db.command(a,cmd,"INCIDENT_ASSIGN|"+id,s,()->{
   Map<String,Object> i=lock(id,s.expectedVersion());
   if("RESOLVED".equals(i.get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_reliability.incidents SET owner_id=?,version=version+1 WHERE id=?",s.ownerId(),id);
   line(a,id,"ASSIGN","Owner set to "+s.ownerId());
   db.audit(a,cmd,"INCIDENT",id,"ASSIGN",(String)i.get("status"),(String)i.get("status"),s.expectedVersion()+1,m());
   return new Reply(200,view(id,db.clock.instant()));
  });
 }
 public Reply note(Actor a,UUID cmd,UUID id,Note n){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(n);
  if(n.kind()==null||!Set.of("NOTE","ACTION").contains(n.kind())||n.text()==null||n.text().isBlank()||n.text().length()>500)throw new Violation("invalid_incident");
  return db.command(a,cmd,"INCIDENT_NOTE|"+id,n,()->{
   Map<String,Object> i=db.rows("SELECT status FROM synqoria_reliability.incidents WHERE id=? FOR SHARE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if("RESOLVED".equals(i.get("status")))throw new Violation("invalid_transition");
   line(a,id,n.kind(),Domain.redactText(n.text().trim()).value());
   return new Reply(201,view(id,db.clock.instant()));
  });
 }
 public Reply escalate(Actor a,UUID cmd,UUID id,Escalate e){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(e);
  if(e.reason()==null||e.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"INCIDENT_ESCALATE|"+id,e,()->{
   Map<String,Object> i=lock(id,e.expectedVersion());
   if("RESOLVED".equals(i.get("status"))||((Number)i.get("escalationLevel")).intValue()>=3)throw new Violation("invalid_transition");
   int to=((Number)i.get("escalationLevel")).intValue()+1;
   db.jdbc.update("UPDATE synqoria_reliability.incidents SET escalation_level=?,version=version+1 WHERE id=?",to,id);
   line(a,id,"ESCALATION","Level "+to+": "+e.reason().trim());
   db.audit(a,cmd,"INCIDENT",id,"ESCALATE",(String)i.get("status"),(String)i.get("status"),e.expectedVersion()+1,m("level",to));
   return new Reply(200,view(id,db.clock.instant()));
  });
 }
 public Reply status(Actor a,UUID cmd,UUID id,StatusChange s){
  a.require(Perm.INCIDENT_MANAGE);ObservabilityService.req(s);
  if(s.to()==null||!Set.of("MITIGATING","MITIGATED").contains(s.to())||s.text()==null||s.text().trim().length()<5)throw new Violation("invalid_incident");
  return db.command(a,cmd,"INCIDENT_STATUS|"+id,s,()->{
   Map<String,Object> i=lock(id,s.expectedVersion());
   String from=(String)i.get("status");
   boolean ok=("ACKNOWLEDGED".equals(from)&&Set.of("MITIGATING","MITIGATED").contains(s.to()))||("MITIGATING".equals(from)&&"MITIGATED".equals(s.to()));
   if(!ok)throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_reliability.incidents SET status=?,mitigated_at=CASE WHEN ?='MITIGATED' THEN ? ELSE mitigated_at END,version=version+1 WHERE id=?",s.to(),s.to(),Timestamp.from(db.clock.instant()),id);
   line(a,id,"STATUS",s.to()+": "+Domain.redactText(s.text().trim()).value());
   db.audit(a,cmd,"INCIDENT",id,s.to(),from,s.to(),s.expectedVersion()+1,m());
   return new Reply(200,view(id,db.clock.instant()));
  });
 }
 /** Resolution = recovery verification by someone other than the owner. */
 public Reply resolve(Actor a,UUID cmd,UUID id,Resolve r){
  a.require(Perm.INCIDENT_VERIFY);ObservabilityService.req(r);
  if(r.verificationEvidence()==null||r.verificationEvidence().trim().length()<5||r.verificationEvidence().length()>300)throw new Violation("evidence_required");
  return db.command(a,cmd,"INCIDENT_RESOLVE|"+id,r,()->{
   Map<String,Object> i=lock(id,r.expectedVersion());
   if(!"MITIGATED".equals(i.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(i.get("ownerId"))||a.id().equals(i.get("createdBy")))throw new Violation("verifier_must_be_independent");
   db.jdbc.update("UPDATE synqoria_reliability.incidents SET status='RESOLVED',verified_by=?,verification_evidence=?,resolved_at=?,version=version+1 WHERE id=?",a.id(),r.verificationEvidence().trim(),Timestamp.from(db.clock.instant()),id);
   line(a,id,"VERIFIED",r.verificationEvidence().trim());
   db.audit(a,cmd,"INCIDENT",id,"RESOLVE","MITIGATED","RESOLVED",r.expectedVersion()+1,m());
   return new Reply(200,view(id,db.clock.instant()));
  });
 }
 public Map<String,Object> list(Actor a,String status,int page,int size){
  a.require(Perm.HEALTH_READ);ObservabilityService.paging(page,size);
  if(status!=null&&!Set.of("OPEN","ACKNOWLEDGED","MITIGATING","MITIGATED","RESOLVED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE i.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_reliability.incidents i"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   Instant now=db.clock.instant();
   List<Map<String,Object>> items=new ArrayList<>();
   for(Map<String,Object> r:db.rows(INC+w+" ORDER BY i.declared_at DESC,i.id LIMIT ? OFFSET ?",args)){
    Map<String,Object> x=new LinkedHashMap<>(r);
    x.put("ackOverdue",Domain.ackOverdue((String)r.get("severity"),(String)r.get("status"),Instant.parse((String)r.get("declaredAt")),now));
    items.add(x);
   }
   return db.pageOf(items,page,size,total);
  });
 }
 public Map<String,Object> get(Actor a,UUID id){a.require(Perm.HEALTH_READ);return db.scoped(a,()->view(id,db.clock.instant()));}
}
