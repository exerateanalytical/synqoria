package org.synqoria.operations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.synqoria.operations.domain.MaintenanceRules;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;
import tools.jackson.databind.json.JsonMapper;

/**
 * M23 preventive maintenance, calibration tracking and servicing calendar.
 * Reuses OperationsStore.scoped() (least-privileged runtime role, cell+facility RLS) and the EXISTING asset / work-order state
 * machine: this service never creates or transitions work orders and never returns an asset to service. It only plans,
 * links, verifies independently and reports. Every mutation is idempotent (pm_commands) and audited (audit_events + outbox).
 */
@Service
public final class MaintenanceService {
 private static final int LEASE_SECONDS=120;
 private final OperationsStore store;
 private final JdbcTemplate jdbc;
 private final JsonMapper json;
 private final Clock clock;
 @Autowired
 public MaintenanceService(OperationsStore store,JdbcTemplate jdbc,JsonMapper json){this(store,jdbc,json,Clock.systemUTC());}
 MaintenanceService(OperationsStore store,JdbcTemplate jdbc,JsonMapper json,Clock clock){
  this.store=Objects.requireNonNull(store);this.jdbc=Objects.requireNonNull(jdbc);this.json=Objects.requireNonNull(json);this.clock=Objects.requireNonNull(clock);
 }

