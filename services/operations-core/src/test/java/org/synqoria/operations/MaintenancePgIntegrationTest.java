package org.synqoria.operations;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.operations.domain.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M23 preventive maintenance on a REAL disposable PostgreSQL 18.6 as the restricted runtime role (FORCE RLS), driven through the
 * existing OperationsStore state machine. Synthetic data only; no real hospital credential, supplier attestation or clinical record.
 * Each test method gets its own cell/facility, so rows are isolated by RLS exactly as in production.
 */
final class MaintenancePgIntegrationTest {
 private final UUID CELL=UUID.randomUUID(),FAC=UUID.randomUUID();
 private final UUID MAKER=UUID.randomUUID(),CHECKER=UUID.randomUUID(),TECH=UUID.randomUUID(),PLANNER=UUID.randomUUID(),ADMIN_APPROVER=UUID.randomUUID();
 private final DataSource runtimeDs=new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_DB_USER"),System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));
 private JdbcTemplate admin(){return new JdbcTemplate(new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD")));}
 private final JsonMapper json=JsonMapper.builder().build();
 private OperationsStore store(){return new OperationsStore(new JdbcTemplate(runtimeDs),json,new DataSourceTransactionManager(runtimeDs),"synqoria_operations_runtime");}
 private MaintenanceService service(OperationsStore s,Clock clock){return new MaintenanceService(s,new JdbcTemplate(runtimeDs),json,clock);}
 private MaintenanceService service(OperationsStore s){return service(s,Clock.systemUTC());}
 private static void ready(){Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));}
 private OperationWorkflow.Actor actor(UUID id,String...p){return new OperationWorkflow.Actor(id,CELL,FAC,Set.of(p));}
 private OperationWorkflow.Actor maker(){return actor(MAKER,"m23:create","m23:maintenance","m23:pm_propose","m23:pm_link","m23:pm_record","m23:read","m23:calibrate");}
 private OperationWorkflow.Actor checker(){return actor(CHECKER,"m23:commission","m23:activate","m23:maintenance","m23:return","m23:retire","m23:pm_approve","m23:pm_complete","m23:read","m23:audit_read","m23:calibrate");}
 private OperationWorkflow.Actor planner(){return actor(PLANNER,"m23:pm_plan_run","m23:read");}
 private static Map<String,Object> data(Object...kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 private OperationWorkflow.Item create(OperationsStore s,String type,OperationWorkflow.Actor a,Map<String,Object> f){return s.create(a,"M23",new OperationsApi.NewItem(type,f,UUID.randomUUID()));}
 private OperationWorkflow.Item next(OperationsStore s,OperationWorkflow.Item i,String action,OperationWorkflow.Actor a,Map<String,Object> f){
  return s.transition(a,"M23",i.id(),new OperationsApi.TransitionItem(action,i.version(),f,UUID.randomUUID()));
 }
 private void evidence(UUID item,String action,long version,String kind,String ref,UUID approver,String from,String until){
  admin().update("INSERT INTO synqoria_operations.external_evidence(evidence_id,cell_id,facility_id,item_id,for_action,expected_version,evidence_kind,external_ref,issuer_ref,approver_id,valid_from,valid_until,digest_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,'external-protocol-synthetic',?,clock_timestamp()+?::interval,clock_timestamp()+?::interval,repeat('c',64))",UUID.randomUUID(),CELL,FAC,item,action,version,kind,ref,approver,from,until);
 }
 private void evidence(UUID item,String action,long version,String kind,String ref){evidence(item,action,version,kind,ref,ADMIN_APPROVER,"-1 minute","1 hour");}
 private void grant(UUID actor,String[] permissions,String shiftFrom,String shiftUntil){
  admin().update("INSERT INTO synqoria_operations.workforce_grants(cell_id,facility_id,actor_id,grant_revision,permissions,active,shift_from,shift_until,valid_until,approved_by,source_ref) "+
   "VALUES(?,?,?,1,?::text[],true,clock_timestamp()+?::interval,clock_timestamp()+?::interval,clock_timestamp()+interval '3 hours',?,'trusted-roster-synthetic') ON CONFLICT(cell_id,facility_id,actor_id) DO UPDATE SET permissions=EXCLUDED.permissions,shift_from=EXCLUDED.shift_from,shift_until=EXCLUDED.shift_until",
   CELL,FAC,actor,"{"+String.join(",",permissions)+"}",shiftFrom,shiftUntil,ADMIN_APPROVER);
 }
 private void techOnShift(){grant(TECH,new String[]{"m23:maintenance"},"-1 hour","1 hour");}
 /** A commissioned, active asset taken offline through the EXISTING transitions. */
 private OperationWorkflow.Item offlineAsset(OperationsStore s,String safetyClass){
  var a=create(s,"ASSET",maker(),data("title","Infusion pump","serial","SN-"+UUID.randomUUID().toString().substring(0,8),"manufacturer","Acme Medical","model","IP-200","safetyClass",safetyClass,"locationRef","ICU-01","inspectionRef","BIO-INSPECT-1"));
  evidence(a.id(),"COMMISSION",1,"biomed_inspection","BIO-INSPECT-1");
  a=next(s,a,"COMMISSION",checker(),Map.of());a=next(s,a,"ACTIVATE",checker(),Map.of());
  return next(s,a,"TAKE_OFFLINE",checker(),Map.of());
 }
 private OperationWorkflow.Item activeAsset(OperationsStore s,String safetyClass){
  var a=create(s,"ASSET",maker(),data("title","Patient monitor","serial","SN-"+UUID.randomUUID().toString().substring(0,8),"safetyClass",safetyClass,"locationRef","ICU-02","inspectionRef","BIO-INSPECT-1"));
  evidence(a.id(),"COMMISSION",1,"biomed_inspection","BIO-INSPECT-1");
  a=next(s,a,"COMMISSION",checker(),Map.of());return next(s,a,"ACTIVATE",checker(),Map.of());
 }
 private OperationWorkflow.Item openWork(OperationsStore s,OperationWorkflow.Item asset){
  var w=create(s,"WORK_ORDER",maker(),data("title","Preventive maintenance","assetRef",asset.id().toString(),"dueAt",Instant.now().plus(Duration.ofDays(1)).toString()));
  return next(s,w,"OPEN_WORK",maker(),Map.of());
 }
 private OperationWorkflow.Item assignedWork(OperationsStore s,OperationWorkflow.Item asset){
  techOnShift();var w=openWork(s,asset);
  return next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString()));
 }
 private OperationWorkflow.Item verifiedWork(OperationsStore s,OperationWorkflow.Item asset){
  var w=assignedWork(s,asset);
  evidence(w.id(),"COMPLETE_WORK",w.version(),"biomed_inspection","WORK-INSPECT-1");
  w=next(s,w,"COMPLETE_WORK",checker(),Map.of("inspectionRef","WORK-INSPECT-1"));
  evidence(w.id(),"VERIFY_WORK",w.version(),"calibration_safe","CAL-WO-1");
  return next(s,w,"VERIFY_WORK",checker(),Map.of("workOrderRef","WO-1"));
 }
 private MaintenanceService.PlanRequest planReq(UUID asset,String type,int interval,Integer cal,LocalDate anchor){
  return new MaintenanceService.PlanRequest(asset,type,interval,cal,"Europe/Paris",anchor.toString(),"09:00","NEXT_WEEKDAY",UUID.randomUUID());
 }
 private UUID approvedPlan(MaintenanceService m,UUID asset,String type,int interval,Integer cal,LocalDate anchor){
  var proposed=m.proposePlan(maker(),planReq(asset,type,interval,cal,anchor));
  UUID plan=UUID.fromString((String)proposed.get("planId"));
  m.decidePlan(checker(),plan,1,new MaintenanceService.Decision(true,UUID.randomUUID()));
  return plan;
 }
 private long count(String sql,Object...args){return admin().queryForObject(sql,Long.class,args);}
 private static void denied(String code,org.junit.jupiter.api.function.Executable e){
  var ex=assertThrows(WorkflowViolation.class,e);assertEquals(code,ex.getMessage());
 }

 // ------------------------------------------------------------------ policy
 @Test void policyIsMakerCheckerVersionedImmutableAndNeverLooserThanTheSafeDefault(){
  ready();var s=store();var m=service(s);
  denied("policy_looser_than_safe_default",()->m.proposePolicy(maker(),new MaintenanceService.PolicyRequest("HIGH",120,180,24,72,UUID.randomUUID())));
  var p=m.proposePolicy(maker(),new MaintenanceService.PolicyRequest("HIGH",60,120,12,48,UUID.randomUUID()));
  assertEquals(1,p.get("version"));
  denied("policy_decision_pending",()->m.proposePolicy(maker(),new MaintenanceService.PolicyRequest("HIGH",45,90,12,48,UUID.randomUUID())));
  denied("maker_checker_denied",()->m.decidePolicy(actor(MAKER,"m23:pm_approve"),"HIGH",1,new MaintenanceService.Decision(true,UUID.randomUUID())));
  denied("capability_denied",()->m.decidePolicy(maker(),"HIGH",1,new MaintenanceService.Decision(true,UUID.randomUUID())));
  m.decidePolicy(checker(),"HIGH",1,new MaintenanceService.Decision(true,UUID.randomUUID()));
  var high=m.policies(maker()).stream().filter(x->"HIGH".equals(x.get("safetyClass"))).findFirst().orElseThrow();
  assertEquals(60,high.get("intervalDays"));assertEquals("APPROVED_FACILITY_POLICY",high.get("source"));
  assertEquals("SAFE_DEFAULT",m.policies(maker()).stream().filter(x->"LOW".equals(x.get("safetyClass"))).findFirst().orElseThrow().get("source"));
  m.proposePolicy(maker(),new MaintenanceService.PolicyRequest("HIGH",45,90,12,48,UUID.randomUUID()));
  m.decidePolicy(checker(),"HIGH",2,new MaintenanceService.Decision(true,UUID.randomUUID()));
  assertEquals("SUPERSEDED",admin().queryForObject("SELECT decision FROM synqoria_operations.pm_policy_versions WHERE cell_id=? AND safety_class='HIGH' AND version=1",String.class,CELL));
  // history is immutable even for the migration owner (trigger), and the runtime role cannot delete.
  assertThrows(Exception.class,()->admin().update("UPDATE synqoria_operations.pm_policy_versions SET interval_days=1 WHERE cell_id=? AND version=2",CELL));
  assertThrows(Exception.class,()->new JdbcTemplate(runtimeDs).update("DELETE FROM synqoria_operations.pm_policy_versions"));
 }

 // ------------------------------------------------------------------ plans, approval and calendar arithmetic
 @Test void planRequiresSafetyClassPolicyBoundsApprovalAndOneLivePlanPerAsset(){
  ready();var s=store();var m=service(s);
  var noClass=create(s,"ASSET",maker(),data("title","Unclassified device","serial","SN-NOCLASS-1"));
  denied("asset_safety_class_required",()->m.proposePlan(maker(),planReq(noClass.id(),"PREVENTIVE",30,null,LocalDate.now())));
  var asset=offlineAsset(s,"CRITICAL");
  denied("interval_exceeds_policy",()->m.proposePlan(maker(),planReq(asset.id(),"PREVENTIVE",31,null,LocalDate.now())));
  denied("invalid_timezone",()->m.proposePlan(maker(),new MaintenanceService.PlanRequest(asset.id(),"PREVENTIVE",30,null,"Mars/Olympus",LocalDate.now().toString(),"09:00","EXACT_DAY",UUID.randomUUID())));
  denied("invalid_calibration_interval",()->m.proposePlan(maker(),planReq(asset.id(),"CALIBRATION",30,null,LocalDate.now())));
  denied("capability_denied",()->m.proposePlan(checker(),planReq(asset.id(),"PREVENTIVE",30,null,LocalDate.now())));
  var proposed=m.proposePlan(maker(),planReq(asset.id(),"PREVENTIVE_AND_CALIBRATION",30,90,LocalDate.now()));
  UUID plan=UUID.fromString((String)proposed.get("planId"));
  denied("plan_exists_use_new_version",()->m.proposePlan(maker(),planReq(asset.id(),"PREVENTIVE",30,null,LocalDate.now())));
  denied("maker_checker_denied",()->m.decidePlan(actor(MAKER,"m23:pm_approve"),plan,1,new MaintenanceService.Decision(true,UUID.randomUUID())));
  m.decidePlan(checker(),plan,1,new MaintenanceService.Decision(true,UUID.randomUUID()));
  denied("decision_already_made",()->m.decidePlan(checker(),plan,1,new MaintenanceService.Decision(true,UUID.randomUUID())));
  // DB-level maker-checker: even a direct write by the migration owner cannot self-approve.
  assertThrows(Exception.class,()->admin().update("UPDATE synqoria_operations.pm_plan_versions SET decision='SUPERSEDED',decided_by=proposed_by WHERE plan_id=?",plan));
  assertEquals(1,m.plans(maker(),10).size());
  assertEquals(2L,count("SELECT count(*) FROM synqoria_operations.audit_events WHERE cell_id=? AND action LIKE 'PM_PLAN_%'",CELL));
 }
 @Test void replayedCommandReturnsTheStoredResultAndADifferentRequestWithTheSameKeyCollides(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  var req=planReq(asset.id(),"PREVENTIVE",90,null,LocalDate.now());
  var first=m.proposePlan(maker(),req);
  assertEquals(first,m.proposePlan(maker(),req));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_operations.pm_plans WHERE cell_id=?",CELL));
  denied("idempotency_collision",()->m.proposePlan(maker(),new MaintenanceService.PlanRequest(asset.id(),"PREVENTIVE",60,null,"Europe/Paris",LocalDate.now().toString(),"09:00","NEXT_WEEKDAY",req.commandId())));
  denied("idempotency_key_required",()->m.proposePlan(maker(),new MaintenanceService.PlanRequest(asset.id(),"PREVENTIVE",60,null,"Europe/Paris",LocalDate.now().toString(),"09:00","NEXT_WEEKDAY",null)));
 }
 @Test void newApprovedVersionSupersedesTheOldOneAndWithdrawsOnlyUnlinkedFutureOccurrences(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  UUID plan=approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(5));
  m.runPlanner(planner(),UUID.randomUUID(),60);
  long v1=count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND plan_version=1 AND state='PLANNED'",CELL);
  assertTrue(v1>=2);
  var proposed=m.proposeVersion(maker(),plan,planReq(asset.id(),"PREVENTIVE",45,null,LocalDate.now().minusDays(5)));
  assertEquals(2,proposed.get("version"));
  denied("plan_decision_pending",()->m.proposeVersion(maker(),plan,planReq(asset.id(),"PREVENTIVE",45,null,LocalDate.now())));
  m.decidePlan(checker(),plan,2,new MaintenanceService.Decision(true,UUID.randomUUID()));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND plan_version=1 AND state='PLANNED'",CELL));
  assertEquals(v1,count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND plan_version=1 AND state='CANCELLED'",CELL));
  assertEquals("SUPERSEDED",admin().queryForObject("SELECT decision FROM synqoria_operations.pm_plan_versions WHERE plan_id=? AND version=1",String.class,plan));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_operations.pm_plan_versions WHERE plan_id=? AND decision='APPROVED'",plan));
 }

 // ------------------------------------------------------------------ generation, lease, restart/replay, concurrency
 @Test void generationIsDeterministicIdempotentAndBoundedByTheHorizon(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  UUID plan=approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(10));
  var cmd=UUID.randomUUID();
  var first=m.runPlanner(planner(),cmd,60);
  assertTrue(((Number)first.get("generated")).intValue()>=2);
  var replay=m.runPlanner(planner(),cmd,60);
  for(String k:List.of("generated","plans","fence"))assertEquals(((Number)first.get(k)).longValue(),((Number)replay.get(k)).longValue(),"same command id replays the stored result: "+k);
  var again=m.runPlanner(planner(),UUID.randomUUID(),60);
  assertEquals(0,again.get("generated"),"regeneration never double-schedules");
  List<UUID> ids=admin().queryForList("SELECT occurrence_id FROM synqoria_operations.pm_occurrences WHERE cell_id=? ORDER BY seq",UUID.class,CELL);
  List<Integer> seqs=admin().queryForList("SELECT seq FROM synqoria_operations.pm_occurrences WHERE cell_id=? ORDER BY seq",Integer.class,CELL);
  for(int i=0;i<ids.size();i++)assertEquals(MaintenanceRules.occurrenceId(plan,1,seqs.get(i)),ids.get(i));
  assertEquals(ids.size(),new HashSet<>(ids).size());
  Instant horizon=Instant.now().plus(Duration.ofDays(61));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND due_at>?",CELL,Timestamp.from(horizon)));
  denied("invalid_horizon",()->m.runPlanner(planner(),UUID.randomUUID(),400));
  denied("capability_denied",()->m.runPlanner(maker(),UUID.randomUUID(),30));
  var run=admin().queryForMap("SELECT last_error_code,last_generated,holder_id FROM synqoria_operations.pm_planner_state WHERE cell_id=?",CELL);
  assertNull(run.get("holder_id"),"lease released after the run");
 }
 @Test void aLiveLeaseExcludesOtherPlannersAndAStaleFenceCannotWrite(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(10));
  UUID other=UUID.randomUUID();
  admin().update("INSERT INTO synqoria_operations.pm_planner_state(cell_id,facility_id,holder_id,fence,lease_expires_at) VALUES(?,?,?,7,clock_timestamp()+interval '10 minutes')",CELL,FAC,other);
  denied("planner_lease_held",()->m.runPlanner(planner(),UUID.randomUUID(),30));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=?",CELL));
  denied("planner_lease_lost",()->m.runWithFence(planner(),7,UUID.randomUUID(),30,false));
  admin().update("UPDATE synqoria_operations.pm_planner_state SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE cell_id=?",CELL);
  var ok=m.runPlanner(planner(),UUID.randomUUID(),30);
  assertEquals(8L,((Number)ok.get("fence")).longValue(),"takeover increments the fence");
  // the previous holder (fence 7) resumes after takeover: rejected, nothing written under the stale fence
  admin().update("UPDATE synqoria_operations.pm_planner_state SET holder_id=?,fence=9,lease_expires_at=clock_timestamp()+interval '5 minutes' WHERE cell_id=?",PLANNER,CELL);
  denied("planner_lease_lost",()->m.runWithFence(planner(),8,UUID.randomUUID(),30,false));
 }
 @Test void concurrentPlannersNeverDuplicateOccurrencesAndFailuresAreOnlyLeaseDenials() throws Exception{
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(10));
  ExecutorService pool=Executors.newFixedThreadPool(8);
  List<Future<String>> results=new ArrayList<>();
  CountDownLatch go=new CountDownLatch(1);
  for(int i=0;i<8;i++)results.add(pool.submit(()->{go.await();try{m.runPlanner(planner(),UUID.randomUUID(),60);return "ok";}catch(WorkflowViolation e){return e.getMessage();}}));
  go.countDown();
  Set<String> outcomes=new HashSet<>();for(var f:results)outcomes.add(f.get(60,TimeUnit.SECONDS));pool.shutdown();
  assertTrue(outcomes.contains("ok"));
  assertTrue(Set.of("ok","planner_lease_held").containsAll(outcomes),"unexpected: "+outcomes);
  long total=count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=?",CELL);
  assertEquals(total,count("SELECT count(DISTINCT occurrence_id) FROM synqoria_operations.pm_occurrences WHERE cell_id=?",CELL));
  assertEquals(total,count("SELECT count(DISTINCT (plan_id,plan_version,seq)) FROM synqoria_operations.pm_occurrences WHERE cell_id=?",CELL));
 }
 @Test void plannerHealthIsUnknownThenHealthyThenStaleAndNeverFalselyHealthy(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  assertEquals("UNKNOWN",m.health(planner(),120).get("status"));
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(80));
  m.runPlanner(planner(),UUID.randomUUID(),30);
  var h=m.health(planner(),120);
  assertEquals("DEGRADED",h.get("status"),"overdue occurrences exist that have not been escalated");
  assertTrue(((Number)h.get("overdueUnescalated")).longValue()>0);
  m.escalateOverdue(planner(),UUID.randomUUID());
  assertEquals("HEALTHY",m.health(planner(),120).get("status"));
  admin().update("UPDATE synqoria_operations.pm_planner_state SET last_success_at=clock_timestamp()-interval '5 hours' WHERE cell_id=?",CELL);
  assertEquals("STALE",m.health(planner(),120).get("status"));
  denied("invalid_threshold",()->m.health(planner(),0));
 }

 // ------------------------------------------------------------------ escalation (M37 contract through outbox only)
 @Test void overdueEscalationIsLeveledIdempotentAndEmitsOnlyOutboxContractEvents(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(40));
  m.runPlanner(planner(),UUID.randomUUID(),30);
  var r1=m.escalateOverdue(planner(),UUID.randomUUID());
  int raised=((Number)r1.get("escalationsRaised")).intValue();
  assertTrue(raised>=3,"a long-overdue HIGH occurrence reaches levels 1..3");
  assertEquals(0,((Number)m.escalateOverdue(planner(),UUID.randomUUID()).get("escalationsRaised")).intValue(),"each (occurrence, level) is raised once");
  assertEquals((long)raised,count("SELECT count(*) FROM synqoria_operations.outbox WHERE cell_id=? AND event_type='M23_PM_OVERDUE_ESCALATION'",CELL));
  String payload=admin().queryForObject("SELECT payload::text FROM synqoria_operations.outbox WHERE cell_id=? AND event_type='M23_PM_OVERDUE_ESCALATION' LIMIT 1",String.class,CELL);
  assertTrue(payload.contains("occurrenceId")&&payload.contains("level")&&!payload.contains("recipient"),"contract carries ids and level, never a recipient or message body");
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.notification_jobs WHERE cell_id=?",CELL),"no second notification sender is created");
 }

 // ------------------------------------------------------------------ linking to the EXISTING work-order state machine
 @Test void occurrenceLinksOnlyToAMatchingOpenWorkOrderOfAnOfflineAssetAndOnlyOnePerAsset(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(10));
  m.runPlanner(planner(),UUID.randomUUID(),60);
  List<UUID> occ=admin().queryForList("SELECT occurrence_id FROM synqoria_operations.pm_occurrences WHERE cell_id=? ORDER BY seq",UUID.class,CELL);
  assertTrue(occ.size()>=2);
  var other=offlineAsset(s,"LOW");var otherWork=openWork(s,other);
  denied("work_order_asset_mismatch",()->m.linkWorkOrder(maker(),occ.get(0),new MaintenanceService.LinkRequest(otherWork.id(),UUID.randomUUID())));
  var draft=create(s,"WORK_ORDER",maker(),data("title","Draft work","assetRef",asset.id().toString(),"dueAt",Instant.now().toString()));
  denied("work_order_not_open",()->m.linkWorkOrder(maker(),occ.get(0),new MaintenanceService.LinkRequest(draft.id(),UUID.randomUUID())));
  var w1=openWork(s,asset);var w2=openWork(s,asset);
  m.linkWorkOrder(maker(),occ.get(0),new MaintenanceService.LinkRequest(w1.id(),UUID.randomUUID()));
  denied("asset_already_under_service",()->m.linkWorkOrder(maker(),occ.get(1),new MaintenanceService.LinkRequest(w2.id(),UUID.randomUUID())));
  denied("occurrence_not_plannable",()->m.linkWorkOrder(maker(),occ.get(0),new MaintenanceService.LinkRequest(w2.id(),UUID.randomUUID())));
  denied("not_found",()->m.linkWorkOrder(maker(),UUID.randomUUID(),new MaintenanceService.LinkRequest(w2.id(),UUID.randomUUID())));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_operations.pm_occurrences WHERE cell_id=? AND state='WORK_LINKED'",CELL));
  // the database refuses linking to an asset that is not offline, even when the service is bypassed
  var active=activeAsset(s,"LOW");
  var ghost=UUID.randomUUID();
  assertThrows(Exception.class,()->admin().update("INSERT INTO synqoria_operations.pm_occurrences(occurrence_id,cell_id,facility_id,plan_id,plan_version,seq,asset_ref,kind,due_at,state,work_order_ref,generated_fence) "+
   "SELECT ?,cell_id,facility_id,plan_id,plan_version,999,?,kind,due_at,'WORK_LINKED',?,1 FROM synqoria_operations.pm_occurrences WHERE cell_id=? LIMIT 1",ghost,active.id(),w1.id(),CELL));
 }

 // ------------------------------------------------------------------ technician and independent inspection
 @Test void technicianMustBeALiveWorkforceMemberOnShiftWithTheMaintenanceGrant(){
  ready();var s=store();var asset=offlineAsset(s,"HIGH");
  var w=openWork(s,asset);
  denied("technician_must_be_workforce_member",()->next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef","biomed-42")));
  denied("technician_not_on_verified_shift",()->next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString())));
  grant(TECH,new String[]{"m23:maintenance"},"-3 hours","-1 hour");
  denied("technician_not_on_verified_shift",()->next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString())));
  grant(TECH,new String[]{"m25:dispatch"},"-1 hour","1 hour");
  denied("technician_not_on_verified_shift",()->next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString())));
  techOnShift();
  var assigned=next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString()));
  assertEquals("WORK_ASSIGNED",assigned.status());
  assertEquals(TECH.toString(),admin().queryForObject("SELECT technician_ref FROM synqoria_operations.maintenance_orders WHERE item_id=?",String.class,w.id()));
 }
 @Test void repairCannotContinueOnceTheAssetIsNoLongerOffline(){
  ready();var s=store();var asset=offlineAsset(s,"HIGH");techOnShift();
  var w=openWork(s,asset);
  admin().update("UPDATE synqoria_operations.items SET status='RETIRED' WHERE id=?",asset.id());
  denied("asset_not_offline_for_work",()->next(s,w,"ASSIGN_WORK",checker(),Map.of("technicianRef",TECH.toString())));
 }
 @Test void theVerifierMustBeIndependentOfTheTechnicianAndOfTheWorkOrderMaker(){
  ready();var s=store();var asset=offlineAsset(s,"HIGH");
  var w=assignedWork(s,asset);
  evidence(w.id(),"COMPLETE_WORK",w.version(),"biomed_inspection","WORK-INSPECT-1");
  w=next(s,w,"COMPLETE_WORK",checker(),Map.of("inspectionRef","WORK-INSPECT-1"));
  evidence(w.id(),"VERIFY_WORK",w.version(),"calibration_safe","CAL-WO-1");
  var tech=actor(TECH,"m23:return","m23:maintenance");
  var item=w;
  denied("verifier_must_be_independent_of_technician",()->next(s,item,"VERIFY_WORK",tech,Map.of("workOrderRef","WO-1")));
  denied("maker_checker_denied",()->next(s,item,"VERIFY_WORK",actor(MAKER,"m23:return"),Map.of("workOrderRef","WO-1")));
  assertEquals("WORK_VERIFIED",next(s,item,"VERIFY_WORK",checker(),Map.of("workOrderRef","WO-1")).status());
 }
 @Test void inspectionQueueListsOnlyWorkTheCallerMayIndependentlyVerifyAndFlagsMissingEvidence(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  var w=assignedWork(s,asset);
  evidence(w.id(),"COMPLETE_WORK",w.version(),"biomed_inspection","WORK-INSPECT-1");
  w=next(s,w,"COMPLETE_WORK",checker(),Map.of("inspectionRef","WORK-INSPECT-1"));
  var reviewer=actor(UUID.randomUUID(),"m23:return");
  var q=m.inspectionQueue(reviewer,10);
  assertEquals(1,q.size());assertEquals(false,q.get(0).get("evidenceReady"),"no verification evidence yet: fail closed");
  assertEquals(0,m.inspectionQueue(checker(),10).size(),"the person who completed the work is not an independent reviewer");
  assertEquals(0,m.inspectionQueue(actor(MAKER,"m23:return"),10).size(),"the work order's maker is excluded");
  assertEquals(0,m.inspectionQueue(actor(TECH,"m23:return"),10).size(),"the technician is excluded");
  evidence(w.id(),"VERIFY_WORK",w.version(),"calibration_safe","CAL-WO-1");
  assertEquals(true,m.inspectionQueue(reviewer,10).get(0).get("evidenceReady"));
  evidence(w.id(),"VERIFY_WORK",w.version(),"calibration_safe","CAL-EXPIRED",ADMIN_APPROVER,"-2 hours","-1 hour");
  denied("capability_denied",()->m.inspectionQueue(maker(),10));
 }

 // ------------------------------------------------------------------ calibration and fail-closed return to service
 @Test void calibrationRequiresIndependentUnexpiredUnusedEvidenceWithinThePolicyInterval(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  Instant performed=Instant.now().minusSeconds(60),next=performed.plus(Duration.ofDays(90));
  var req=new MaintenanceService.CalibrationRequest(performed.toString(),next.toString(),"CAL-CERT-1",UUID.randomUUID());
  denied("trusted_evidence_required",()->m.recordCalibration(maker(),asset.id(),req));
  evidence(asset.id(),"RECORD_CALIBRATION",asset.version(),"calibration_safe","CAL-CERT-1",MAKER,"-1 minute","1 hour");
  denied("trusted_evidence_required",()->m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),next.toString(),"CAL-CERT-1",UUID.randomUUID())));
  evidence(asset.id(),"RECORD_CALIBRATION",asset.version(),"calibration_safe","CAL-OLD",ADMIN_APPROVER,"-3 hours","-1 hour");
  denied("trusted_evidence_required",()->m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),next.toString(),"CAL-OLD",UUID.randomUUID())));
  evidence(asset.id(),"RECORD_CALIBRATION",asset.version(),"calibration_safe","CAL-CERT-2");
  denied("calibration_interval_exceeds_policy",()->m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),performed.plus(Duration.ofDays(400)).toString(),"CAL-CERT-2",UUID.randomUUID())));
  denied("invalid_calibration_time",()->m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(next.toString(),performed.toString(),"CAL-CERT-2",UUID.randomUUID())));
  var ok=m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),next.toString(),"CAL-CERT-2",UUID.randomUUID()));
  assertNotNull(ok.get("calibrationId"));
  denied("evidence_already_used",()->m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),next.toString(),"CAL-CERT-2",UUID.randomUUID())));
  assertThrows(Exception.class,()->new JdbcTemplate(runtimeDs).update("UPDATE synqoria_operations.pm_calibrations SET next_due_at=next_due_at+interval '1 year'"));
 }
 @Test void aCalibrationBearingPlanBlocksReturnToServiceUntilAValidCalibrationExists(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE_AND_CALIBRATION",90,180,LocalDate.now().plusDays(30));
  var work=verifiedWork(s,asset);
  evidence(asset.id(),"RETURN_TO_SERVICE",asset.version(),"biomed_inspection","BIO-INSPECT-1");
  denied("calibration_expired_or_missing",()->next(s,asset,"RETURN_TO_SERVICE",checker(),Map.of("workOrderRef",work.id().toString())));
  evidence(asset.id(),"RECORD_CALIBRATION",asset.version(),"calibration_safe","CAL-CERT-9");
  Instant performed=Instant.now().minusSeconds(30);
  m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(performed.toString(),performed.plus(Duration.ofDays(180)).toString(),"CAL-CERT-9",UUID.randomUUID()));
  assertEquals("ACTIVE",next(s,asset,"RETURN_TO_SERVICE",checker(),Map.of("workOrderRef",work.id().toString())).status());
 }
 @Test void expiredOrMissingInspectionEvidenceStillBlocksReturnToService(){
  ready();var s=store();var asset=offlineAsset(s,"MEDIUM");var work=verifiedWork(s,asset);
  denied("trusted_evidence_required",()->next(s,asset,"RETURN_TO_SERVICE",checker(),Map.of("workOrderRef",work.id().toString())));
  evidence(asset.id(),"RETURN_TO_SERVICE",asset.version(),"biomed_inspection","BIO-INSPECT-1",ADMIN_APPROVER,"-3 hours","-1 hour");
  denied("trusted_evidence_required",()->next(s,asset,"RETURN_TO_SERVICE",checker(),Map.of("workOrderRef",work.id().toString())));
  admin().update("UPDATE synqoria_operations.external_evidence SET valid_until=clock_timestamp()+interval '1 hour' WHERE item_id=? AND for_action='RETURN_TO_SERVICE'",asset.id());
  assertEquals("ACTIVE",next(s,asset,"RETURN_TO_SERVICE",checker(),Map.of("workOrderRef",work.id().toString())).status());
 }
 @Test void completingAnOccurrenceNeedsTheVerifiedWorkOrderCalibrationEvidenceAndAnIndependentCompleter(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE_AND_CALIBRATION",90,180,LocalDate.now().minusDays(1));
  m.runPlanner(planner(),UUID.randomUUID(),30);
  UUID occ=admin().queryForObject("SELECT occurrence_id FROM synqoria_operations.pm_occurrences WHERE cell_id=? ORDER BY due_at LIMIT 1",UUID.class,CELL);
  var w=assignedWork(s,asset);
  m.linkWorkOrder(maker(),occ,new MaintenanceService.LinkRequest(w.id(),UUID.randomUUID()));
  denied("completion_must_be_independent",()->m.completeOccurrence(actor(MAKER,"m23:pm_complete"),occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())));
  denied("completion_must_be_independent",()->m.completeOccurrence(actor(TECH,"m23:pm_complete"),occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())));
  denied("capability_denied",()->m.completeOccurrence(maker(),occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())));
  var completer=actor(UUID.randomUUID(),"m23:pm_complete");
  denied("work_order_not_verified",()->m.completeOccurrence(completer,occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())));
  evidence(w.id(),"COMPLETE_WORK",w.version(),"biomed_inspection","WORK-INSPECT-1");
  w=next(s,w,"COMPLETE_WORK",checker(),Map.of("inspectionRef","WORK-INSPECT-1"));
  evidence(w.id(),"VERIFY_WORK",w.version(),"calibration_safe","CAL-WO-1");
  next(s,w,"VERIFY_WORK",checker(),Map.of("workOrderRef","WO-1"));
  denied("calibration_evidence_required",()->m.completeOccurrence(completer,occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())));
  evidence(asset.id(),"RECORD_CALIBRATION",asset.version(),"calibration_safe","CAL-CERT-5");
  Instant p=Instant.now().plusSeconds(2);
  m.recordCalibration(maker(),asset.id(),new MaintenanceService.CalibrationRequest(p.toString(),p.plus(Duration.ofDays(180)).toString(),"CAL-CERT-5",UUID.randomUUID()));
  assertEquals("COMPLETED",m.completeOccurrence(completer,occ,new MaintenanceService.CompleteRequest(UUID.randomUUID())).get("state"));
  assertEquals("MAINTENANCE",admin().queryForObject("SELECT status FROM synqoria_operations.items WHERE id=?",String.class,asset.id()),"completion never returns the asset to service");
 }

 // ------------------------------------------------------------------ external references, costs
 @Test void externalServiceReferencesAreUnattestedAndNeverSatisfyATrustedEvidenceGate(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");var w=assignedWork(s,asset);
  var r=m.addServiceReference(maker(),w.id(),new MaintenanceService.ServiceReferenceRequest("ENGINEER_CERTIFICATE","ENG-CERT-77","Independent Biomedical Engineers Ltd",UUID.randomUUID()));
  assertEquals(false,r.get("attested"));
  denied("reference_already_recorded",()->m.addServiceReference(maker(),w.id(),new MaintenanceService.ServiceReferenceRequest("ENGINEER_CERTIFICATE","ENG-CERT-77","Independent Biomedical Engineers Ltd",UUID.randomUUID())));
  denied("invalid_reference_kind",()->m.addServiceReference(maker(),w.id(),new MaintenanceService.ServiceReferenceRequest("SELF_ATTESTATION","X-1","me",UUID.randomUUID())));
  denied("capability_denied",()->m.addServiceReference(checker(),w.id(),new MaintenanceService.ServiceReferenceRequest("SUPPLIER_REPORT","SR-1","Supplier",UUID.randomUUID())));
  var item=w;
  denied("trusted_evidence_required",()->next(s,item,"COMPLETE_WORK",checker(),Map.of("inspectionRef","ENG-CERT-77")));
  assertThrows(Exception.class,()->admin().update("UPDATE synqoria_operations.pm_service_references SET attested=true WHERE cell_id=?",CELL));
 }
 @Test void repairCostsAreAdministrativeReferencesWithoutLedgerPosting(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");var w=openWork(s,asset);
  var c=m.addCostReference(maker(),w.id(),new MaintenanceService.CostRequest(125000L,"XAF","BIOMED-CC-01","PO-REQ-9",UUID.randomUUID()));
  assertEquals(false,c.get("posted"));
  denied("invalid_amount",()->m.addCostReference(maker(),w.id(),new MaintenanceService.CostRequest(-1L,"XAF","BIOMED-CC-01",null,UUID.randomUUID())));
  denied("invalid_currency",()->m.addCostReference(maker(),w.id(),new MaintenanceService.CostRequest(5L,"xaf","BIOMED-CC-01",null,UUID.randomUUID())));
  denied("cost_already_recorded",()->m.addCostReference(maker(),w.id(),new MaintenanceService.CostRequest(125000L,"XAF","BIOMED-CC-01","PO-REQ-9",UUID.randomUUID())));
  denied("not_found",()->m.addCostReference(maker(),UUID.randomUUID(),new MaintenanceService.CostRequest(5L,"XAF","BIOMED-CC-01",null,UUID.randomUUID())));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.procurement_export_queue WHERE cell_id=?",CELL),"no finance export or ledger entry is created");
 }

 // ------------------------------------------------------------------ read models, privacy, isolation, projections
 @Test void calendarIsBoundedProjectsStatesAndFailsClosedForUnsafeAssets(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(3));
  m.runPlanner(planner(),UUID.randomUUID(),60);
  Instant from=Instant.now().minus(Duration.ofDays(60)),to=Instant.now().plus(Duration.ofDays(90));
  var cal=m.calendar(maker(),from,to,100);
  assertFalse(cal.isEmpty());
  assertTrue(cal.stream().allMatch(x->Set.of("SCHEDULED","DUE","OVERDUE","IN_SERVICE").contains(x.get("projection"))));
  assertTrue(cal.stream().noneMatch(x->x.containsKey("technicianRef")||x.containsKey("createdBy")),"privacy-safe field selection");
  denied("invalid_range",()->m.calendar(maker(),from,from.plus(Duration.ofDays(400)),10));
  denied("invalid_range",()->m.calendar(maker(),to,from,10));
  denied("invalid_limit",()->m.calendar(maker(),from,to,500));
  denied("capability_denied",()->m.calendar(actor(UUID.randomUUID(),"m21:read"),from,to,10));
  assertTrue(m.calendar(maker(),from,to,1).size()<=1);
  admin().update("UPDATE synqoria_operations.items SET status='RETIRED' WHERE id=?",asset.id());
  assertTrue(m.calendar(maker(),from,to,100).stream().allMatch(x->"BLOCKED".equals(x.get("projection"))||"DONE".equals(x.get("projection"))||"CANCELLED".equals(x.get("projection"))),"a retired asset is never shown as schedulable");
  denied("asset_retired",()->m.proposePlan(maker(),planReq(asset.id(),"PREVENTIVE",30,null,LocalDate.now())));
  assertEquals(0,((Number)m.runPlanner(planner(),UUID.randomUUID(),60).get("generated")).intValue(),"no new occurrences for a retired asset");
 }
 @Test void assetHistoryHidesStaffIdentitiesUnlessAuditReadIsGranted(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now());
  var plain=m.history(maker(),asset.id(),50);
  assertEquals(false,plain.get("identitiesIncluded"));
  @SuppressWarnings("unchecked") var events=(List<Map<String,Object>>)plain.get("events");
  assertFalse(events.isEmpty());assertTrue(events.stream().noneMatch(e->e.containsKey("actorId")));
  var full=m.history(checker(),asset.id(),50);
  assertEquals(true,full.get("identitiesIncluded"));
  @SuppressWarnings("unchecked") var fullEvents=(List<Map<String,Object>>)full.get("events");
  assertTrue(fullEvents.stream().allMatch(e->e.containsKey("actorId")));
  denied("invalid_limit",()->m.history(maker(),asset.id(),0));
 }
 @Test void everyMaintenanceMutationIsAuditedWithAnOutboxRowInTheSameTransaction(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  long before=count("SELECT count(*) FROM synqoria_operations.audit_events WHERE cell_id=?",CELL);
  long beforeBox=count("SELECT count(*) FROM synqoria_operations.outbox WHERE cell_id=?",CELL);
  approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now());
  assertEquals(2L,count("SELECT count(*) FROM synqoria_operations.audit_events WHERE cell_id=?",CELL)-before);
  assertEquals(2L,count("SELECT count(*) FROM synqoria_operations.outbox WHERE cell_id=?",CELL)-beforeBox);
  // a rejected command leaves neither a plan, an audit event nor a half-written idempotency record
  long auditNow=count("SELECT count(*) FROM synqoria_operations.audit_events WHERE cell_id=?",CELL);
  UUID key=UUID.randomUUID();
  assertThrows(WorkflowViolation.class,()->m.proposePlan(maker(),new MaintenanceService.PlanRequest(asset.id(),"PREVENTIVE",9999,null,"Europe/Paris",LocalDate.now().toString(),"09:00","EXACT_DAY",key)));
  assertEquals(auditNow,count("SELECT count(*) FROM synqoria_operations.audit_events WHERE cell_id=?",CELL));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_operations.pm_commands WHERE cell_id=? AND command_id=?",CELL,key));
 }
 @Test void anotherCellCannotSeeOrActOnMaintenanceDataAndUnscopedSessionsSeeNothing(){
  ready();var s=store();var m=service(s);var asset=offlineAsset(s,"HIGH");
  UUID plan=approvedPlan(m,asset.id(),"PREVENTIVE",30,null,LocalDate.now().minusDays(5));
  m.runPlanner(planner(),UUID.randomUUID(),60);
  var foreign=new OperationWorkflow.Actor(UUID.randomUUID(),UUID.randomUUID(),FAC,Set.of("m23:read","m23:pm_approve","m23:pm_link","m23:pm_propose","m23:pm_plan_run"));
  assertTrue(m.plans(foreign,10).isEmpty());
  assertTrue(m.calendar(foreign,Instant.now().minus(Duration.ofDays(60)),Instant.now().plus(Duration.ofDays(60)),50).isEmpty());
  denied("not_found",()->m.decidePlan(foreign,plan,1,new MaintenanceService.Decision(true,UUID.randomUUID())));
  denied("not_found",()->m.history(foreign,asset.id(),10));
  denied("not_found",()->m.proposePlan(foreign,planReq(asset.id(),"PREVENTIVE",30,null,LocalDate.now())));
  var otherFacility=new OperationWorkflow.Actor(UUID.randomUUID(),CELL,UUID.randomUUID(),Set.of("m23:read"));
  assertTrue(m.plans(otherFacility,10).isEmpty());
  var raw=new JdbcTemplate(runtimeDs);
  for(String table:List.of("pm_policy_versions","pm_plans","pm_plan_versions","pm_occurrences","pm_calibrations","pm_service_references","pm_cost_references","pm_escalations","pm_planner_state","pm_commands"))
   assertEquals(0L,raw.queryForObject("SELECT count(*) FROM synqoria_operations."+table,Long.class),table+" must be invisible without a cell/facility scope");
  assertEquals(10L,count("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='synqoria_operations' AND c.relname LIKE 'pm\\_%' AND c.relrowsecurity AND c.relforcerowsecurity"));
 }
 @Test void assetMetadataIsValidatedWithoutCollectingUnnecessaryData(){
  ready();var s=store();
  denied("invalid_asset_serial",()->create(s,"ASSET",maker(),data("title","Bad serial","serial","<script>alert(1)</script>")));
  denied("invalid_asset_manufacturer",()->create(s,"ASSET",maker(),data("title","Bad maker","serial","SN-12345","manufacturer","Dr John Smith's patient 4455 <b>")));
  denied("invalid_asset_safety_class",()->create(s,"ASSET",maker(),data("title","Bad class","serial","SN-12345","safetyClass","EXTREME")));
  assertEquals("REGISTERED",create(s,"ASSET",maker(),data("title","Good asset","serial","SN-12345","manufacturer","Acme Medical","model","M-1","safetyClass","LOW")).status());
 }
}
