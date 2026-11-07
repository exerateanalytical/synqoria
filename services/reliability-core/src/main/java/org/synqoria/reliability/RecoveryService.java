package org.synqoria.reliability;

import static org.synqoria.reliability.Db.m;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;

/**
 * Controlled recovery: plan against a VERIFIED base backup and continuous WAL, independent authorization, fencing, restore, reconciliation,
 * audit recovery, validation and independently approved, scoped reopening. States advance only on recorded evidence.
 */
@Service
public class RecoveryService {
 final Db db;final BackupService backups;
 public RecoveryService(Db db,BackupService backups){this.db=db;this.backups=backups;}
 public record Plan(String scenario,boolean exercise,UUID baseBackupId,long targetLsn,Instant targetTime,Instant outageStartedAt,UUID incidentId){}
 public record Authorize(long expectedVersion){}
 public record StepRequest(long expectedVersion,String step,String evidenceRef,Boolean egressDisabled,Boolean writersStopped,Long restoredLsn,Long objectsMissing,String auditChainDigest,Long effectsPending){}
 public record ValidationItem(String itemKey,String result,String evidenceRef,String residualRisk){}
 public record Reopen(long expectedVersion,List<String> scope){}
 public record Abort(long expectedVersion,String reason){}

 static final String REC="SELECT r.id,r.incident_id,r.scenario,r.exercise,r.base_backup_id,b.snapshot_id AS base_snapshot,r.target_lsn,r.target_time,r.outage_started_at,r.state,r.planned_by,r.authorized_by,r.abort_reason,"+
  "r.fence_evidence,r.restored_lsn,r.restore_evidence,r.objects_evidence,r.audit_chain_digest,r.effects_evidence,r.reopen_approved_by,r.reopen_scope,r.reopened_at,r.version,r.created_at,r.updated_at "+
  "FROM synqoria_reliability.recoveries r JOIN synqoria_reliability.backups b ON b.id=r.base_backup_id";
 Map<String,Object> view(UUID id){
  Map<String,Object> r=new LinkedHashMap<>(db.one(REC+" WHERE r.id=?",id));
  r.put("steps",db.rows("SELECT step,evidence_ref,detail,actor_id,at FROM synqoria_reliability.recovery_steps WHERE recovery_id=? ORDER BY at,id",id));
  r.put("validations",db.rows("SELECT item_key,result,evidence_ref,residual_risk,validated_by,at FROM synqoria_reliability.recovery_validations WHERE recovery_id=? ORDER BY item_key",id));
  r.put("measurement",db.rows("SELECT source,rto_seconds,rpo_seconds,measured_at FROM synqoria_reliability.rpo_rto_measurements WHERE recovery_id=?",id).stream().findFirst().orElse(null));
  return r;
 }
 void ensureRecoverable(UUID baseId,long target){
  Map<String,Object> b=db.rows("SELECT kind,status,wal_end,retention_until FROM synqoria_reliability.backups WHERE id=?",baseId).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(!"FULL".equals(b.get("kind"))||!"VERIFIED".equals(b.get("status"))||Instant.parse((String)b.get("retentionUntil")).isBefore(db.clock.instant()))throw new Violation("backup_not_verified");
  if(target<((Number)b.get("walEnd")).longValue())throw new Violation("target_out_of_range");
  Map<String,Object> c=backups.continuityIn(baseId,target);
  if(!Boolean.TRUE.equals(c.get("continuous")))throw new Violation("wal_gap",List.of(String.valueOf(c.get("gapStart")),String.valueOf(c.get("gapEnd"))));
 }
 public Reply plan(Actor a,UUID cmd,Plan p){
  a.require(Perm.RECOVERY_PLAN);ObservabilityService.req(p);
  if(!Domain.SCENARIOS.contains(p.scenario())||p.baseBackupId()==null||p.targetLsn()<0||p.outageStartedAt()==null)throw new Violation("invalid_recovery");
  return db.command(a,cmd,"RECOVERY_PLAN",p,()->{
   ensureRecoverable(p.baseBackupId(),p.targetLsn());
   if(p.outageStartedAt().isAfter(db.clock.instant().plusSeconds(60)))throw new Violation("invalid_recovery");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_reliability.recoveries(id,cell_id,facility_id,incident_id,scenario,exercise,base_backup_id,target_lsn,target_time,outage_started_at,state,planned_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,'PLANNED',?,1)",
    id,a.cell(),a.facility(),p.incidentId(),p.scenario(),p.exercise(),p.baseBackupId(),p.targetLsn(),p.targetTime()==null?null:Timestamp.from(p.targetTime()),Timestamp.from(p.outageStartedAt()),a.id());   // one open REAL recovery per cell (unique index)
   db.audit(a,cmd,"RECOVERY",id,"PLAN",null,"PLANNED",1,m("scenario",p.scenario(),"exercise",p.exercise()));
   return new Reply(201,view(id));
  });
 }
 Map<String,Object> lock(UUID id,long expected){
  Map<String,Object> r=db.rows("SELECT * FROM synqoria_reliability.recoveries WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(((Number)r.get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r;
 }
 void move(Actor a,UUID cmd,UUID id,Map<String,Object> r,String to,String action,String set,Object... args){
  Object[] all=new Object[args.length+2];System.arraycopy(args,0,all,0,args.length);all[args.length]=to;all[args.length+1]=id;
  db.jdbc.update("UPDATE synqoria_reliability.recoveries SET "+set+(set.isEmpty()?"":",")+"state=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",all);
  db.audit(a,cmd,"RECOVERY",id,action,(String)r.get("state"),to,((Number)r.get("version")).longValue()+1,m());
 }
 public Reply authorize(Actor a,UUID cmd,UUID id,Authorize z){
  a.require(Perm.RECOVERY_AUTHORIZE);ObservabilityService.req(z);
  return db.command(a,cmd,"RECOVERY_AUTHORIZE|"+id,z,()->{
   Map<String,Object> r=lock(id,z.expectedVersion());
   if(!"PLANNED".equals(r.get("state")))throw new Violation("invalid_transition");
   if(a.id().equals(r.get("plannedBy")))throw new Violation("approver_must_be_independent");
   ensureRecoverable((UUID)r.get("baseBackupId"),((Number)r.get("targetLsn")).longValue());              // the backup may have expired or the archive changed since planning
   move(a,cmd,id,r,"AUTHORIZED","AUTHORIZE","authorized_by=?",a.id());
   return new Reply(200,view(id));
  });
 }
 static void need(String e){if(e==null||e.isBlank()||e.length()>160)throw new Violation("evidence_required");}
 public Reply step(Actor a,UUID cmd,UUID id,StepRequest s){
  a.require(Perm.RECOVERY_OPERATE);ObservabilityService.req(s);
  if(s.step()==null||!Domain.STEPS.contains(s.step()))throw new Violation("invalid_step");
  return db.command(a,cmd,"RECOVERY_STEP|"+id,s,()->{
   Map<String,Object> r=lock(id,s.expectedVersion());
   if(!Domain.stepFrom(s.step()).equals(r.get("state")))throw new Violation("invalid_transition");
   String to=Domain.stepTo(s.step());String set="";Object[] args=new Object[0];Map<String,Object> detail=new LinkedHashMap<>();
   switch(s.step()){
    case "FENCE"->{
     need(s.evidenceRef());
     if(!Boolean.TRUE.equals(s.egressDisabled())||!Boolean.TRUE.equals(s.writersStopped()))throw new Violation("fencing_not_confirmed");    // positive confirmation only
     set="fence_evidence=?";args=new Object[]{s.evidenceRef().trim()};detail.put("egressDisabled",true);detail.put("writersStopped",true);}
    case "RESTORE_DB"->{
     need(s.evidenceRef());
     if(s.restoredLsn()==null||s.restoredLsn()!=((Number)r.get("targetLsn")).longValue())throw new Violation("restore_target_mismatch");
     set="restored_lsn=?,restore_evidence=?";args=new Object[]{s.restoredLsn(),s.evidenceRef().trim()};detail.put("restoredLsn",s.restoredLsn());}
    case "RECONCILE_OBJECTS"->{
     need(s.evidenceRef());
     if(s.objectsMissing()==null||s.objectsMissing()!=0)throw new Violation("objects_unreconciled");
     set="objects_evidence=?";args=new Object[]{s.evidenceRef().trim()};detail.put("objectsMissing",0);}
    case "RECOVER_AUDIT"->{
     if(s.auditChainDigest()==null||!s.auditChainDigest().matches("[0-9a-f]{64}"))throw new Violation("evidence_required");
     set="audit_chain_digest=?";args=new Object[]{s.auditChainDigest()};detail.put("auditChainDigest",s.auditChainDigest());}
    case "RECONCILE_EFFECTS"->{
     need(s.evidenceRef());
     if(s.effectsPending()==null||s.effectsPending()!=0)throw new Violation("effects_unreconciled");     // no external side effect may be left unreconciled
     set="effects_evidence=?";args=new Object[]{s.evidenceRef().trim()};detail.put("effectsPending",0);}
    case "BEGIN_VALIDATION"->{}
    default->throw new Violation("invalid_step");
   }
   db.jdbc.update("INSERT INTO synqoria_reliability.recovery_steps(id,cell_id,facility_id,recovery_id,step,evidence_ref,detail,actor_id,at) VALUES (?,?,?,?,?,?,?::jsonb,?,?)",
    UUID.randomUUID(),a.cell(),a.facility(),id,s.step(),s.evidenceRef(),db.encode(detail),a.id(),Timestamp.from(db.clock.instant()));
   move(a,cmd,id,r,to,s.step(),set,args);
   return new Reply(200,view(id));
  });
 }
 public Reply validate(Actor a,UUID cmd,UUID id,ValidationItem v){
  a.require(Perm.RECOVERY_VALIDATE);ObservabilityService.req(v);
  if(v.itemKey()==null||!Domain.VALIDATION_ITEMS.contains(v.itemKey())||!Set.of("PASS","FAIL").contains(v.result())||v.evidenceRef()==null||v.evidenceRef().isBlank()||v.evidenceRef().length()>160||(v.residualRisk()!=null&&v.residualRisk().length()>300))throw new Violation("invalid_validation");
  return db.command(a,cmd,"RECOVERY_VALIDATE|"+id,v,()->{
   Map<String,Object> r=db.rows("SELECT state FROM synqoria_reliability.recoveries WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!"VALIDATING".equals(r.get("state")))throw new Violation("invalid_transition");
   db.jdbc.update("INSERT INTO synqoria_reliability.recovery_validations(id,cell_id,facility_id,recovery_id,item_key,result,evidence_ref,residual_risk,validated_by) VALUES (?,?,?,?,?,?,?,?,?)",
    UUID.randomUUID(),a.cell(),a.facility(),id,v.itemKey(),v.result(),v.evidenceRef().trim(),v.residualRisk(),a.id());
   db.audit(a,cmd,"RECOVERY",id,"VALIDATE:"+v.itemKey(),"VALIDATING","VALIDATING",1,m("result",v.result()));
   return new Reply(201,view(id));
  });
 }
 /** Scoped reactivation: only the named services are reopened, by an approver who neither planned nor authorized the recovery. */
 public Reply reopen(Actor a,UUID cmd,UUID id,Reopen o){
  a.require(Perm.REOPEN_APPROVE);ObservabilityService.req(o);
  if(o.scope()==null||o.scope().isEmpty()||o.scope().size()>50)throw new Violation("invalid_recovery");
  return db.command(a,cmd,"RECOVERY_REOPEN|"+id,o,()->{
   Map<String,Object> r=lock(id,o.expectedVersion());
   if(!"VALIDATING".equals(r.get("state")))throw new Violation("invalid_transition");
   if(a.id().equals(r.get("plannedBy"))||a.id().equals(r.get("authorizedBy")))throw new Violation("reopener_must_be_independent");
   Map<String,String> results=new HashMap<>();
   for(Map<String,Object> v:db.rows("SELECT item_key,result FROM synqoria_reliability.recovery_validations WHERE recovery_id=?",id))results.put((String)v.get("itemKey"),(String)v.get("result"));
   List<String> notPassed=Domain.VALIDATION_ITEMS.stream().filter(k->!"PASS".equals(results.get(k))).toList();
   if(!notPassed.isEmpty())throw new Violation("validation_incomplete",notPassed);
   for(String key:o.scope())if(db.rows("SELECT 1 AS x FROM synqoria_reliability.services WHERE service_key=?",key).isEmpty())throw new Violation("not_found");
   Instant now=db.clock.instant();
   db.jdbc.update(c->{
    var ps=c.prepareStatement("UPDATE synqoria_reliability.recoveries SET reopen_approved_by=?,reopen_scope=?,reopened_at=?,state='REOPENED',version=version+1,updated_at=clock_timestamp() WHERE id=?");
    ps.setObject(1,a.id());ps.setArray(2,c.createArrayOf("text",new TreeSet<>(o.scope()).toArray()));ps.setTimestamp(3,Timestamp.from(now));ps.setObject(4,id);return ps;});
   Instant outage=Instant.parse((String)r.get("outageStartedAt"));
   Object tt=r.get("targetTime");
   Long rpo=tt==null?null:Math.max(0,Duration.between(Instant.parse((String)tt),outage).getSeconds());
   db.jdbc.update("INSERT INTO synqoria_reliability.rpo_rto_measurements(id,cell_id,facility_id,recovery_id,source,rto_seconds,rpo_seconds) VALUES (?,?,?,?,?,?,?)",
    UUID.randomUUID(),a.cell(),a.facility(),id,Boolean.TRUE.equals(r.get("exercise"))?"EXERCISE":"REAL",Math.max(0,Duration.between(outage,now).getSeconds()),rpo);
   db.audit(a,cmd,"RECOVERY",id,"REOPEN","VALIDATING","REOPENED",o.expectedVersion()+1,m("services",o.scope().size()));
   return new Reply(200,view(id));
  });
 }
 public Reply abort(Actor a,UUID cmd,UUID id,Abort x){
  a.require(Perm.RECOVERY_OPERATE);ObservabilityService.req(x);
  if(x.reason()==null||x.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"RECOVERY_ABORT|"+id,x,()->{
   Map<String,Object> r=lock(id,x.expectedVersion());
   if(Set.of("REOPENED","ABORTED").contains(r.get("state")))throw new Violation("invalid_transition");
   move(a,cmd,id,r,"ABORTED","ABORT","abort_reason=?",x.reason().trim());
   return new Reply(200,view(id));
  });
 }
 public Map<String,Object> list(Actor a,int page,int size){
  a.require(Perm.RECOVERY_READ);ObservabilityService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_reliability.recoveries",Long.class);
   return db.pageOf(db.rows(REC+" ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
 public Map<String,Object> get(Actor a,UUID id){a.require(Perm.RECOVERY_READ);return db.scoped(a,()->view(id));}
 public Map<String,Object> measurements(Actor a,int page,int size){
  a.require(Perm.RECOVERY_READ);ObservabilityService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_reliability.rpo_rto_measurements",Long.class);
   return db.pageOf(db.rows("SELECT m.recovery_id,m.source,m.rto_seconds,m.rpo_seconds,m.measured_at,r.scenario FROM synqoria_reliability.rpo_rto_measurements m JOIN synqoria_reliability.recoveries r ON r.id=m.recovery_id ORDER BY m.measured_at DESC LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
}