 // ---------------------------------------------------------------- helpers
 private static String sha(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable",e);}
 }
 private static Object plain(Object v){
  if(v instanceof Timestamp t)return t.toInstant().toString();
  if(v instanceof java.sql.Date d)return d.toLocalDate().toString();
  if(v instanceof java.sql.Time t)return t.toLocalTime().toString();
  if(v instanceof UUID u)return u.toString();
  if(v instanceof String s)return s.strip();
  return v;
 }
 private static Map<String,Object> clean(Map<String,Object> row){
  Map<String,Object> out=new LinkedHashMap<>();
  row.forEach((k,v)->out.put(camel(k),plain(v)));
  return out;
 }
 private static String camel(String snake){
  StringBuilder b=new StringBuilder();boolean up=false;
  for(char c:snake.toCharArray()){if(c=='_')up=true;else{b.append(up?Character.toUpperCase(c):c);up=false;}}
  return b.toString();
 }
 private static void limit(int n,int max){if(n<1||n>max)throw new WorkflowViolation("invalid_limit");}
 private static UUID need(UUID id){if(id==null)throw new WorkflowViolation("invalid_reference");return id;}
 private static int bounded(Integer value,int min,int max,String code){
  if(value==null||value<min||value>max)throw new WorkflowViolation(code);
  return value;
 }
 /** Idempotent command wrapper: same key + same request replays the stored result; same key + different request is a collision. */
 private Map<String,Object> command(OperationWorkflow.Actor a,UUID commandId,String request,Supplier<Map<String,Object>> work){
  if(commandId==null)throw new WorkflowViolation("idempotency_key_required");
  String hash=sha(request);
  jdbc.update("INSERT INTO synqoria_operations.pm_commands(cell_id,facility_id,actor_id,command_id,request_hash) VALUES(?,?,?,?,?) "+
   "ON CONFLICT(cell_id,facility_id,actor_id,command_id) DO NOTHING",a.cell(),a.facility(),a.actor(),commandId,hash);
  Map<String,Object> row=jdbc.queryForMap("SELECT request_hash,result::text AS result FROM synqoria_operations.pm_commands "+
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",a.cell(),a.facility(),a.actor(),commandId);
  if(!hash.equals(String.valueOf(row.get("request_hash")).strip()))throw new WorkflowViolation("idempotency_collision");
  if(row.get("result")!=null){
   @SuppressWarnings("unchecked") Map<String,Object> replay=json.readValue((String)row.get("result"),Map.class);
   return replay;
  }
  Map<String,Object> result=work.get();
  jdbc.update("UPDATE synqoria_operations.pm_commands SET result=?::jsonb WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=?",
   json.writeValueAsString(result),a.cell(),a.facility(),a.actor(),commandId);
  return result;
 }
 private record Asset(UUID id,String status,long version,String safetyClass,String title){}
 private Asset asset(OperationWorkflow.Actor a,UUID id,boolean lock){
  List<Asset> found=jdbc.query("SELECT id,status,version,fields->>'safetyClass' AS safety_class,fields->>'title' AS title "+
   "FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? AND id=? AND module_id='M23' AND record_type='ASSET'"+(lock?" FOR SHARE":""),
   (rs,i)->new Asset(rs.getObject("id",UUID.class),rs.getString("status"),rs.getLong("version"),rs.getString("safety_class"),rs.getString("title")),
   a.cell(),a.facility(),id);
  if(found.size()!=1)throw new WorkflowViolation("not_found");
  return found.getFirst();
 }
 /** Audit event + outbox row on the asset aggregate for every maintenance mutation (M37 contract: event_type M23_PM_*). */
 private UUID audit(OperationWorkflow.Actor a,Asset asset,String action,UUID commandId,Map<String,Object> payload){
  UUID event=UUID.randomUUID();
  String body=json.writeValueAsString(new TreeMap<>(payload));
  jdbc.update("INSERT INTO synqoria_operations.audit_events(event_id,cell_id,facility_id,item_id,module_id,action,from_status,to_status,item_version,actor_id,command_id,field_digest) "+
   "VALUES(?,?,?,?,'M23',?,NULL,?,?,?,?,?)",event,a.cell(),a.facility(),asset.id(),action,asset.status(),asset.version(),a.actor(),commandId,sha(body));
  jdbc.update("INSERT INTO synqoria_operations.outbox(event_id,cell_id,facility_id,item_id,module_id,event_type,event_version,payload) VALUES(?,?,?,?,'M23',?,?,?::jsonb)",
   event,a.cell(),a.facility(),asset.id(),"M23_"+action,asset.version(),body);
  return event;
 }
 private MaintenanceRules.Policy effectivePolicy(OperationWorkflow.Actor a,String safetyClass){
  MaintenanceRules.Policy d=MaintenanceRules.defaults(safetyClass);
  List<MaintenanceRules.Policy> approved=jdbc.query("SELECT interval_days,calibration_interval_days,grace_hours,escalate_after_hours FROM synqoria_operations.pm_policy_versions "+
   "WHERE cell_id=? AND facility_id=? AND safety_class=? AND decision='APPROVED'",
   (rs,i)->new MaintenanceRules.Policy(safetyClass,rs.getInt("interval_days"),
     rs.getObject("calibration_interval_days")==null?d.calibrationIntervalDays():rs.getInt("calibration_interval_days"),rs.getInt("grace_hours"),rs.getInt("escalate_after_hours")),
   a.cell(),a.facility(),safetyClass);
  return approved.isEmpty()?d:approved.getFirst();
 }

 // ---------------------------------------------------------------- policy (per facility and safety class)
 public record PolicyRequest(String safetyClass,Integer intervalDays,Integer calibrationIntervalDays,Integer graceHours,Integer escalateAfterHours,UUID commandId){}
 public Map<String,Object> proposePolicy(OperationWorkflow.Actor a,PolicyRequest r){
  a.require("m23:pm_propose");
  String cls=MaintenanceRules.requireClass(r.safetyClass());
  int interval=bounded(r.intervalDays(),1,730,"invalid_interval"),grace=bounded(r.graceHours(),0,720,"invalid_grace"),esc=bounded(r.escalateAfterHours(),1,2160,"invalid_escalation");
  Integer cal=r.calibrationIntervalDays()==null?null:bounded(r.calibrationIntervalDays(),1,1095,"invalid_calibration_interval");
  MaintenanceRules.Policy d=MaintenanceRules.defaults(cls);
  // A facility may tighten, but never loosen beyond the explicit safe default for the class.
  if(interval>d.intervalDays()||(cal!=null&&cal>d.calibrationIntervalDays())||grace>d.graceHours()*2)throw new WorkflowViolation("policy_looser_than_safe_default");
  return store.scoped(a,()->command(a,r.commandId(),"POLICY|"+cls+"|"+interval+"|"+cal+"|"+grace+"|"+esc,()->{
   if(jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.pm_policy_versions WHERE cell_id=? AND facility_id=? AND safety_class=? AND decision='PENDING'",
     Long.class,a.cell(),a.facility(),cls)!=0)throw new WorkflowViolation("policy_decision_pending");
   int version=jdbc.queryForObject("SELECT coalesce(max(version),0)+1 FROM synqoria_operations.pm_policy_versions WHERE cell_id=? AND facility_id=? AND safety_class=?",
     Integer.class,a.cell(),a.facility(),cls);
   jdbc.update("INSERT INTO synqoria_operations.pm_policy_versions(cell_id,facility_id,safety_class,version,interval_days,calibration_interval_days,grace_hours,escalate_after_hours,proposed_by) VALUES(?,?,?,?,?,?,?,?,?)",
     a.cell(),a.facility(),cls,version,interval,cal,grace,esc,a.actor());
   return Map.of("safetyClass",cls,"version",version,"decision","PENDING");
  }));
 }
 public record Decision(boolean approve,UUID commandId){}
 public Map<String,Object> decidePolicy(OperationWorkflow.Actor a,String safetyClass,int version,Decision d){
  a.require("m23:pm_approve");
  String cls=MaintenanceRules.requireClass(safetyClass);
  return store.scoped(a,()->command(a,d.commandId(),"POLICY_DECISION|"+cls+"|"+version+"|"+d.approve(),()->{
   Map<String,Object> row;
   try{row=jdbc.queryForMap("SELECT proposed_by,decision FROM synqoria_operations.pm_policy_versions WHERE cell_id=? AND facility_id=? AND safety_class=? AND version=? FOR UPDATE",
     a.cell(),a.facility(),cls,version);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("not_found");}
   if(!"PENDING".equals(String.valueOf(row.get("decision")).strip()))throw new WorkflowViolation("decision_already_made");
   if(a.actor().equals(row.get("proposed_by")))throw new WorkflowViolation("maker_checker_denied");
   if(d.approve())jdbc.update("UPDATE synqoria_operations.pm_policy_versions SET decision='SUPERSEDED' WHERE cell_id=? AND facility_id=? AND safety_class=? AND decision='APPROVED'",a.cell(),a.facility(),cls);
   jdbc.update("UPDATE synqoria_operations.pm_policy_versions SET decision=?,decided_by=?,decided_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND safety_class=? AND version=?",
     d.approve()?"APPROVED":"REJECTED",a.actor(),a.cell(),a.facility(),cls,version);
   return Map.of("safetyClass",cls,"version",version,"decision",d.approve()?"APPROVED":"REJECTED");
  }));
 }
 public List<Map<String,Object>> policies(OperationWorkflow.Actor a){
  a.require("m23:read");
  return store.scoped(a,()->{
   List<Map<String,Object>> out=new ArrayList<>();
   for(String cls:List.of("CRITICAL","HIGH","MEDIUM","LOW")){
    MaintenanceRules.Policy p=effectivePolicy(a,cls);
    Long approved=jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.pm_policy_versions WHERE cell_id=? AND facility_id=? AND safety_class=? AND decision='APPROVED'",Long.class,a.cell(),a.facility(),cls);
    out.add(Map.of("safetyClass",cls,"intervalDays",p.intervalDays(),"calibrationIntervalDays",p.calibrationIntervalDays(),"graceHours",p.graceHours(),
      "escalateAfterHours",p.escalateAfterHours(),"source",approved!=null&&approved>0?"APPROVED_FACILITY_POLICY":"SAFE_DEFAULT"));
   }
   return out;
  });
 }

