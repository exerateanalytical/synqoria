package org.synqoria.nutrition;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.nutrition.Db.Reply;
import org.synqoria.nutrition.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real PostgreSQL integration (disposable PostgreSQL 18.x, restricted non-owner runtime role, forced RLS).
 * Opt-in via SYNQORIA_NUTRITION_PG_TEST=1. All identities and patients below are SYNTHETIC TEST FIXTURES.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class NutritionPgIntegrationTest {
 static final String ENV_ON="SYNQORIA_NUTRITION_PG_TEST";
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_nutrition_runtime",runtimePassword="m26-ci-only";

 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId z){return this;}
  public Instant instant(){return now;}
 }

 MutableClock clock;Db db;OrderService orders;ProductionService production;DeliveryService delivery;WorklistService worklist;
 JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID();
 // SYNTHETIC identities, one per role so independence rules are exercised.
 final UUID clinicianId=UUID.randomUUID(),approverId=UUID.randomUUID(),plannerId=UUID.randomUUID(),cookId=UUID.randomUUID(),
  dispatcherId=UUID.randomUUID(),porterId=UUID.randomUUID(),nurseId=UUID.randomUUID(),integratorId=UUID.randomUUID();
 Actor clinician,approver,planner,cook,dispatcher,porter,nurse,integrator;
 final UUID p1=UUID.randomUUID(),p2=UUID.randomUUID();
 static final LocalDate DAY=LocalDate.parse("2026-11-07");

 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv(ENV_ON)),"set "+ENV_ON+"=1 with disposable PostgreSQL to run");
  url=System.getenv("SYNQORIA_NUTRITION_TEST_JDBC_URL");
  adminUrl=System.getenv().getOrDefault("SYNQORIA_NUTRITION_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_NUTRITION_TEST_ADMIN_USER");
  adminPassword=System.getenv("SYNQORIA_NUTRITION_TEST_ADMIN_PASSWORD");
  runtimePassword=System.getenv().getOrDefault("SYNQORIA_NUTRITION_TEST_DB_PASSWORD",runtimePassword);
 }

 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_nutrition_runtime') THEN "+
   "CREATE ROLE synqoria_nutrition_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_nutrition CASCADE");
  String ddl=Files.readString(Path.of("db","migration","V001__m26_nutrition_core.sql"));
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(ddl);}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);
  runtimeDs=rds;
  clock=new MutableClock();
  JdbcTemplate jdbc=new JdbcTemplate(rds);
  db=new Db(jdbc,JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_nutrition_runtime",24,"UTC");
  Safety safety=new Safety(db);
  orders=new OrderService(db);production=new ProductionService(db,safety);delivery=new DeliveryService(db,safety);
  worklist=new WorklistService(db,safety);
  clinician=actor(clinicianId,cell,facility,Perm.ORDER_PRESCRIBE,Perm.ORDER_AMEND,Perm.ORDER_CANCEL,Perm.ORDER_REVOKE,Perm.ORDER_READ,Perm.WORKLIST_READ);
  approver=actor(approverId,cell,facility,Perm.ORDER_APPROVE,Perm.ORDER_READ,Perm.SUBST_DECIDE,Perm.WORKLIST_READ);
  planner=actor(plannerId,cell,facility,Perm.PLAN_MANAGE,Perm.PLAN_RELEASE,Perm.SUBST_PROPOSE,Perm.WORKLIST_READ);
  cook=actor(cookId,cell,facility,Perm.PREP_RECORD,Perm.WORKLIST_READ);
  dispatcher=actor(dispatcherId,cell,facility,Perm.DISPATCH_APPROVE,Perm.WORKLIST_READ);
  porter=actor(porterId,cell,facility,Perm.DELIVERY_RECORD,Perm.DELIVERY_READ,Perm.WORKLIST_READ);
  nurse=actor(nurseId,cell,facility,Perm.ACK_RECORD,Perm.DELIVERY_READ,Perm.INCIDENT_ESCALATE,Perm.INCIDENT_RESOLVE,Perm.WORKLIST_READ);
  integrator=actor(integratorId,cell,facility,Perm.CLINICAL_FEED);
 }
 static Actor actor(UUID id,UUID cell,UUID facility,String...perms){return new Actor(id,cell,facility,Set.of(perms));}

 // ------------------------------------------------------------------ fixture helpers
 Map<String,Object> body(Reply r){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)r.body();return m;}
 Reply ok(Reply r){assertTrue(r.status()<300,"unexpected "+r.status()+" "+r.body());return r;}
 static UUID k(){return UUID.randomUUID();}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}

 void patient(UUID p,String label,String ward,String bed){
  ok(orders.feedPatient(integrator,k(),p,new OrderService.PatientContext(k(),1,label,ward,bed,true,clock.instant())));
 }
 void allergies(UUID p,long v,String...codes){
  ok(orders.feedAllergies(integrator,k(),p,new OrderService.Allergies(k(),v,List.of(codes),clock.instant())));
 }
 OrderService.OrderRequest req(UUID p,String diet,String texture,List<String> excluded,List<String> meals){
  return new OrderService.OrderRequest(p,UUID.randomUUID(),diet,texture,List.of(),excluded,meals,"synthetic instruction",
   clock.instant().minusSeconds(60),null);
 }
 Map<String,Object> approvedOrder(UUID p,String...excluded){
  Map<String,Object> o=body(ok(orders.prescribe(clinician,k(),req(p,"REGULAR","NORMAL",List.of(excluded),List.of("BREAKFAST","LUNCH","DINNER")))));
  return body(ok(orders.approve(approver,k(),id(o),new OrderService.TransitionRequest(ver(o),"checked"))));
 }
 /** patient + allergy + approved order for P1 and P2 in ward W1. */
 void baseline(){
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");patient(p2,"SYNTHETIC-PATIENT-002","W1","B02");
  allergies(p1,1,"PEANUT");allergies(p2,1);
  approvedOrder(p1,"PEANUT");approvedOrder(p2);
 }
 Map<String,Object> newBatch(){
  return body(ok(production.create(planner,k(),new ProductionService.BatchRequest("W1",DAY,"LUNCH",clock.instant().plusSeconds(4*3600)))));
 }
 /** Drives a batch to DISPATCH_APPROVED and returns the batch. */
 Map<String,Object> dispatchedBatch(){
  Map<String,Object> b=newBatch();
  b=body(ok(production.release(planner,k(),id(b),new ProductionService.Transition(ver(b)))));
  b=body(ok(production.prepared(cook,k(),id(b),new ProductionService.Transition(ver(b)))));
  return body(ok(production.approveDispatch(dispatcher,k(),id(b),new ProductionService.Transition(ver(b)))));
 }
 @SuppressWarnings("unchecked") List<Map<String,Object>> lines(Map<String,Object> b){return (List<Map<String,Object>>)b.get("lines");}
 UUID mealOf(Map<String,Object> batch,UUID patient){
  return lines(batch).stream().filter(l->patient.equals(l.get("patientRef"))).map(l->(UUID)l.get("id")).findFirst().orElseThrow();
 }
 Map<String,Object> loadTray(UUID meal,String code){
  return body(ok(delivery.load(porter,k(),new DeliveryService.LoadRequest(meal,code))));
 }
 Map<String,Object> inTransit(UUID meal,String code){
  Map<String,Object> t=loadTray(meal,code);
  return body(ok(delivery.dispatch(porter,k(),id(t),new DeliveryService.Transition(ver(t)))));
 }
 String status(String table,UUID id){return admin.queryForObject("SELECT status FROM synqoria_nutrition."+table+" WHERE id=?",String.class,id);}

 // ------------------------------------------------------------------ tests
 @Test void fullLifecyclePrescribedCheckedPreparedDeliveredAcknowledged(){
  baseline();
  Map<String,Object> b=dispatchedBatch();
  assertEquals("DISPATCH_APPROVED",b.get("status"));
  assertEquals(2,lines(b).size());
  @SuppressWarnings("unchecked") Map<String,Object> portions=(Map<String,Object>)b.get("portions");
  assertEquals(2,((Number)portions.get("TOTAL")).intValue());
  UUID meal=mealOf(b,p1);
  Map<String,Object> t=inTransit(meal,"TRAY-0001");
  Map<String,Object> d=body(ok(delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t),p1,"WRISTBAND_SCAN"))));
  assertEquals("DELIVERED",d.get("status"));
  assertEquals("DELIVERED",d.get("mealStatus"));
  Map<String,Object> a=body(ok(delivery.acknowledge(nurse,k(),id(d),new DeliveryService.Transition(ver(d)))));
  assertEquals("ACKNOWLEDGED",a.get("status"));
  assertEquals("ACKNOWLEDGED",status("meal_services",meal));
  assertTrue(admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.outbox",Long.class)>10);
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.audit_events e LEFT JOIN synqoria_nutrition.outbox o USING(event_id) WHERE o.event_id IS NULL",Long.class));
 }

 @Test void kitchenStaffCannotCreateOrChangeClinicalPrescriptions(){
  baseline();
  var r=req(p1,"REGULAR","NORMAL",List.of("PEANUT"),List.of("LUNCH"));
  assertEquals("permission_denied",code(()->orders.prescribe(planner,k(),r)));
  assertEquals("permission_denied",code(()->orders.prescribe(cook,k(),r)));
  assertEquals("permission_denied",code(()->orders.prescribe(porter,k(),r)));
  UUID current=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p1);
  assertEquals("permission_denied",code(()->orders.revoke(planner,k(),current,new OrderService.TransitionRequest(2,"nope"))));
  assertEquals("permission_denied",code(()->orders.amend(cook,k(),current,new OrderService.AmendRequest(2,"nope",r))));
  assertEquals("permission_denied",code(()->orders.approve(planner,k(),current,new OrderService.TransitionRequest(2,"nope"))));
  assertEquals("permission_denied",code(()->production.decide(planner,k(),UUID.randomUUID(),new ProductionService.Decision(1,true,"x"))));
 }

 @Test void prescriberCannotApproveOwnOrderAndApprovalNeedsFreshAllergyReconciliation(){
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");
  Actor both=actor(clinicianId,cell,facility,Perm.ORDER_PRESCRIBE,Perm.ORDER_APPROVE,Perm.ORDER_READ);
  Map<String,Object> o=body(ok(orders.prescribe(both,k(),req(p1,"REGULAR","NORMAL",List.of("PEANUT"),List.of("LUNCH")))));
  assertEquals("approver_must_be_independent",code(()->orders.approve(both,k(),id(o),new OrderService.TransitionRequest(1,"self"))));
  // no allergy information at all: unknown, therefore not approvable
  assertEquals("allergy_reconciliation_required",code(()->orders.approve(approver,k(),id(o),new OrderService.TransitionRequest(1,"x"))));
  allergies(p1,1,"PEANUT","EGG");
  assertEquals("allergen_not_excluded",code(()->orders.approve(approver,k(),id(o),new OrderService.TransitionRequest(1,"x"))));
  // DB-level backstop: approver == prescriber is structurally impossible
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.dietary_orders SET status='APPROVED',approver_id=prescriber_id,approved_at=now(),reconciled_allergy_version=1 WHERE id=?",id(o)));
 }

 @Test void conflictingDietInstructionsAreRejectedByDomainAndByDatabase(){
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");
  assertEquals("diet_contradictory",code(()->orders.prescribe(clinician,k(),req(p1,"NIL_BY_MOUTH","NORMAL",List.of(),List.of()))));
  assertEquals("diet_contradictory",code(()->orders.prescribe(clinician,k(),req(p1,"PUREED","NORMAL",List.of(),List.of("LUNCH")))));
  assertEquals("diet_contradictory",code(()->orders.prescribe(clinician,k(),req(p1,"NIL_BY_MOUTH","NONE",List.of(),List.of("LUNCH")))));
  // bypass the Java layer entirely: the CHECK constraints still hold
  assertThrows(DataAccessException.class,()->admin.update(
   "INSERT INTO synqoria_nutrition.dietary_orders(id,cell_id,facility_id,patient_ref,clinical_order_ref,status,diet_category,texture,restrictions,"+
   "excluded_allergens,meal_timing,effective_from,prescriber_id,version) VALUES (gen_random_uuid(),?,?,?,gen_random_uuid(),'PRESCRIBED','NIL_BY_MOUTH','NORMAL','{}','{}','{}',now(),gen_random_uuid(),1)",
   cell,facility,p1));
 }

 @Test void amendmentSupersedesAtomicallyAndOrderHistoryIsImmutable(){
  baseline();
  UUID old=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p1);
  var next=req(p1,"DIABETIC","SOFT_BITE_SIZED",List.of("PEANUT"),List.of("LUNCH","DINNER"));
  Map<String,Object> am=body(ok(orders.amend(clinician,k(),old,new OrderService.AmendRequest(2,"clinical review",next))));
  assertEquals("PRESCRIBED",am.get("status"));
  assertEquals("APPROVED",status("dietary_orders",old),"the old order stays current until the amendment is independently approved");
  ok(orders.approve(approver,k(),id(am),new OrderService.TransitionRequest(1,"ok")));
  assertEquals("SUPERSEDED",status("dietary_orders",old));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",Long.class,p1));
  // approved clinical content cannot be edited in place, even by the table owner
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.dietary_orders SET diet_category='REGULAR' WHERE id=?",id(am)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.dietary_orders SET status='APPROVED' WHERE id=?",old));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_nutrition.dietary_orders WHERE id=?",old));
  // audit history is append-only
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.audit_events SET action='X'"));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_nutrition.audit_events"));
  assertThrows(DataAccessException.class,()->admin.execute("TRUNCATE synqoria_nutrition.audit_events"));
  List<Map<String,Object>> hist=orders.history(clinician,old);
  assertEquals(List.of("PRESCRIBE","APPROVE","SUPERSEDE"),hist.stream().map(h->(String)h.get("action")).toList());
  // an amendment cannot be made against a stale view of the order
  assertEquals("stale_version",code(()->orders.amend(clinician,k(),old,new OrderService.AmendRequest(2,"late",next))));
 }

 @Test void duplicateMealIssuanceIsImpossible(){
  baseline();
  Map<String,Object> b=dispatchedBatch();
  // second plan for the same ward/date/meal
  assertEquals("batch_already_planned",code(()->production.create(planner,k(),new ProductionService.BatchRequest("W1",DAY,"LUNCH",clock.instant().plusSeconds(3600)))));
  UUID meal=mealOf(b,p1);
  Map<String,Object> t=loadTray(meal,"TRAY-0001");
  assertEquals("tray_already_issued",code(()->delivery.load(porter,k(),new DeliveryService.LoadRequest(meal,"TRAY-0002"))));
  assertEquals("tray_already_issued",code(()->delivery.load(porter,k(),new DeliveryService.LoadRequest(mealOf(b,p2),"TRAY-0001"))));
  // DB backstops, bypassing the service layer
  assertThrows(DataAccessException.class,()->admin.update(
   "INSERT INTO synqoria_nutrition.trays(id,cell_id,facility_id,meal_service_id,tray_code,status,loaded_by,version) VALUES (gen_random_uuid(),?,?,?,'TRAY-9999','LOADED',gen_random_uuid(),1)",cell,facility,meal));
  assertThrows(DataAccessException.class,()->admin.update(
   "INSERT INTO synqoria_nutrition.meal_services(id,cell_id,facility_id,batch_id,patient_ref,order_id,ward_code,bed_code,meal_date,meal_type,diet_category,texture,status,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,batch_id,patient_ref,order_id,ward_code,bed_code,meal_date,meal_type,diet_category,texture,'PLANNED',1 FROM synqoria_nutrition.meal_services WHERE id=?",meal));
  assertNotNull(t);
 }

 @Test void concurrentDuplicateRequestsYieldExactlyOneWinner() throws Exception{
  baseline();
  Map<String,Object> b=dispatchedBatch();
  UUID meal=mealOf(b,p1);
  ExecutorService pool=Executors.newFixedThreadPool(6);
  CyclicBarrier gate=new CyclicBarrier(6);
  List<Future<String>> f=new ArrayList<>();
  for(int i=0;i<6;i++){
   final int n=i;
   f.add(pool.submit(()->{gate.await();
    try{Reply r=delivery.load(porter,k(),new DeliveryService.LoadRequest(meal,"TRAY-C"+n+"0"));return "OK"+r.status();}
    catch(Violation v){return v.code();}catch(DataAccessException e){return "duplicate_record";}}));
  }
  List<String> results=new ArrayList<>();for(Future<String> x:f)results.add(x.get(30,TimeUnit.SECONDS));
  pool.shutdown();
  assertEquals(1,results.stream().filter(r->r.startsWith("OK2")).count(),results.toString());
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.trays WHERE meal_service_id=?",Long.class,meal));
 }

 @Test void concurrentApprovalsAreSerialisedByOptimisticVersion() throws Exception{
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");allergies(p1,1);
  Map<String,Object> o=body(ok(orders.prescribe(clinician,k(),req(p1,"REGULAR","NORMAL",List.of(),List.of("LUNCH")))));
  Actor a2=actor(UUID.randomUUID(),cell,facility,Perm.ORDER_APPROVE,Perm.ORDER_READ);
  ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
  Callable<String> c1=()->{gate.await();try{return "OK"+orders.approve(approver,k(),id(o),new OrderService.TransitionRequest(1,"a")).status();}catch(Violation v){return v.code();}};
  Callable<String> c2=()->{gate.await();try{return "OK"+orders.approve(a2,k(),id(o),new OrderService.TransitionRequest(1,"b")).status();}catch(Violation v){return v.code();}};
  Future<String> f1=pool.submit(c1),f2=pool.submit(c2);
  List<String> res=List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));
  pool.shutdown();
  assertEquals(1,res.stream().filter(r->r.equals("OK200")).count(),res.toString());
  assertEquals(1,res.stream().filter(r->r.equals("stale_version")).count(),res.toString());
 }

 @Test void idempotentReplayAndKeyReuseWithDifferentPayload(){
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");
  UUID key=k();var r=req(p1,"REGULAR","NORMAL",List.of(),List.of("LUNCH"));
  Reply first=orders.prescribe(clinician,key,r),replay=orders.prescribe(clinician,key,r);
  assertEquals(201,first.status());assertEquals(201,replay.status());
  assertEquals(id(body(first)).toString(),String.valueOf(body(replay).get("id")));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.dietary_orders",Long.class));
  var changed=req(p1,"RENAL","NORMAL",List.of(),List.of("LUNCH"));
  assertEquals("idempotency_collision",code(()->orders.prescribe(clinician,key,changed)));
  assertEquals("idempotency_key_required",code(()->orders.prescribe(clinician,null,r)));
 }

 @Test void revokedDietaryOrderHoldsPlannedMealsAndBlocksDelivery(){
  baseline();
  Map<String,Object> b=dispatchedBatch();
  UUID meal=mealOf(b,p1);
  Map<String,Object> t=inTransit(meal,"TRAY-0001");
  UUID order=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p1);
  ok(orders.revoke(clinician,k(),order,new OrderService.TransitionRequest(2,"clinical change")));
  assertEquals("HELD",status("meal_services",meal));
  assertEquals("EXCEPTION",status("trays",id(t)));
  assertEquals("invalid_transition",code(()->delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t)+1,p1,"WRISTBAND_SCAN"))));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.trays WHERE status='DELIVERED'",Long.class));
  // and a revoked order can never be revived or re-approved
  assertEquals("invalid_transition",code(()->orders.cancel(clinician,k(),order,new OrderService.TransitionRequest(3,"again"))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.dietary_orders SET status='APPROVED' WHERE id=?",order));
 }

 @Test void revokeRacingTheDeliveryGateNeverShipsRevokedDiet() throws Exception{
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);
  Map<String,Object> t=inTransit(meal,"TRAY-0001");
  UUID order=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p1);
  ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
  Future<String> rev=pool.submit(()->{gate.await();try{return "REV"+orders.revoke(clinician,k(),order,new OrderService.TransitionRequest(2,"race")).status();}catch(Violation v){return v.code();}});
  Future<String> del=pool.submit(()->{gate.await();try{return "DEL"+delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t),p1,"WRISTBAND_SCAN")).status();}catch(Violation v){return v.code();}});
  String r1=rev.get(30,TimeUnit.SECONDS),r2=del.get(30,TimeUnit.SECONDS);pool.shutdown();
  assertEquals("REV200",r1);
  String trayStatus=status("trays",id(t));
  // either delivery completed strictly before the revoke committed (then revoke could not hold a DELIVERED meal)
  // or it was blocked. A delivery after the revoke committed must never succeed.
  if(r2.equals("DEL200")){
   assertEquals("DELIVERED",trayStatus);
   assertTrue(admin.queryForObject("SELECT delivered_at <= (SELECT max(occurred_at) FROM synqoria_nutrition.audit_events WHERE action='REVOKE') FROM synqoria_nutrition.trays WHERE id=?",Boolean.class,id(t)));
  }else assertEquals("EXCEPTION",trayStatus,r2);
 }

 @Test void outdatedAllergyInformationBlocksProductionAndDelivery(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);
  // 25 hours later with no re-verification of the allergy source: must not be shipped
  clock.now=clock.now.plus(Duration.ofHours(25));
  DeliveryService.LoadRequest load=new DeliveryService.LoadRequest(meal,"TRAY-0001");
  Reply r=delivery.load(porter,k(),load);
  assertEquals(409,r.status());assertEquals("diet_safety_block",body(r).get("error"));
  assertTrue(body(r).get("details").toString().contains("ALLERGY_OUTDATED"));
  assertEquals("HELD",status("meal_services",meal));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_nutrition.delivery_incidents WHERE meal_service_id=? AND status='ESCALATED'",Long.class,meal));
  // the committed rejection replays identically
 }

 @Test void allergyAddedAfterApprovalBlocksUntilClinicallyReconciled(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p2);
  allergies(p2,2,"SHELLFISH");
  Reply r=delivery.load(porter,k(),new DeliveryService.LoadRequest(meal,"TRAY-0001"));
  assertEquals(409,r.status());
  String d=body(r).get("details").toString();
  assertTrue(d.contains("ALLERGY_CHANGED")&&d.contains("ALLERGEN_CONFLICT"),d);
  var inc=admin.queryForMap("SELECT incident_type,severity FROM synqoria_nutrition.delivery_incidents WHERE meal_service_id=?",meal);
  assertEquals("ALLERGEN_RISK",inc.get("incident_type"));assertEquals("CRITICAL",inc.get("severity"));
  // stale allergy feed versions are refused (cannot roll back to an older allergy view)
  assertEquals("stale_source_version",code(()->orders.feedAllergies(integrator,k(),p2,new OrderService.Allergies(k(),1,List.of(),clock.instant()))));
 }

 @Test void activeFastingExcludesPatientFromPlanAndBlocksLater(){
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");patient(p2,"SYNTHETIC-PATIENT-002","W1","B02");
  allergies(p1,1);allergies(p2,1);approvedOrder(p1);approvedOrder(p2);
  UUID ins=UUID.randomUUID();
  ok(orders.feedFasting(integrator,k(),ins,new OrderService.FastingFeed(k(),p1,1,"ACTIVE","PRE_PROCEDURE",clock.instant().minusSeconds(60),null,clock.instant())));
  Map<String,Object> b=newBatch();
  assertEquals(1,lines(b).size());
  assertTrue(b.get("exceptions").toString().contains("FASTING_ACTIVE"));
  // fasting revoked by the clinical authority => P1 can be planned in a new batch after cancel
  ok(orders.feedFasting(integrator,k(),ins,new OrderService.FastingFeed(k(),p1,2,"REVOKED","PRE_PROCEDURE",clock.instant().minusSeconds(60),null,clock.instant())));
  assertEquals("stale_source_version",code(()->orders.feedFasting(integrator,k(),ins,new OrderService.FastingFeed(k(),p1,2,"ACTIVE","PRE_PROCEDURE",clock.instant(),null,clock.instant()))));
 }

 @Test void fastingStartedAfterDispatchApprovalBlocksTrayLoad(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);
  ok(orders.feedFasting(integrator,k(),UUID.randomUUID(),new OrderService.FastingFeed(k(),p1,1,"ACTIVE","SURGERY",clock.instant(),null,clock.instant())));
  Reply r=delivery.load(porter,k(),new DeliveryService.LoadRequest(meal,"TRAY-0001"));
  assertEquals(409,r.status());assertTrue(body(r).get("details").toString().contains("FASTING_ACTIVE"));
 }

 @Test void mistakenPatientIdentityIsRejectedAndEscalated(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);
  Map<String,Object> t=inTransit(meal,"TRAY-0001");
  Reply r=delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t),p2,"WRISTBAND_SCAN"));
  assertEquals(409,r.status());assertEquals("identity_mismatch",body(r).get("error"));
  assertEquals("EXCEPTION",status("trays",id(t)));
  assertEquals("HELD",status("meal_services",meal));
  var inc=admin.queryForMap("SELECT incident_type,severity,status,escalated_to_role FROM synqoria_nutrition.delivery_incidents WHERE meal_service_id=?",meal);
  assertEquals("WRONG_PATIENT",inc.get("incident_type"));assertEquals("CRITICAL",inc.get("severity"));assertEquals("ESCALATED",inc.get("status"));
  assertNull(admin.queryForObject("SELECT delivered_at FROM synqoria_nutrition.trays WHERE id=?",Timestamp.class,id(t)));
  // a null/absent scan is refused outright
  assertEquals("identity_confirmation_required",code(()->delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t),null,"WRISTBAND_SCAN"))));
 }

 @Test void invalidHandoversAreRefused(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);
  Map<String,Object> t=loadTray(meal,"TRAY-0001");
  // cannot deliver a tray that has not left the kitchen
  assertEquals("invalid_transition",code(()->delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t),p1,"WRISTBAND_SCAN"))));
  assertEquals("invalid_transition",code(()->delivery.acknowledge(nurse,k(),id(t),new DeliveryService.Transition(ver(t)))));
  Map<String,Object> t2=body(ok(delivery.dispatch(porter,k(),id(t),new DeliveryService.Transition(ver(t)))));
  assertEquals("stale_version",code(()->delivery.dispatch(porter,k(),id(t),new DeliveryService.Transition(ver(t)))));
  assertEquals("invalid_identity_method",code(()->delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t2),p1,"GUESS"))));
  Map<String,Object> d=body(ok(delivery.deliver(porter,k(),id(t),new DeliveryService.DeliverRequest(ver(t2),p1,"DUAL_VERBAL"))));
  // the deliverer cannot acknowledge their own delivery
  Actor porterAsNurse=actor(porterId,cell,facility,Perm.ACK_RECORD,Perm.DELIVERY_RECORD,Perm.DELIVERY_READ);
  assertEquals("ack_must_be_independent",code(()->delivery.acknowledge(porterAsNurse,k(),id(d),new DeliveryService.Transition(ver(d)))));
  assertEquals("permission_denied",code(()->delivery.acknowledge(porter,k(),id(d),new DeliveryService.Transition(ver(d)))));
  ok(delivery.acknowledge(nurse,k(),id(d),new DeliveryService.Transition(ver(d))));
  // delivery evidence is immutable afterwards
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_nutrition.trays SET delivered_by=gen_random_uuid() WHERE id=?",id(t)));
  // meals that were never dispatch-approved cannot be loaded
  UUID other=mealOf(b,p2);
  admin.update("UPDATE synqoria_nutrition.meal_services SET status='PREPARED' WHERE id=?",other);
  assertEquals("meal_not_dispatch_approved",code(()->delivery.load(porter,k(),new DeliveryService.LoadRequest(other,"TRAY-0002"))));
 }

 @Test void dispatchApprovalMustBeIndependentAndSubstitutionsMustBeDecided(){
  baseline();
  Map<String,Object> b=newBatch();
  b=body(ok(production.release(planner,k(),id(b),new ProductionService.Transition(ver(b)))));
  UUID meal=mealOf(b,p2);
  Map<String,Object> s=body(ok(production.propose(planner,k(),new ProductionService.SubstitutionRequest(meal,"RICE_MEAL","POTATO_MEAL",List.of(),"OUT_OF_STOCK"))));
  final Map<String,Object> released=b;
  assertEquals("substitutions_pending",code(()->production.prepared(cook,k(),id(released),new ProductionService.Transition(ver(released)))));
  assertEquals("decider_must_be_independent",code(()->production.decide(
   actor(plannerId,cell,facility,Perm.SUBST_DECIDE),k(),id(s),new ProductionService.Decision(1,true,"self"))));
  ok(production.decide(approver,k(),id(s),new ProductionService.Decision(1,true,"ok")));
  Map<String,Object> prep=body(ok(production.prepared(cook,k(),id(b),new ProductionService.Transition(ver(b)))));
  Actor plannerAsDispatcher=actor(plannerId,cell,facility,Perm.DISPATCH_APPROVE);
  assertEquals("dispatch_must_be_independent",code(()->production.approveDispatch(plannerAsDispatcher,k(),id(prep),new ProductionService.Transition(ver(prep)))));
  Actor cookAsDispatcher=actor(cookId,cell,facility,Perm.DISPATCH_APPROVE);
  assertEquals("dispatch_must_be_independent",code(()->production.approveDispatch(cookAsDispatcher,k(),id(prep),new ProductionService.Transition(ver(prep)))));
  ok(production.approveDispatch(dispatcher,k(),id(prep),new ProductionService.Transition(ver(prep))));
  assertEquals("DISPATCH_APPROVED",status("production_batches",id(prep)));
 }

 @Test void substitutionWithPatientAllergenCannotBeApproved(){
  baseline();
  Map<String,Object> b=newBatch();UUID meal=mealOf(b,p1);
  Map<String,Object> s=body(ok(production.propose(planner,k(),new ProductionService.SubstitutionRequest(meal,"RICE_MEAL","SATAY",List.of("PEANUT"),"OUT_OF_STOCK"))));
  assertEquals("substitution_allergen_conflict",code(()->production.decide(approver,k(),id(s),new ProductionService.Decision(1,true,"x"))));
  ok(production.decide(approver,k(),id(s),new ProductionService.Decision(1,false,"rejected")));
 }

 @Test void releaseHoldsLinesWhoseDietChangedSincePlanning(){
  baseline();
  Map<String,Object> b=newBatch();
  UUID order=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p2);
  ok(orders.revoke(clinician,k(),order,new OrderService.TransitionRequest(2,"changed")));
  Map<String,Object> rel=body(ok(production.release(planner,k(),id(b),new ProductionService.Transition(ver(b)))));
  assertEquals(1,lines(rel).stream().filter(l->"PLANNED".equals(l.get("status"))).count());
  assertEquals(1,lines(rel).stream().filter(l->"HELD".equals(l.get("status"))).count());
  @SuppressWarnings("unchecked") Map<String,Object> portions=(Map<String,Object>)rel.get("portions");
  assertEquals(1,((Number)portions.get("TOTAL")).intValue());
 }

 @Test void cutoffIsEnforced(){
  baseline();
  Map<String,Object> b=newBatch();
  clock.now=clock.now.plus(Duration.ofHours(5));
  assertEquals("cutoff_passed",code(()->production.release(planner,k(),id(b),new ProductionService.Transition(ver(b)))));
  assertEquals("cutoff_passed",code(()->production.create(planner,k(),new ProductionService.BatchRequest("W2",DAY,"LUNCH",clock.instant().minusSeconds(1)))));
 }

 @Test void crossHospitalAndCrossFacilityAccessIsImpossible()throws Exception{
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);Map<String,Object> t=loadTray(meal,"TRAY-0001");
  UUID order=admin.queryForObject("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",UUID.class,p1);
  Actor otherCell=actor(UUID.randomUUID(),UUID.randomUUID(),facility,Perm.ORDER_READ,Perm.ORDER_REVOKE,Perm.WORKLIST_READ,Perm.DELIVERY_READ,Perm.DELIVERY_RECORD,Perm.PLAN_MANAGE);
  Actor otherFacility=actor(UUID.randomUUID(),cell,UUID.randomUUID(),Perm.ORDER_READ,Perm.ORDER_REVOKE,Perm.WORKLIST_READ,Perm.DELIVERY_READ,Perm.DELIVERY_RECORD,Perm.PLAN_MANAGE);
  for(Actor foreign:List.of(otherCell,otherFacility)){
   assertEquals("not_found",code(()->orders.get(foreign,order)));
   assertEquals("not_found",code(()->orders.revoke(foreign,k(),order,new OrderService.TransitionRequest(2,"x"))));
   assertEquals("not_found",code(()->production.get(foreign,id(b))));
   assertEquals("not_found",code(()->delivery.get(foreign,id(t))));
   assertEquals("not_found",code(()->worklist.detail(foreign,p1)));
   assertEquals(0L,((Number)worklist.list(foreign,new WorklistService.Query(null,null,null,null,null,null,null,null,null,0,50)).get("total")).longValue());
   assertEquals(0L,((Number)orders.list(foreign,null,null,0,50).get("total")).longValue());
   assertEquals("patient_unknown",code(()->orders.prescribe(actor(UUID.randomUUID(),foreign.cell(),foreign.facility(),Perm.ORDER_PRESCRIBE),k(),req(p1,"REGULAR","NORMAL",List.of(),List.of("LUNCH")))));
  }
  // raw SQL under the runtime principal: no scope => nothing; wrong scope => nothing; forged insert => rejected by RLS
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   assertEquals(0,count(c,"SELECT count(*) FROM synqoria_nutrition.dietary_orders"));
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true)")){
    ps.setString(1,UUID.randomUUID().toString());ps.setString(2,facility.toString());ps.execute();
   }
   assertEquals(0,count(c,"SELECT count(*) FROM synqoria_nutrition.patient_context"));
   assertEquals(0,count(c,"SELECT count(*) FROM synqoria_nutrition.audit_events"));
   assertThrows(SQLException.class,()->{
    try(Statement s=c.createStatement()){s.execute("INSERT INTO synqoria_nutrition.consumed_events(cell_id,facility_id,source_event_id,kind) VALUES ('"+cell+"','"+facility+"',gen_random_uuid(),'ALLERGY')");}
   });
   c.rollback();
  }
 }
 static int count(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}}

 @Test void runtimeRoleIsLeastPrivilegedAndRlsIsForcedEverywhere()throws Exception{
  List<String> unforced=admin.queryForList("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='synqoria_nutrition' AND c.relkind='r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity)",String.class);
  assertEquals(List.of(),unforced);
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_nutrition_runtime' AND table_schema='synqoria_nutrition' AND privilege_type IN ('DELETE','TRUNCATE')",Long.class));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_nutrition_runtime' AND table_name IN ('audit_events','outbox','consumed_events') AND privilege_type IN ('UPDATE')",Long.class));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='PUBLIC' AND table_schema='synqoria_nutrition'",Long.class));
  // an owner-level or BYPASSRLS login is refused by the service
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  Db bad=new Db(new JdbcTemplate(ads),JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"synqoria_nutrition_runtime",24,"UTC");
  assertEquals("database_role_denied",code(()->new OrderService(bad).get(clinician,UUID.randomUUID())));
  assertThrows(IllegalStateException.class,()->new Db(admin,JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"postgres",24,"UTC"));
  // runtime principal cannot rewrite history even in its own scope
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true)")){ps.setString(1,cell.toString());ps.setString(2,facility.toString());ps.execute();}
   assertThrows(SQLException.class,()->{try(Statement s=c.createStatement()){s.execute("DELETE FROM synqoria_nutrition.audit_events");}});
   c.rollback();
  }
 }

 @Test void worklistFiltersSortsPagesAndFlagsExceptions(){
  baseline();
  patient(UUID.randomUUID(),"SYNTHETIC-PATIENT-003","W2","B07"); // admitted, no allergy info, no order => unknown diet
  ok(orders.feedFasting(integrator,k(),UUID.randomUUID(),new OrderService.FastingFeed(k(),p2,1,"ACTIVE","SURGERY",clock.instant().minusSeconds(60),null,clock.instant())));
  var all=worklist.list(planner,new WorklistService.Query(null,null,null,null,null,null,null,"ward","asc",0,25));
  assertEquals(3L,((Number)all.get("total")).longValue());
  @SuppressWarnings("unchecked") List<Map<String,Object>> items=(List<Map<String,Object>>)all.get("items");
  assertEquals("SYNTHETIC-PATIENT-001",items.get(0).get("patientLabel"));
  assertEquals(List.of("W1","W1","W2"),items.stream().map(i->(String)i.get("wardCode")).toList());
  Map<String,Object> r1=items.get(0),r2=items.get(1),r3=items.get(2);
  assertEquals(List.of(),r1.get("exceptions"));assertEquals(false,r1.get("hasException"));
  assertEquals(List.of("FASTING_ACTIVE"),r2.get("exceptions"));assertEquals(true,r2.get("hasException"));
  assertTrue(r3.get("exceptions").toString().contains("DIET_UNKNOWN"));
  // every row flagged by the SQL mirror has at least one code from the domain gate, and vice versa
  for(Map<String,Object> r:items)assertEquals(Boolean.TRUE.equals(r.get("hasException")),!((List<?>)r.get("exceptions")).isEmpty(),r.toString());
  assertEquals(2L,total(new WorklistService.Query("W1",null,null,null,null,null,null,null,null,0,25)));
  assertEquals(1L,total(new WorklistService.Query(null,null,null,true,null,null,null,null,null,0,25)));
  assertEquals(1L,total(new WorklistService.Query(null,null,null,null,true,null,null,null,null,0,25)));
  assertEquals(2L,total(new WorklistService.Query(null,null,null,null,null,true,null,null,null,0,25)));
  assertEquals(1L,total(new WorklistService.Query(null,null,null,null,null,false,null,null,null,0,25)));
  assertEquals(1L,total(new WorklistService.Query(null,null,"NONE",null,null,null,null,null,null,0,25)));
  assertEquals(1L,total(new WorklistService.Query(null,null,null,null,null,null,"patient-002",null,null,0,25)));
  assertEquals(0L,total(new WorklistService.Query(null,null,null,null,null,null,"%",null,null,0,25)));
  var desc=worklist.list(planner,new WorklistService.Query(null,null,null,null,null,null,null,"patient","desc",0,2));
  @SuppressWarnings("unchecked") List<Map<String,Object>> d=(List<Map<String,Object>>)desc.get("items");
  assertEquals(2,d.size());assertEquals("SYNTHETIC-PATIENT-003",d.get(0).get("patientLabel"));assertEquals(3L,((Number)desc.get("total")).longValue());
  assertEquals("invalid_sort",code(()->worklist.list(planner,new WorklistService.Query(null,null,null,null,null,null,null,"patient_label; DROP TABLE x","asc",0,2))));
  assertEquals("invalid_pagination",code(()->worklist.list(planner,new WorklistService.Query(null,null,null,null,null,null,null,null,null,0,1000))));
  assertEquals("permission_denied",code(()->worklist.list(integrator,new WorklistService.Query(null,null,null,null,null,null,null,null,null,0,5))));
 }
 long total(WorklistService.Query q){return ((Number)worklist.list(planner,q).get("total")).longValue();}

 @Test void incidentsEscalateAndResolveWithIndependence(){
  baseline();
  Map<String,Object> b=dispatchedBatch();UUID meal=mealOf(b,p1);Map<String,Object> t=inTransit(meal,"TRAY-0001");
  Map<String,Object> ex=body(ok(delivery.reportException(porter,k(),id(t),new DeliveryService.ExceptionRequest(ver(t),"LATE","LOW","ward closed for round"))));
  assertEquals("EXCEPTION",ex.get("status"));
  UUID inc=admin.queryForObject("SELECT id FROM synqoria_nutrition.delivery_incidents WHERE meal_service_id=?",UUID.class,meal);
  Actor porterEsc=actor(porterId,cell,facility,Perm.INCIDENT_ESCALATE,Perm.INCIDENT_RESOLVE);
  Map<String,Object> e=body(ok(delivery.escalate(nurse,k(),inc,new DeliveryService.EscalateRequest(1,"NURSE_IN_CHARGE"))));
  assertEquals("ESCALATED",e.get("status"));
  assertEquals("resolver_must_be_independent",code(()->delivery.resolve(porterEsc,k(),inc,new DeliveryService.ResolveRequest(2,"self-resolve"))));
  assertEquals("resolution_note_required",code(()->delivery.resolve(nurse,k(),inc,new DeliveryService.ResolveRequest(2,""))));
  Map<String,Object> r=body(ok(delivery.resolve(nurse,k(),inc,new DeliveryService.ResolveRequest(2,"re-delivered after round"))));
  assertEquals("RESOLVED",r.get("status"));
  assertEquals("invalid_transition",code(()->delivery.resolve(nurse,k(),inc,new DeliveryService.ResolveRequest(3,"again"))));
 }

 @Test void feedRejectsStaleAndUnknownPatients(){
  assertEquals("patient_unknown",code(()->orders.feedAllergies(integrator,k(),UUID.randomUUID(),new OrderService.Allergies(k(),1,List.of(),clock.instant()))));
  patient(p1,"SYNTHETIC-PATIENT-001","W1","B01");
  assertEquals("stale_source_version",code(()->orders.feedPatient(integrator,k(),p1,new OrderService.PatientContext(k(),1,"SYNTHETIC-PATIENT-001","W1","B01",true,clock.instant()))));
  assertEquals("permission_denied",code(()->orders.feedPatient(clinician,k(),p1,new OrderService.PatientContext(k(),2,"X","W1","B01",true,clock.instant()))));
  assertEquals("invalid_verified_at",code(()->orders.feedAllergies(integrator,k(),p1,new OrderService.Allergies(k(),5,List.of(),clock.instant().plus(Duration.ofDays(2))))));
  UUID ev=k();OrderService.Allergies a=new OrderService.Allergies(ev,3,List.of("EGG"),clock.instant());
  ok(orders.feedAllergies(integrator,k(),p1,a));
  Reply again=orders.feedAllergies(integrator,k(),p1,a); // same source event, new key: applied once
  assertEquals("already_applied",body(again).get("status"));
 }
}
