package org.synqoria.fleet;

import static org.synqoria.fleet.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;

/** Tenants, provisioning, hospital-cell lifecycle, qualification, health and the fleet views. Management-plane authority only. */
@Service
public class FleetService {
 final Db db;final ReleaseService releases;
 static final Set<String> TIERS=Set.of("SMALL","STANDARD","LARGE"),ISOLATION=Set.of("DEDICATED_DATABASE","DEDICATED_CLUSTER"),RESIDENCY=Set.of("IN_REGION_ONLY","IN_COUNTRY_ONLY","SAME_CONTINENT");
 static final Pattern REGION=Pattern.compile("[a-z][a-z0-9-]{1,31}"),SERVICE=Pattern.compile("[a-z][a-z0-9_.-]{1,39}");
 public FleetService(Db db,ReleaseService releases){this.db=db;this.releases=releases;}

 public record ProvisionRequest(String requestKey,String tenantKey,String tenantName,String region,String tier,int capacityUnits,List<String> serviceProfile,String storageIsolation,String residencyPolicy,Map<String,Object> rollbackMetadata){}
 public record Decision(long expectedVersion,boolean approve,String reason){}
 public record ApplyRequest(long expectedVersion,String executorResult,String failureCode){}
 public record RollbackRequest(long expectedVersion,String reason){}
 public record Transition(long expectedVersion,String to,String reason){}
 public record CheckRequest(String checkKey,String result,String evidenceRef,String detail){}
 public record Complete(long expectedVersion){}
 public record HealthReport(String status,Instant observedAt,String releaseKey){}

 static <T> T req(T v){if(v==null)throw new Violation("malformed_request");return v;}
 static String lit(Collection<String> v){return "{"+String.join(",",new TreeSet<>(v))+"}";}