 // ---------------------------------------------------------------- plans
 public record PlanRequest(UUID assetId,String serviceType,Integer intervalDays,Integer calibrationIntervalDays,String timezone,String anchorDate,String localTime,
                           String calendarRule,UUID commandId){}
 private Map<String,Object> insertVersion(OperationWorkflow.Actor a,UUID planId,int version,Asset asset,PlanRequest r){
  String type=r.serviceType();
  if(type==null||!MaintenanceRules.SERVICE_TYPES.contains(type))throw new WorkflowViolation("invalid_service_type");
  MaintenanceRules.Policy policy=effectivePolicy(a,MaintenanceRules.requireClass(asset.safetyClass()));
  int interval=bounded(r.intervalDays(),1,730,"invalid_interval");
  if(interval>policy.intervalDays())throw new WorkflowViolation("interval_exceeds_policy");
  Integer cal=null;
  if(MaintenanceRules.needsCalibration(type)){
   cal=bounded(r.calibrationIntervalDays(),1,1095,"invalid_calibration_interval");
   if(cal>policy.calibrationIntervalDays())throw new WorkflowViolation("calibration_interval_exceeds_policy");
  }else if(r.calibrationIntervalDays()!=null)throw new WorkflowViolation("calibration_not_in_service_type");
  MaintenanceRules.zone(r.timezone());
  String rule=r.calendarRule()==null?"EXACT_DAY":r.calendarRule();
  if(!MaintenanceRules.CALENDAR_RULES.contains(rule))throw new WorkflowViolation("invalid_calendar_rule");
  final LocalDate anchor;final LocalTime time;
  try{anchor=LocalDate.parse(r.anchorDate());time=LocalTime.parse(r.localTime());}
  catch(RuntimeException e){throw new WorkflowViolation("invalid_schedule");}
  MaintenanceRules.dueAt(anchor,time,r.timezone(),rule,interval,0);
  String digest=sha(planId+"|"+version+"|"+type+"|"+asset.safetyClass()+"|"+interval+"|"+cal+"|"+r.timezone()+"|"+anchor+"|"+time+"|"+rule+"|"+policy.graceHours()+"|"+policy.escalateAfterHours());
  jdbc.update("INSERT INTO synqoria_operations.pm_plan_versions(plan_id,cell_id,facility_id,version,service_type,safety_class,interval_days,calibration_interval_days,timezone,anchor_date,local_time,calendar_rule,grace_hours,escalate_after_hours,content_digest,proposed_by) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",planId,a.cell(),a.facility(),version,type,asset.safetyClass(),interval,cal,r.timezone(),java.sql.Date.valueOf(anchor),java.sql.Time.valueOf(time),rule,
   policy.graceHours(),policy.escalateAfterHours(),digest,a.actor());
  return Map.of("planId",planId.toString(),"version",version,"decision","PENDING","contentDigest",digest);
 }
 public Map<String,Object> proposePlan(OperationWorkflow.Actor a,PlanRequest r){
  a.require("m23:pm_propose");
  need(r.assetId());
  return store.scoped(a,()->command(a,r.commandId(),"PLAN|"+r.assetId()+"|"+r.serviceType()+"|"+r.intervalDays()+"|"+r.calibrationIntervalDays()+"|"+r.timezone()+"|"+r.anchorDate()+"|"+r.localTime()+"|"+r.calendarRule(),()->{
   Asset asset=asset(a,r.assetId(),true);
   if(Set.of("RETIRED").contains(asset.status()))throw new WorkflowViolation("asset_retired");
   UUID planId=UUID.randomUUID();
   try{jdbc.update("INSERT INTO synqoria_operations.pm_plans(plan_id,cell_id,facility_id,asset_ref,created_by) VALUES(?,?,?,?,?)",planId,a.cell(),a.facility(),asset.id(),a.actor());}
   catch(DataIntegrityViolationException e){throw new WorkflowViolation("plan_exists_use_new_version");}
   Map<String,Object> v=insertVersion(a,planId,1,asset,r);
   audit(a,asset,"PM_PLAN_PROPOSED",r.commandId(),Map.of("planId",planId.toString(),"version",1));
   return v;
  }));
 }
 public Map<String,Object> proposeVersion(OperationWorkflow.Actor a,UUID planId,PlanRequest r){
  a.require("m23:pm_propose");
  need(planId);
  return store.scoped(a,()->command(a,r.commandId(),"PLAN_VERSION|"+planId+"|"+r.serviceType()+"|"+r.intervalDays()+"|"+r.calibrationIntervalDays()+"|"+r.timezone()+"|"+r.anchorDate()+"|"+r.localTime()+"|"+r.calendarRule(),()->{
   Map<String,Object> plan;
   try{plan=jdbc.queryForMap("SELECT asset_ref,retired_at FROM synqoria_operations.pm_plans WHERE cell_id=? AND facility_id=? AND plan_id=? FOR UPDATE",a.cell(),a.facility(),planId);}
   catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("not_found");}
   if(plan.get("retired_at")!=null)throw new WorkflowViolation("plan_retired");
   Asset asset=asset(a,(UUID)plan.get("asset_ref"),true);
   if(jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.pm_plan_versions WHERE plan_id=? AND decision='PENDING'",Long.class,planId)!=0)throw new WorkflowViolation("plan_decision_pending");
   int version=jdbc.queryForObject("SELECT max(version)+1 FROM synqoria_operations.pm_plan_versions WHERE plan_id=?",Integer.class,planId);
   Map<String,Object> v=insertVersion(a,planId,version,asset,r);
   audit(a,asset,"PM_PLAN_PROPOSED",r.commandId(),Map.of("planId",planId.toString(),"version",version));
   return v;
  }));
 }
 public Map<String,Object> decidePlan(OperationWorkflow.Actor a,UUID planId,int version,Decision d){
  a.require("m23:pm_approve");
  need(planId);
  return store.scoped(a,()->command(a,d.commandId(),"PLAN_DECISION|"+planId+"|"+version+"|"+d.approve(),()->{
   Map<String,Object> row;
   try{row=jdbc.queryForMap("SELECT v.proposed_by,v.decision,p.asset_ref FROM synqoria_operations.pm_plan_versions v JOIN synqoria_operations.pm_plans p ON p.plan_id=v.plan_id "+
     "WHERE v.cell_id=? AND v.facility_id=? AND v.plan_id=? AND v.version=? FOR UPDATE OF v",a.cell(),a.facility(),planId,version);}
   catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("not_found");}
   if(!"PENDING".equals(String.valueOf(row.get("decision")).strip()))throw new WorkflowViolation("decision_already_made");
   if(a.actor().equals(row.get("proposed_by")))throw new WorkflowViolation("maker_checker_denied");
   Asset asset=asset(a,(UUID)row.get("asset_ref"),true);
   if(d.approve()){
    jdbc.update("UPDATE synqoria_operations.pm_plan_versions SET decision='SUPERSEDED' WHERE plan_id=? AND decision='APPROVED'",planId);
    // Occurrences of older versions that were never linked to work are withdrawn; linked / completed history is untouched.
    jdbc.update("UPDATE synqoria_operations.pm_occurrences SET state='CANCELLED' WHERE cell_id=? AND facility_id=? AND plan_id=? AND plan_version<>? AND state='PLANNED'",a.cell(),a.facility(),planId,version);
   }
   jdbc.update("UPDATE synqoria_operations.pm_plan_versions SET decision=?,decided_by=?,decided_at=clock_timestamp() WHERE plan_id=? AND version=?",d.approve()?"APPROVED":"REJECTED",a.actor(),planId,version);
   audit(a,asset,d.approve()?"PM_PLAN_APPROVED":"PM_PLAN_REJECTED",d.commandId(),Map.of("planId",planId.toString(),"version",version));
   return Map.of("planId",planId.toString(),"version",version,"decision",d.approve()?"APPROVED":"REJECTED");
  }));
 }
 public List<Map<String,Object>> plans(OperationWorkflow.Actor a,int limit){
  a.require("m23:read");limit(limit,100);
  return store.scoped(a,()->jdbc.queryForList("SELECT p.plan_id,p.asset_ref,v.version,v.service_type,v.safety_class,v.interval_days,v.calibration_interval_days,v.timezone,v.anchor_date,v.local_time,v.calendar_rule,"+
   "v.grace_hours,v.escalate_after_hours,v.decision,v.proposed_at,v.decided_at FROM synqoria_operations.pm_plans p JOIN synqoria_operations.pm_plan_versions v ON v.plan_id=p.plan_id "+
   "WHERE p.cell_id=? AND p.facility_id=? AND p.retired_at IS NULL AND v.version=(SELECT max(version) FROM synqoria_operations.pm_plan_versions x WHERE x.plan_id=p.plan_id) ORDER BY v.proposed_at DESC,p.plan_id LIMIT ?",
   a.cell(),a.facility(),limit).stream().map(MaintenanceService::clean).toList());
 }

 // ---------------------------------------------------------------- planner: mutually exclusive lease, deterministic generation, escalation
 private long acquire(OperationWorkflow.Actor a){
  List<Long> fence=jdbc.query("INSERT INTO synqoria_operations.pm_planner_state(cell_id,facility_id,holder_id,fence,lease_expires_at,last_started_at) "+
   "VALUES(?,?,?,1,clock_timestamp()+make_interval(secs=>?),clock_timestamp()) ON CONFLICT(cell_id,facility_id) DO UPDATE SET holder_id=EXCLUDED.holder_id,"+
   "fence=synqoria_operations.pm_planner_state.fence+1,lease_expires_at=EXCLUDED.lease_expires_at,last_started_at=EXCLUDED.last_started_at "+
   "WHERE synqoria_operations.pm_planner_state.lease_expires_at IS NULL OR synqoria_operations.pm_planner_state.lease_expires_at<=clock_timestamp() RETURNING fence",
   (rs,i)->rs.getLong(1),a.cell(),a.facility(),a.actor(),(double)LEASE_SECONDS);
  if(fence.isEmpty())throw new WorkflowViolation("planner_lease_held");
  return fence.getFirst();
 }
 private void release(OperationWorkflow.Actor a,long fence,String errorCode){
  jdbc.update("UPDATE synqoria_operations.pm_planner_state SET holder_id=NULL,lease_expires_at=NULL,last_error_code=COALESCE(?,last_error_code) WHERE cell_id=? AND facility_id=? AND holder_id=? AND fence=?",
   errorCode,a.cell(),a.facility(),a.actor(),fence);
 }
 /** Acquire the lease (own transaction), generate occurrences (own transaction, fenced), always release. */
 public Map<String,Object> runPlanner(OperationWorkflow.Actor a,UUID commandId,int horizonDays){
  a.require("m23:pm_plan_run");
  if(commandId==null)throw new WorkflowViolation("idempotency_key_required");
  long fence=store.scoped(a,()->acquire(a));
  return runWithFence(a,fence,commandId,horizonDays,true);
 }
 /** Test/ops seam: run with an explicit fence token; a stale fence is rejected before any write. */
 Map<String,Object> runWithFence(OperationWorkflow.Actor a,long fence,UUID commandId,int horizonDays,boolean releaseAfter){
  try{
   return store.scoped(a,()->command(a,commandId,"GENERATE|"+horizonDays,()->generate(a,fence,horizonDays)));
  }catch(RuntimeException e){
   String code=e instanceof WorkflowViolation?e.getMessage():"planner_failed";
   try{store.scoped(a,()->{release(a,fence,code);return null;});}catch(RuntimeException ignored){/* lease expires by itself */}
   throw e;
  }finally{
   if(releaseAfter)try{store.scoped(a,()->{release(a,fence,null);return null;});}catch(RuntimeException ignored){/* lease expires by itself */}
  }
 }
 private Map<String,Object> generate(OperationWorkflow.Actor a,long fence,int horizonDays){
  Map<String,Object> lease;
  try{lease=jdbc.queryForMap("SELECT holder_id,fence,lease_expires_at>clock_timestamp() AS live FROM synqoria_operations.pm_planner_state WHERE cell_id=? AND facility_id=? FOR UPDATE",a.cell(),a.facility());}
  catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("planner_lease_lost");}
  if(!a.actor().equals(lease.get("holder_id"))||((Number)lease.get("fence")).longValue()!=fence||!Boolean.TRUE.equals(lease.get("live")))throw new WorkflowViolation("planner_lease_lost");
  Instant now=clock.instant();
  List<Map<String,Object>> plans=jdbc.queryForList("SELECT p.plan_id,p.asset_ref,v.version,v.service_type,v.interval_days,v.timezone,v.anchor_date,v.local_time,v.calendar_rule "+
   "FROM synqoria_operations.pm_plans p JOIN synqoria_operations.pm_plan_versions v ON v.plan_id=p.plan_id AND v.decision='APPROVED' "+
   "JOIN synqoria_operations.items a ON a.cell_id=p.cell_id AND a.facility_id=p.facility_id AND a.id=p.asset_ref AND a.status<>'RETIRED' "+
   "WHERE p.cell_id=? AND p.facility_id=? AND p.retired_at IS NULL ORDER BY p.plan_id LIMIT 200",a.cell(),a.facility());
  int created=0;
  for(Map<String,Object> p:plans){
   UUID planId=(UUID)p.get("plan_id");int version=((Number)p.get("version")).intValue(),interval=((Number)p.get("interval_days")).intValue();
   LocalDate anchor=((java.sql.Date)p.get("anchor_date")).toLocalDate();LocalTime time=((java.sql.Time)p.get("local_time")).toLocalTime();
   String tz=(String)p.get("timezone"),rule=((String)p.get("calendar_rule")).strip();
   for(int seq:MaintenanceRules.sequencesToGenerate(anchor,time,tz,rule,interval,now,horizonDays,24)){
    created+=jdbc.update("INSERT INTO synqoria_operations.pm_occurrences(occurrence_id,cell_id,facility_id,plan_id,plan_version,seq,asset_ref,kind,due_at,generated_fence) VALUES(?,?,?,?,?,?,?,?,?,?) "+
     "ON CONFLICT(occurrence_id) DO NOTHING",MaintenanceRules.occurrenceId(planId,version,seq),a.cell(),a.facility(),planId,version,seq,p.get("asset_ref"),
     ((String)p.get("service_type")).strip(),Timestamp.from(MaintenanceRules.dueAt(anchor,time,tz,rule,interval,seq)),fence);
   }
  }
  jdbc.update("UPDATE synqoria_operations.pm_planner_state SET last_success_at=clock_timestamp(),last_error_code=NULL,last_generated=? WHERE cell_id=? AND facility_id=?",created,a.cell(),a.facility());
  return Map.of("generated",created,"plans",plans.size(),"fence",fence);
 }
 /** Raise one escalation per (occurrence, level) for overdue open occurrences; emits the M37 notification CONTRACT via outbox only. */
 public Map<String,Object> escalateOverdue(OperationWorkflow.Actor a,UUID commandId){
  a.require("m23:pm_plan_run");
  return store.scoped(a,()->command(a,commandId,"ESCALATE",()->{
   Instant now=clock.instant();
   List<Map<String,Object>> open=jdbc.queryForList("SELECT o.occurrence_id,o.asset_ref,o.due_at,o.state,v.grace_hours,v.escalate_after_hours,"+
    "coalesce((SELECT max(level) FROM synqoria_operations.pm_escalations e WHERE e.occurrence_id=o.occurrence_id),0) AS done "+
    "FROM synqoria_operations.pm_occurrences o JOIN synqoria_operations.pm_plan_versions v ON v.plan_id=o.plan_id AND v.version=o.plan_version "+
    "WHERE o.cell_id=? AND o.facility_id=? AND o.state IN('PLANNED','WORK_LINKED') AND o.due_at<? ORDER BY o.due_at,o.occurrence_id LIMIT 200 FOR UPDATE OF o",a.cell(),a.facility(),Timestamp.from(now));
   int raised=0;
   for(Map<String,Object> o:open){
    int level=MaintenanceRules.escalationLevel(now,((Timestamp)o.get("due_at")).toInstant(),((Number)o.get("grace_hours")).intValue(),((Number)o.get("escalate_after_hours")).intValue());
    int done=((Number)o.get("done")).intValue();
    UUID occurrence=(UUID)o.get("occurrence_id");
    Asset asset=asset(a,(UUID)o.get("asset_ref"),true);
    for(int l=done+1;l<=level;l++){
     UUID event=audit(a,asset,"PM_OVERDUE_ESCALATION",commandId,Map.of("occurrenceId",occurrence.toString(),"assetId",asset.id().toString(),"level",l,"dueAt",((Timestamp)o.get("due_at")).toInstant().toString()));
     jdbc.update("INSERT INTO synqoria_operations.pm_escalations(escalation_id,cell_id,facility_id,occurrence_id,level,raised_by,event_id) VALUES(?,?,?,?,?,?,?)",
      UUID.randomUUID(),a.cell(),a.facility(),occurrence,(short)l,a.actor(),event);
     raised++;
    }
   }
   return Map.of("escalationsRaised",raised,"openOverdueExamined",open.size());
  }));
 }
 /** Unknown or stale is never healthy. */
 public Map<String,Object> health(OperationWorkflow.Actor a,int staleAfterMinutes){
  a.require("m23:read");
  if(staleAfterMinutes<1||staleAfterMinutes>1440)throw new WorkflowViolation("invalid_threshold");
  return store.scoped(a,()->{
   List<Map<String,Object>> st=jdbc.queryForList("SELECT last_success_at,last_error_code,last_generated,(lease_expires_at>clock_timestamp()) AS leased FROM synqoria_operations.pm_planner_state WHERE cell_id=? AND facility_id=?",a.cell(),a.facility());
   Map<String,Object> counts=jdbc.queryForMap("SELECT count(*) FILTER (WHERE o.state='PLANNED' AND o.due_at<clock_timestamp()) AS unprocessed_due,"+
    "count(*) FILTER (WHERE o.state IN('PLANNED','WORK_LINKED') AND o.due_at+make_interval(hours=>v.grace_hours)<clock_timestamp()) AS overdue,"+
    "count(*) FILTER (WHERE o.state IN('PLANNED','WORK_LINKED') AND o.due_at+make_interval(hours=>v.grace_hours)<clock_timestamp() AND NOT EXISTS(SELECT 1 FROM synqoria_operations.pm_escalations e WHERE e.occurrence_id=o.occurrence_id)) AS overdue_unescalated "+
    "FROM synqoria_operations.pm_occurrences o JOIN synqoria_operations.pm_plan_versions v ON v.plan_id=o.plan_id AND v.version=o.plan_version WHERE o.cell_id=? AND o.facility_id=?",a.cell(),a.facility());
   String status;Object last=null;String error=null;
   if(st.isEmpty()||st.getFirst().get("last_success_at")==null)status="UNKNOWN";
   else{
    Instant ok=((Timestamp)st.getFirst().get("last_success_at")).toInstant();last=ok.toString();error=(String)st.getFirst().get("last_error_code");
    boolean stale=Duration.between(ok,clock.instant()).toMinutes()>staleAfterMinutes;
    status=stale||error!=null?"STALE":"HEALTHY";
   }
   if("HEALTHY".equals(status)&&((Number)counts.get("overdue_unescalated")).longValue()>0)status="DEGRADED";
   Map<String,Object> out=new LinkedHashMap<>();
   out.put("status",status);out.put("lastSuccessAt",last);out.put("lastErrorCode",error);
   out.put("unprocessedDue",((Number)counts.get("unprocessed_due")).longValue());out.put("overdue",((Number)counts.get("overdue")).longValue());
   out.put("overdueUnescalated",((Number)counts.get("overdue_unescalated")).longValue());
   return out;
  });
 }

 // ---------------------------------------------------------------- occurrence <-> existing work orders
 public record LinkRequest(UUID workOrderId,UUID commandId){}
 public Map<String,Object> linkWorkOrder(OperationWorkflow.Actor a,UUID occurrenceId,LinkRequest r){
  a.require("m23:pm_link");need(occurrenceId);need(r.workOrderId());
  return store.scoped(a,()->command(a,r.commandId(),"LINK|"+occurrenceId+"|"+r.workOrderId(),()->{
   Map<String,Object> o;
   try{o=jdbc.queryForMap("SELECT asset_ref,state FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND facility_id=? AND occurrence_id=? FOR UPDATE",a.cell(),a.facility(),occurrenceId);}
   catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("not_found");}
   if(!"PLANNED".equals(String.valueOf(o.get("state")).strip()))throw new WorkflowViolation("occurrence_not_plannable");
   Asset asset=asset(a,(UUID)o.get("asset_ref"),true);
   Long wo=jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? AND id=? AND module_id='M23' AND record_type='WORK_ORDER' AND status IN('WORK_OPEN','WORK_ASSIGNED')",
     Long.class,a.cell(),a.facility(),r.workOrderId());
   if(wo==null||wo!=1)throw new WorkflowViolation("work_order_not_open");
   try{jdbc.update("UPDATE synqoria_operations.pm_occurrences SET state='WORK_LINKED',work_order_ref=?,linked_by=?,linked_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND occurrence_id=?",
     r.workOrderId(),a.actor(),a.cell(),a.facility(),occurrenceId);}
   catch(DataIntegrityViolationException e){
    String m=String.valueOf(e.getMostSpecificCause().getMessage());
    if(m.contains("m23_asset_not_offline_for_service"))throw new WorkflowViolation("asset_not_offline_for_work");
    if(m.contains("m23_work_order_asset_mismatch"))throw new WorkflowViolation("work_order_asset_mismatch");
    throw new WorkflowViolation("asset_already_under_service");
   }
   audit(a,asset,"PM_OCCURRENCE_LINKED",r.commandId(),Map.of("occurrenceId",occurrenceId.toString(),"workOrderId",r.workOrderId().toString()));
   return Map.of("occurrenceId",occurrenceId.toString(),"state","WORK_LINKED","workOrderId",r.workOrderId().toString());
  }));
 }
 public record CompleteRequest(UUID commandId){}
 /** Closes the occurrence only after the existing, independently verified work order (and calibration evidence when required). Never touches the asset. */
 public Map<String,Object> completeOccurrence(OperationWorkflow.Actor a,UUID occurrenceId,CompleteRequest r){
  a.require("m23:pm_complete");need(occurrenceId);
  return store.scoped(a,()->command(a,r.commandId(),"COMPLETE|"+occurrenceId,()->{
   Map<String,Object> o;
   try{o=jdbc.queryForMap("SELECT o.asset_ref,o.state,o.linked_by,m.technician_ref FROM synqoria_operations.pm_occurrences o LEFT JOIN synqoria_operations.maintenance_orders m "+
    "ON m.item_id=o.work_order_ref AND m.cell_id=o.cell_id AND m.facility_id=o.facility_id WHERE o.cell_id=? AND o.facility_id=? AND o.occurrence_id=? FOR UPDATE OF o",a.cell(),a.facility(),occurrenceId);}
   catch(org.springframework.dao.EmptyResultDataAccessException e){throw new WorkflowViolation("not_found");}
   if(!"WORK_LINKED".equals(String.valueOf(o.get("state")).strip()))throw new WorkflowViolation("occurrence_not_in_service");
   if(a.actor().equals(o.get("linked_by"))||a.actor().toString().equalsIgnoreCase(String.valueOf(o.get("technician_ref"))))throw new WorkflowViolation("completion_must_be_independent");
   Asset asset=asset(a,(UUID)o.get("asset_ref"),true);
   try{jdbc.update("UPDATE synqoria_operations.pm_occurrences SET state='COMPLETED',completed_by=?,completed_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND occurrence_id=?",a.actor(),a.cell(),a.facility(),occurrenceId);}
   catch(DataIntegrityViolationException e){
    String m=String.valueOf(e.getMostSpecificCause().getMessage());
    if(m.contains("m23_work_order_not_verified"))throw new WorkflowViolation("work_order_not_verified");
    if(m.contains("m23_calibration_evidence_required"))throw new WorkflowViolation("calibration_evidence_required");
    throw new WorkflowViolation("occurrence_integrity_conflict");
   }
   audit(a,asset,"PM_OCCURRENCE_COMPLETED",r.commandId(),Map.of("occurrenceId",occurrenceId.toString()));
   return Map.of("occurrenceId",occurrenceId.toString(),"state","COMPLETED");
  }));
 }

 // ---------------------------------------------------------------- calibration, external references, costs
 public record CalibrationRequest(String performedAt,String nextDueAt,String evidenceRef,UUID commandId){}
 /** The record is accepted only if INDEPENDENT trusted evidence (issued outside the runtime role) exists, is valid now and was approved by someone other than the recorder. */
 public Map<String,Object> recordCalibration(OperationWorkflow.Actor a,UUID assetId,CalibrationRequest r){
  a.require("m23:calibrate");need(assetId);
  String ref=MaintenanceRules.requireRef(r.evidenceRef());
  final Instant performed,next;
  try{performed=Instant.parse(r.performedAt());next=Instant.parse(r.nextDueAt());}catch(RuntimeException e){throw new WorkflowViolation("invalid_calibration_time");}
  Instant now=clock.instant();
  if(!next.isAfter(performed)||performed.isAfter(now.plusSeconds(300)))throw new WorkflowViolation("invalid_calibration_time");
  return store.scoped(a,()->command(a,r.commandId(),"CALIBRATION|"+assetId+"|"+performed+"|"+next+"|"+ref,()->{
   Asset asset=asset(a,assetId,true);
   if(Set.of("RETIRED","REGISTERED").contains(asset.status()))throw new WorkflowViolation("asset_not_calibratable");
   List<UUID> evidence=jdbc.query("SELECT evidence_id FROM synqoria_operations.external_evidence WHERE cell_id=? AND facility_id=? AND item_id=? AND for_action='RECORD_CALIBRATION' AND expected_version=? "+
    "AND evidence_kind='calibration_safe' AND external_ref=? AND approver_id<>? AND revoked_at IS NULL AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
    (rs,i)->rs.getObject(1,UUID.class),a.cell(),a.facility(),assetId,asset.version(),ref,a.actor());
   if(evidence.isEmpty())throw new WorkflowViolation("trusted_evidence_required");
   MaintenanceRules.Policy cap=effectivePolicy(a,MaintenanceRules.requireClass(asset.safetyClass()));
   if(Duration.between(performed,next).toDays()>cap.calibrationIntervalDays())throw new WorkflowViolation("calibration_interval_exceeds_policy");
   UUID id=UUID.randomUUID();
   try{jdbc.update("INSERT INTO synqoria_operations.pm_calibrations(calibration_id,cell_id,facility_id,asset_ref,performed_at,next_due_at,evidence_id,external_ref,recorded_by) VALUES(?,?,?,?,?,?,?,?,?)",
     id,a.cell(),a.facility(),assetId,Timestamp.from(performed),Timestamp.from(next),evidence.getFirst(),ref,a.actor());}
   catch(DataIntegrityViolationException e){throw new WorkflowViolation("evidence_already_used");}
   audit(a,asset,"PM_CALIBRATION_RECORDED",r.commandId(),Map.of("calibrationId",id.toString(),"nextDueAt",next.toString()));
   return Map.of("calibrationId",id.toString(),"nextDueAt",next.toString());
  }));
 }
 public record ServiceReferenceRequest(String kind,String externalRef,String issuerName,UUID commandId){}
 /** Staff-typed pointer to a supplier / engineer document. It is stored as UNATTESTED and can never satisfy a trusted-evidence gate. */
 public Map<String,Object> addServiceReference(OperationWorkflow.Actor a,UUID workOrderId,ServiceReferenceRequest r){
  a.require("m23:pm_record");need(workOrderId);
  if(r.kind()==null||!Set.of("SUPPLIER_REPORT","ENGINEER_CERTIFICATE","MANUFACTURER_BULLETIN").contains(r.kind()))throw new WorkflowViolation("invalid_reference_kind");
  String ref=MaintenanceRules.requireRef(r.externalRef());
  if(r.issuerName()==null||r.issuerName().isBlank()||r.issuerName().length()>160||r.issuerName().chars().anyMatch(c->c<32))throw new WorkflowViolation("invalid_issuer");
  return store.scoped(a,()->command(a,r.commandId(),"SERVICE_REF|"+workOrderId+"|"+r.kind()+"|"+ref+"|"+r.issuerName(),()->{
   requireWorkOrder(a,workOrderId);
   UUID id=UUID.randomUUID();
   try{jdbc.update("INSERT INTO synqoria_operations.pm_service_references(reference_id,cell_id,facility_id,work_order_ref,kind,external_ref,issuer_name,recorded_by) VALUES(?,?,?,?,?,?,?,?)",
     id,a.cell(),a.facility(),workOrderId,r.kind(),ref,r.issuerName().strip(),a.actor());}
   catch(DataIntegrityViolationException e){throw new WorkflowViolation("reference_already_recorded");}
   return Map.of("referenceId",id.toString(),"attested",false);
  }));
 }
 public record CostRequest(Long amountMinor,String currency,String costCentreRef,String noteRef,UUID commandId){}
 /** Administrative cost reference only: no general-ledger posting, no payment, no approval implied. */
 public Map<String,Object> addCostReference(OperationWorkflow.Actor a,UUID workOrderId,CostRequest r){
  a.require("m23:pm_record");need(workOrderId);
  if(r.amountMinor()==null||r.amountMinor()<0||r.amountMinor()>1_000_000_000_000L)throw new WorkflowViolation("invalid_amount");
  if(r.currency()==null||!r.currency().matches("[A-Z]{3}"))throw new WorkflowViolation("invalid_currency");
  String centre=MaintenanceRules.requireRef(r.costCentreRef()),note=r.noteRef()==null?"-":MaintenanceRules.requireRef(r.noteRef());
  return store.scoped(a,()->command(a,r.commandId(),"COST|"+workOrderId+"|"+r.amountMinor()+"|"+r.currency()+"|"+centre+"|"+note,()->{
   requireWorkOrder(a,workOrderId);
   UUID id=UUID.randomUUID();
   try{jdbc.update("INSERT INTO synqoria_operations.pm_cost_references(cost_id,cell_id,facility_id,work_order_ref,amount_minor,currency,cost_centre_ref,note_ref,recorded_by) VALUES(?,?,?,?,?,?,?,?,?)",
     id,a.cell(),a.facility(),workOrderId,r.amountMinor(),r.currency(),centre,note,a.actor());}
   catch(DataIntegrityViolationException e){throw new WorkflowViolation("cost_already_recorded");}
   return Map.of("costId",id.toString(),"posted",false);
  }));
 }
 private void requireWorkOrder(OperationWorkflow.Actor a,UUID id){
  Long n=jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? AND id=? AND module_id='M23' AND record_type='WORK_ORDER'",Long.class,a.cell(),a.facility(),id);
  if(n==null||n!=1)throw new WorkflowViolation("not_found");
 }

 // ---------------------------------------------------------------- bounded read models
 public List<Map<String,Object>> calendar(OperationWorkflow.Actor a,Instant from,Instant to,int limit){
  a.require("m23:read");limit(limit,200);
  if(from==null||to==null||!to.isAfter(from)||Duration.between(from,to).toDays()>366)throw new WorkflowViolation("invalid_range");
  Instant now=clock.instant();
  return store.scoped(a,()->jdbc.queryForList("SELECT o.occurrence_id,o.plan_id,o.plan_version,o.kind,o.due_at,o.state,o.asset_ref,o.work_order_ref,a.status AS asset_status,"+
   "a.fields->>'title' AS asset_title,a.fields->>'safetyClass' AS safety_class,a.fields->>'locationRef' AS location_ref,v.grace_hours,v.escalate_after_hours,"+
   "EXISTS(SELECT 1 FROM synqoria_operations.pm_calibrations c WHERE c.cell_id=o.cell_id AND c.facility_id=o.facility_id AND c.asset_ref=o.asset_ref AND c.next_due_at>clock_timestamp()) AS calibration_current "+
   "FROM synqoria_operations.pm_occurrences o JOIN synqoria_operations.pm_plan_versions v ON v.plan_id=o.plan_id AND v.version=o.plan_version "+
   "JOIN synqoria_operations.items a ON a.cell_id=o.cell_id AND a.facility_id=o.facility_id AND a.id=o.asset_ref "+
   "WHERE o.cell_id=? AND o.facility_id=? AND o.due_at>=? AND o.due_at<? ORDER BY o.due_at,o.occurrence_id LIMIT ?",
   a.cell(),a.facility(),Timestamp.from(from),Timestamp.from(to),limit).stream().map(row->{
    Instant due=((Timestamp)row.get("due_at")).toInstant();
    int grace=((Number)row.get("grace_hours")).intValue(),esc=((Number)row.get("escalate_after_hours")).intValue();
    String state=String.valueOf(row.get("state")).strip(),assetStatus=(String)row.get("asset_status");
    Map<String,Object> out=clean(row);
    out.remove("graceHours");out.remove("escalateAfterHours");
    out.put("projection",MaintenanceRules.project(now,due,grace,state,assetStatus).name());
    out.put("escalationLevel",("PLANNED".equals(state)||"WORK_LINKED".equals(state))?MaintenanceRules.escalationLevel(now,due,grace,esc):0);
    return out;
   }).toList());
 }
 /** History without staff identities unless the caller also holds m23:audit_read. */
 public Map<String,Object> history(OperationWorkflow.Actor a,UUID assetId,int limit){
  a.require("m23:read");limit(limit,100);need(assetId);
  boolean identities=a.permissions().contains("m23:audit_read");
  return store.scoped(a,()->{
   asset(a,assetId,false);
   List<Map<String,Object>> events=jdbc.queryForList("SELECT e.occurred_at,e.module_id,e.action,e.from_status,e.to_status,e.item_version"+(identities?",e.actor_id":"")+
    " FROM synqoria_operations.audit_events e WHERE e.cell_id=? AND e.facility_id=? AND (e.item_id=? OR e.item_id IN(SELECT item_id FROM synqoria_operations.maintenance_orders WHERE cell_id=? AND facility_id=? AND asset_ref=?)) "+
    "ORDER BY e.occurred_at DESC,e.event_id LIMIT ?",a.cell(),a.facility(),assetId,a.cell(),a.facility(),assetId,limit).stream().map(MaintenanceService::clean).toList();
   List<Map<String,Object>> occurrences=jdbc.queryForList("SELECT occurrence_id,kind,due_at,state,work_order_ref,completed_at FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND facility_id=? AND asset_ref=? ORDER BY due_at DESC LIMIT ?",
    a.cell(),a.facility(),assetId,limit).stream().map(MaintenanceService::clean).toList();
   List<Map<String,Object>> calibrations=jdbc.queryForList("SELECT calibration_id,performed_at,next_due_at FROM synqoria_operations.pm_calibrations WHERE cell_id=? AND facility_id=? AND asset_ref=? ORDER BY performed_at DESC LIMIT ?",
    a.cell(),a.facility(),assetId,limit).stream().map(MaintenanceService::clean).toList();
   Map<String,Object> out=new LinkedHashMap<>();
   out.put("assetId",assetId.toString());out.put("identitiesIncluded",identities);out.put("events",events);out.put("occurrences",occurrences);out.put("calibrations",calibrations);
   return out;
  });
 }
 /** Work orders awaiting verification that THIS caller may independently verify, with a fail-closed evidence-readiness flag. */
 public List<Map<String,Object>> inspectionQueue(OperationWorkflow.Actor a,int limit){
  a.require("m23:return");limit(limit,100);
  String me=a.actor().toString();
  return store.scoped(a,()->jdbc.queryForList("SELECT i.id AS work_order_id,i.version,i.fields->>'assetRef' AS asset_ref,m.due_at,"+
   "EXISTS(SELECT 1 FROM synqoria_operations.external_evidence e WHERE e.cell_id=i.cell_id AND e.facility_id=i.facility_id AND e.item_id=i.id AND e.for_action='VERIFY_WORK' "+
   "AND e.expected_version=i.version AND e.evidence_kind='calibration_safe' AND e.revoked_at IS NULL AND e.valid_from<=clock_timestamp() AND e.valid_until>clock_timestamp() AND e.approver_id<>i.created_by) AS evidence_ready "+
   "FROM synqoria_operations.items i JOIN synqoria_operations.maintenance_orders m ON m.item_id=i.id AND m.cell_id=i.cell_id AND m.facility_id=i.facility_id "+
   "WHERE i.cell_id=? AND i.facility_id=? AND i.module_id='M23' AND i.record_type='WORK_ORDER' AND i.status='WORK_COMPLETE_PENDING_VERIFICATION' "+
   "AND i.created_by::text<>? AND i.last_actor::text<>? AND coalesce(m.technician_ref,'')<>? ORDER BY m.due_at,i.id LIMIT ?",
   a.cell(),a.facility(),me,me,me,limit).stream().map(MaintenanceService::clean).toList());
 }
}