 // ------------------------------------------------------------------ provisioning (B02)
 public Reply requestTenant(Actor a,UUID cmd,ProvisionRequest r){
  a.require(Perm.TENANT_REQUEST);a.requirePlatformWide();req(r);
  if(!Domain.validKey(r.requestKey())||!Domain.validKey(r.tenantKey())||r.tenantName()==null||r.tenantName().isBlank()||r.region()==null||!REGION.matcher(r.region()).matches()||!TIERS.contains(r.tier())||
     r.capacityUnits()<1||r.capacityUnits()>10000||r.serviceProfile()==null||r.serviceProfile().isEmpty()||r.serviceProfile().size()>40||!ISOLATION.contains(r.storageIsolation())||
     !RESIDENCY.contains(r.residencyPolicy())||r.rollbackMetadata()==null||r.rollbackMetadata().isEmpty())throw new Violation("invalid_provisioning");
  for(String s:r.serviceProfile())if(!SERVICE.matcher(s).matches())throw new Violation("invalid_provisioning");
  return db.command(a,cmd,"PROVISION_REQUEST",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.provisioning_requests(id,cell_id,facility_id,request_key,tenant_key,tenant_name,region,tier,capacity_units,service_profile,storage_isolation,residency_policy,rollback_metadata,status,requested_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?::text[],?,?,?::jsonb,'REQUESTED',?,1)",id,a.cell(),a.facility(),r.requestKey(),r.tenantKey(),r.tenantName().trim(),r.region(),r.tier(),r.capacityUnits(),lit(r.serviceProfile()),
    r.storageIsolation(),r.residencyPolicy(),db.encode(new TreeMap<>(r.rollbackMetadata())),a.id());
   db.audit(a,cmd,"PROVISIONING",id,"REQUEST",null,"REQUESTED",1,m("tenantKey",r.tenantKey()));
   return new Reply(201,provisioning(id));
  });
 }
 static final String PROV="SELECT id,tenant_id,request_key,tenant_key,tenant_name,region,tier,capacity_units,service_profile,storage_isolation,residency_policy,rollback_metadata,status,failure_code,requested_by,approved_by,version,created_at FROM synqoria_fleet.provisioning_requests";
 Map<String,Object> provisioning(UUID id){return db.one(PROV+" WHERE id=?",id);}
 public Reply decideProvisioning(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.TENANT_APPROVE);a.requirePlatformWide();req(d);
  if(!d.approve()&&(d.reason()==null||d.reason().trim().length()<5))throw new Violation("reason_required");
  return db.command(a,cmd,"PROVISION_DECIDE|"+id,d,()->{
   Map<String,Object> p=lockProv(id,d.expectedVersion());
   if(!"REQUESTED".equals(p.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(p.get("requestedBy")))throw new Violation("approver_must_be_independent");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_fleet.provisioning_requests SET status=?,approved_by=?,failure_code=?,version=version+1 WHERE id=?",to,a.id(),d.approve()?null:"rejected",id);
   db.audit(a,cmd,"PROVISIONING",id,d.approve()?"APPROVE":"REJECT","REQUESTED",to,d.expectedVersion()+1,m());
   return new Reply(200,provisioning(id));
  });
 }
 Map<String,Object> lockProv(UUID id,long expected){
  Map<String,Object> p=db.rows("SELECT * FROM synqoria_fleet.provisioning_requests WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(((Number)p.get("version")).longValue()!=expected)throw new Violation("stale_version");
  return p;
 }
 /**
  * Records the declarative outcome reported by the (external) infrastructure executor. This service never creates real infrastructure:
  * on OK it creates the tenant and a PROVISIONING cell with opaque references; on FAILED nothing is created and the failure is kept.
  */
 public Reply applyProvisioning(Actor a,UUID cmd,UUID id,ApplyRequest r){
  a.require(Perm.CELL_OPERATE);a.requirePlatformWide();req(r);
  if(!Set.of("OK","FAILED").contains(r.executorResult())||("FAILED".equals(r.executorResult())&&(r.failureCode()==null||!r.failureCode().matches("[a-z][a-z0-9_]{2,47}"))))throw new Violation("invalid_provisioning");
  return db.command(a,cmd,"PROVISION_APPLY|"+id,r,()->{
   Map<String,Object> p=lockProv(id,r.expectedVersion());
   if(!"APPROVED".equals(p.get("status")))throw new Violation("invalid_transition");
   if("FAILED".equals(r.executorResult())){
    db.jdbc.update("UPDATE synqoria_fleet.provisioning_requests SET status='FAILED',failure_code=?,version=version+1 WHERE id=?",r.failureCode(),id);
    db.audit(a,cmd,"PROVISIONING",id,"FAIL","APPROVED","FAILED",r.expectedVersion()+1,m("code",r.failureCode()));
    return new Reply(200,provisioning(id));
   }
   UUID tenant=UUID.randomUUID(),cellRow=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.tenants(id,cell_id,facility_id,tenant_key,name,region,residency_policy,status,version) VALUES (?,?,?,?,?,?,?,'ACTIVE',1)",
    tenant,a.cell(),a.facility(),p.get("tenantKey"),p.get("tenantName"),p.get("region"),p.get("residencyPolicy"));
   db.jdbc.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) VALUES (?,?,?,?,?,'PROD',?,?,?,'PROVISIONING',?,?,?,1)",
    cellRow,a.cell(),a.facility(),tenant,p.get("tenantKey")+"-prod",p.get("region"),p.get("tier"),p.get("capacityUnits"),"db/"+p.get("tenantKey")+"/prod","cred/"+p.get("tenantKey")+"/prod",a.id());
   db.jdbc.update("UPDATE synqoria_fleet.provisioning_requests SET status='APPLIED',tenant_id=?,version=version+1 WHERE id=?",tenant,id);
   db.audit(a,cmd,"PROVISIONING",id,"APPLY","APPROVED","APPLIED",r.expectedVersion()+1,m("tenantId",tenant.toString()));
   db.audit(a,cmd,"CELL",cellRow,"CREATE",null,"PROVISIONING",1,m("tenantId",tenant.toString()));
   return new Reply(200,provisioning(id));
  });
 }
 /** Rollback is possible only while the cell has never served traffic. */
 public Reply rollbackProvisioning(Actor a,UUID cmd,UUID id,RollbackRequest r){
  a.require(Perm.CELL_OPERATE);a.requirePlatformWide();req(r);
  if(r.reason()==null||r.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"PROVISION_ROLLBACK|"+id,r,()->{
   Map<String,Object> p=lockProv(id,r.expectedVersion());
   if(!"APPLIED".equals(p.get("status")))throw new Violation("invalid_transition");
   for(Map<String,Object> c:db.rows("SELECT id,state,version FROM synqoria_fleet.cells WHERE tenant_id=? FOR UPDATE",p.get("tenantId"))){
    String st=(String)c.get("state");
    if(!Set.of("PROVISIONING","QUALIFIED","FAILED").contains(st))throw new Violation("irreversible");
    if(!"FAILED".equals(st))db.jdbc.update("UPDATE synqoria_fleet.cells SET state='FAILED',state_reason=?,version=version+1 WHERE id=?",r.reason().trim(),c.get("id"));
    db.jdbc.update("UPDATE synqoria_fleet.cells SET state='RETIRED',state_reason=?,version=version+1 WHERE id=?",r.reason().trim(),c.get("id"));
   }
   db.jdbc.update("UPDATE synqoria_fleet.tenants SET status='SUSPENDED',version=version+1 WHERE id=?",p.get("tenantId"));
   db.jdbc.update("UPDATE synqoria_fleet.provisioning_requests SET status='ROLLED_BACK',failure_code='rolled_back',version=version+1 WHERE id=?",id);
   db.audit(a,cmd,"PROVISIONING",id,"ROLLBACK","APPLIED","ROLLED_BACK",r.expectedVersion()+1,m());
   return new Reply(200,provisioning(id));
  });
 }
 public Map<String,Object> provisioningList(Actor a,String status,int page,int size){
  a.require(Perm.FLEET_READ);a.requirePlatformWide();paging(page,size);
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.provisioning_requests"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows(PROV+w+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
 static void paging(int page,int size){if(page<0||size<1||size>100)throw new Violation("invalid_pagination");}

 // ------------------------------------------------------------------ cells and lifecycle (B03, B05)
 static final String CELL="SELECT c.id,c.tenant_id,t.tenant_key,t.name AS tenant_name,c.cell_key,c.environment,c.region,c.tier,c.capacity_units,c.state,c.placement_epoch,c.release_key,c.schema_version,c.state_reason,c.version,c.created_at,"+
  "h.status AS health,h.observed_at AS health_observed_at FROM synqoria_fleet.cells c JOIN synqoria_fleet.tenants t ON t.id=c.tenant_id "+
  "LEFT JOIN LATERAL (SELECT status,observed_at FROM synqoria_fleet.cell_health x WHERE x.target_cell=c.id ORDER BY observed_at DESC LIMIT 1) h ON true";
 public Map<String,Object> cells(Actor a,String state,UUID tenantId,int page,int size){
  a.require(Perm.FLEET_READ);paging(page,size);
  if(state!=null&&!Domain.CELL_STATES.contains(state))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   StringBuilder w=new StringBuilder(" WHERE true");List<Object> args=new ArrayList<>();
   if(state!=null){w.append(" AND c.state=?");args.add(state);}
   if(tenantId!=null){w.append(" AND c.tenant_id=?");args.add(tenantId);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.cells c"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows(CELL+w+" ORDER BY t.tenant_key,c.cell_key LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public Map<String,Object> cell(Actor a,UUID id){a.require(Perm.FLEET_READ);return db.scoped(a,()->db.one(CELL+" WHERE c.id=?",id));}
 public Map<String,Object> tenants(Actor a,int page,int size){
  a.require(Perm.FLEET_READ);paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.tenants",Long.class);
   return db.pageOf(db.rows("SELECT t.id,t.tenant_key,t.name,t.region,t.residency_policy,t.status,t.version,(SELECT count(*) FROM synqoria_fleet.cells c WHERE c.tenant_id=t.id AND c.state='ACTIVE') AS active_cells,"+
    "(SELECT count(*) FROM synqoria_fleet.cells c WHERE c.tenant_id=t.id) AS cells FROM synqoria_fleet.tenants t ORDER BY t.tenant_key LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
 /** Manual transitions only; QUALIFIED comes from qualification and MIGRATING from the migration machine. */
 public Reply transition(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.CELL_OPERATE);req(t);
  if(t.to()==null||!Domain.CELL_STATES.contains(t.to())||Set.of("QUALIFIED","MIGRATING","PROVISIONING").contains(t.to()))throw new Violation("invalid_transition");
  if(t.reason()==null||t.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"CELL_TRANSITION|"+id,t,()->{
   Map<String,Object> c=db.rows("SELECT * FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)c.get("tenantId"));
   if(((Number)c.get("version")).longValue()!=t.expectedVersion())throw new Violation("stale_version");
   String from=(String)c.get("state");
   if(!Domain.canMove(from,t.to()))throw new Violation("invalid_transition");
   if(db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.migrations WHERE (source_cell=? OR target_cell=?) AND state NOT IN ('RETIRED','ROLLED_BACK','FAILED')",Long.class,id,id)>0)throw new Violation("migration_in_progress");
   if("ACTIVE".equals(t.to())&&"QUALIFIED".equals(from)&&db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.qualifications WHERE target_cell=? AND status='PASSED'",Long.class,id)==0)throw new Violation("not_qualified");
   db.jdbc.update("UPDATE synqoria_fleet.cells SET state=?,state_reason=?,version=version+1 WHERE id=?",t.to(),t.reason().trim(),id);
   db.audit(a,cmd,"CELL",id,"TRANSITION",from,t.to(),t.expectedVersion()+1,m());
   return new Reply(200,db.one(CELL+" WHERE c.id=?",id));
  });
 }

 // ------------------------------------------------------------------ qualification (B04)
 public Reply startQualification(Actor a,UUID cmd,UUID cellRow){
  a.require(Perm.QUALIFY_RECORD);
  return db.command(a,cmd,"QUALIFY_START|"+cellRow,m("cell",cellRow.toString()),()->{
   Map<String,Object> c=db.rows("SELECT * FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",cellRow).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)c.get("tenantId"));
   if(!Set.of("PROVISIONING","QUALIFIED").contains(c.get("state")))throw new Violation("invalid_transition");
   if(db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.qualifications WHERE target_cell=? AND status='OPEN'",Long.class,cellRow)>0)throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.qualifications(id,cell_id,facility_id,tenant_id,target_cell,status,started_by,version) VALUES (?,?,?,?,?,'OPEN',?,1)",id,a.cell(),a.facility(),c.get("tenantId"),cellRow,a.id());
   db.audit(a,cmd,"QUALIFICATION",id,"START",null,"OPEN",1,m());
   return new Reply(201,qualification(id));
  });
 }
 Map<String,Object> qualification(UUID id){
  Map<String,Object> q=new LinkedHashMap<>(db.one("SELECT id,target_cell,status,started_by,completed_by,completed_at,version,created_at FROM synqoria_fleet.qualifications WHERE id=?",id));
  q.put("checks",db.rows("SELECT check_key,result,evidence_ref,detail,recorded_by,recorded_at FROM synqoria_fleet.qualification_checks WHERE qualification_id=? ORDER BY check_key",id));
  return q;
 }
 public Reply recordCheck(Actor a,UUID cmd,UUID qid,CheckRequest r){
  a.require(Perm.QUALIFY_RECORD);req(r);
  if(!Domain.QUALIFICATION_CHECKS.contains(r.checkKey())||!Set.of("PASS","FAIL").contains(r.result())||r.evidenceRef()==null||r.evidenceRef().isBlank()||r.evidenceRef().length()>160||(r.detail()!=null&&r.detail().length()>300))throw new Violation("invalid_check");
  return db.command(a,cmd,"QUALIFY_CHECK|"+qid,r,()->{
   Map<String,Object> q=db.rows("SELECT * FROM synqoria_fleet.qualifications WHERE id=? FOR UPDATE",qid).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)q.get("tenantId"));
   if(!"OPEN".equals(q.get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("INSERT INTO synqoria_fleet.qualification_checks(id,cell_id,facility_id,tenant_id,qualification_id,check_key,result,evidence_ref,detail,recorded_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
    UUID.randomUUID(),a.cell(),a.facility(),q.get("tenantId"),qid,r.checkKey(),r.result(),r.evidenceRef().trim(),r.detail(),a.id());
   db.audit(a,cmd,"QUALIFICATION",qid,"CHECK:"+r.checkKey(),"OPEN","OPEN",((Number)q.get("version")).longValue(),m("result",r.result()));
   return new Reply(200,qualification(qid));
  });
 }
 public Reply completeQualification(Actor a,UUID cmd,UUID qid,Complete c){
  a.require(Perm.QUALIFY_COMPLETE);req(c);
  return db.command(a,cmd,"QUALIFY_COMPLETE|"+qid,c,()->{
   Map<String,Object> q=db.rows("SELECT * FROM synqoria_fleet.qualifications WHERE id=? FOR UPDATE",qid).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)q.get("tenantId"));
   if(((Number)q.get("version")).longValue()!=c.expectedVersion())throw new Violation("stale_version");
   if(!"OPEN".equals(q.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(q.get("startedBy")))throw new Violation("completer_must_be_independent");
   Map<String,String> results=new HashMap<>();
   for(Map<String,Object> r:db.rows("SELECT check_key,result FROM synqoria_fleet.qualification_checks WHERE qualification_id=?",qid))results.put((String)r.get("checkKey"),(String)r.get("result"));
   List<String> missing=Domain.QUALIFICATION_CHECKS.stream().filter(k->!results.containsKey(k)).toList();
   if(!missing.isEmpty())throw new Violation("qualification_incomplete",missing);
   boolean pass=results.values().stream().allMatch("PASS"::equals);
   UUID cellRow=(UUID)q.get("targetCell");
   db.jdbc.update("UPDATE synqoria_fleet.qualifications SET status=?,completed_by=?,completed_at=clock_timestamp(),version=version+1 WHERE id=?",pass?"PASSED":"FAILED",a.id(),qid);
   Map<String,Object> cell=db.rows("SELECT state FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",cellRow).getFirst();
   if("PROVISIONING".equals(cell.get("state")))db.jdbc.update("UPDATE synqoria_fleet.cells SET state=?,state_reason=?,version=version+1 WHERE id=?",pass?"QUALIFIED":"FAILED",pass?"qualified":"qualification_failed",cellRow);
   db.audit(a,cmd,"QUALIFICATION",qid,pass?"PASS":"FAIL","OPEN",pass?"PASSED":"FAILED",c.expectedVersion()+1,m());
   return new Reply(200,qualification(qid));
  });
 }
 public List<Map<String,Object>> qualifications(Actor a,UUID cellRow){
  a.require(Perm.FLEET_READ);
  return db.scoped(a,()->{
   List<Map<String,Object>> out=new ArrayList<>();
   for(Map<String,Object> q:db.rows("SELECT id FROM synqoria_fleet.qualifications WHERE target_cell=? ORDER BY created_at DESC",cellRow))out.add(qualification(UUID.fromString(String.valueOf(q.get("id")))));
   return out;
  });
 }

 // ------------------------------------------------------------------ health (immutable observations) and automatic rollout stop
 public Reply reportHealth(Actor a,UUID cmd,UUID cellRow,HealthReport h){
  a.require(Perm.HEALTH_REPORT);req(h);
  if(h.status()==null||!Set.of("HEALTHY","DEGRADED","UNHEALTHY").contains(h.status())||h.observedAt()==null||(h.releaseKey()!=null&&h.releaseKey().length()>40))throw new Violation("invalid_health");
  return db.command(a,cmd,"HEALTH|"+cellRow,h,()->{
   Map<String,Object> c=db.rows("SELECT id,tenant_id,state FROM synqoria_fleet.cells WHERE id=?",cellRow).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)c.get("tenantId"));
   if(h.observedAt().isAfter(db.clock.instant().plusSeconds(60)))throw new Violation("invalid_health");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.cell_health(id,cell_id,facility_id,tenant_id,target_cell,status,observed_at,reported_by,release_key) VALUES (?,?,?,?,?,?,?,?,?)",id,a.cell(),a.facility(),c.get("tenantId"),cellRow,h.status(),Timestamp.from(h.observedAt()),a.id(),h.releaseKey());
   String stopped=releases.autoStopIfUnhealthy(a,cmd,cellRow,h.status());
   db.audit(a,cmd,"HEALTH",id,"REPORT",null,h.status(),1,m("cell",cellRow.toString()));
   return new Reply(201,m("id",id.toString(),"status",h.status(),"rolloutStopped",stopped));
  });
 }
}
