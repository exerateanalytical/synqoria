package org.synqoria.scheduling;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.scheduling.SchedulingApplication.*;
import static org.synqoria.scheduling.SchedulingStore.*;

/** PostgreSQL 18.6 CI uses synthetic patient proofs, never operational clinical data. */
class SchedulingPgIntegrationTest{
 private static final UUID CELL=UUID.fromString("02000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("02000000-0000-4000-8000-000000000002");
 private static final UUID ACTOR=UUID.fromString("02000000-0000-4000-8000-000000000003");
 private static final UUID ISSUER=UUID.fromString("02000000-0000-4000-8000-000000000004");
 private static final UUID PATIENT=UUID.fromString("02000000-0000-4000-8000-000000000005");
 private SchedulingStore store;private JdbcTemplate admin;
 private static UUID key(){return UUID.randomUUID();}
 private Actor actor(String permission){return new Actor(ACTOR,CELL,FAC,Set.of(permission));}
 private DriverManagerDataSource ds(String user,String password){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_M02_TEST_JDBC_URL"),user,password);
 }
 private static Map<String,Object> body(Reply r){
  @SuppressWarnings("unchecked") var b=(Map<String,Object>)r.body();return b;
 }
 private static UUID id(Reply r){return UUID.fromString((String)body(r).get("id"));}
 private Instant future(long seconds){return Instant.now().plusSeconds(seconds);}
 @BeforeEach void setup()throws Exception{
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_M02_PG_TEST")));
  var root=ds(System.getenv("SYNQORIA_M02_TEST_ADMIN_USER"),
    System.getenv("SYNQORIA_M02_TEST_ADMIN_PASSWORD"));
  admin=new JdbcTemplate(root);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_schedule_runtime') "+
   "THEN CREATE ROLE synqoria_schedule_runtime LOGIN NOINHERIT NOSUPERUSER "+
   "NOBYPASSRLS NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD 'm02-ci-only';END IF;END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_schedule CASCADE");
  try(Connection c=root.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("db","migration","V001__m02_resource_appointments.sql")));
   s.execute(Files.readString(Path.of("db","migration","V002__m02_rescheduling_transition.sql")));
  }
  var runtime=ds("synqoria_schedule_runtime","m02-ci-only");
  store=new SchedulingStore(new JdbcTemplate(runtime),JsonMapper.builder().build(),
    new DataSourceTransactionManager(runtime),Clock.systemUTC(),"synqoria_schedule_runtime");
 }
 private UUID resource(){return id(store.resource(actor("m02:resource_write"),key(),
  new Resource("ROOM_"+key().toString().substring(0,8).toUpperCase(),"ROOM")));}
 private UUID slot(UUID res,int offset){
  return id(store.slot(actor("m02:slot_publish"),key(),
   new Slot(res,future(offset),future(offset+1800))));
 }
 private UUID proof(){
  UUID id=key();
  admin.update("INSERT INTO synqoria_schedule.patient_proofs "+
   "(proof_id,cell_id,facility_id,patient_ref,issuer,approved_by,valid_from,valid_until) "+
   "VALUES(?,?,?,?,?,?,clock_timestamp()-interval '1 minute',clock_timestamp()+interval '3 hours')",
   id,CELL,FAC,PATIENT,"synthetic-patient-issuer",ISSUER);
  return id;
 }
 private UUID appointment(UUID s,UUID proof){
  return id(store.book(actor("m02:book"),key(),new Booking(PATIENT,proof,s)));
 }
 @Test void immutableScopeAndNonOverlappingResources(){
  UUID res=resource(),s=slot(res,3600);
  assertEquals(1,store.list(actor("m02:read"),"slots",10).size());
  assertThrows(DataAccessException.class,()->store.slot(actor("m02:slot_publish"),key(),
    new Slot(res,future(4000),future(4100))));
  assertEquals(8,admin.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON "+
   "n.oid=c.relnamespace WHERE n.nspname='synqoria_schedule' AND c.relkind='r' "+
   "AND c.relrowsecurity AND c.relforcerowsecurity",Integer.class));
  var foreign=new Actor(ACTOR,CELL,key(),Set.of("m02:read"));
  assertTrue(store.list(foreign,"slots",10).isEmpty());
  assertEquals(0,new JdbcTemplate(ds("synqoria_schedule_runtime","m02-ci-only"))
   .queryForObject("SELECT count(*) FROM synqoria_schedule.slots",Integer.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_schedule_runtime','synqoria_schedule.patient_proofs','INSERT')",Boolean.class));
  assertNotNull(s);
 }
 @Test void duplicateSlotBookingRejectedAndArrivalLifecycleGuarded(){
  UUID s=slot(resource(),3600),p=proof();
  var first=store.book(actor("m02:book"),key(),new Booking(PATIENT,p,s));
  assertEquals("REQUESTED",body(first).get("status"));
  assertThrows(DataAccessException.class,()->appointment(s,p));
  assertEquals("invalid_transition",assertThrows(Denied.class,()->
   store.appointmentAction(actor("m02:fulfil"),key(),id(first),"fulfil",new Version(1))).code);
  var confirmed=store.appointmentAction(actor("m02:confirm"),key(),id(first),"confirm",new Version(1));
  assertEquals("CONFIRMED",body(confirmed).get("status"));
  assertEquals("stale_version",assertThrows(Denied.class,()->
   store.appointmentAction(actor("m02:arrive"),key(),id(first),"arrive",new Version(1))).code);
  var arrived=store.appointmentAction(actor("m02:arrive"),key(),id(first),"arrive",new Version(2));
  assertEquals("ARRIVED",body(arrived).get("status"));
  var completed=store.appointmentAction(actor("m02:fulfil"),key(),id(first),"fulfil",new Version(3));
  assertEquals("FULFILLED",body(completed).get("status"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_schedule.appointments SET patient_ref=? WHERE id=?",key(),id(first)));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_schedule.audit_events SET action='TAMPER'"));
  assertEquals(admin.queryForObject("SELECT count(*) FROM synqoria_schedule.audit_events",Integer.class),
   admin.queryForObject("SELECT count(*) FROM synqoria_schedule.outbox",Integer.class));
 }
 @Test void reschedulingAndWaitlistAreRealAtomicWorkflows(){
  UUID resource=resource(),s1=slot(resource,3600),s2=slot(resource,7200),p=proof();
  var initial=store.book(actor("m02:book"),key(),new Booking(PATIENT,p,s1));
  var shifted=store.reschedule(actor("m02:book"),key(),id(initial),new Move(1,s2));
  assertEquals(2L,body(shifted).get("version"));
  var newPatientReservation=store.book(actor("m02:book"),key(),new Booking(PATIENT,p,s1));
  assertEquals("REQUESTED",body(newPatientReservation).get("status"));
  UUID s3=slot(resource,10800);
  var wait=store.waitlist(actor("m02:waitlist"),key(),new Wait(PATIENT,p,resource));
  var offered=store.offer(actor("m02:offer"),key(),id(wait),new Offer(1,s3));
  assertEquals("OFFERED",body(offered).get("status"));
  var accepted=store.accept(actor("m02:waitlist_accept"),key(),id(wait),new Version(2));
  assertEquals("BOOKED",body(accepted).get("status"));
  assertThrows(DataAccessException.class,()->appointment(s3,p));
 }
 @Test void patientProofIsExternalRevocableAndIdempotencyIsBoundToPayload(){
  UUID res=resource(),s=slot(res,3600),p=proof();
  UUID cmd=key();
  var request=new Booking(PATIENT,p,s);
  var first=store.book(actor("m02:book"),cmd,request);
  var repeat=store.book(actor("m02:book"),cmd,request);
  assertEquals(id(first),id(repeat));
  assertEquals("idempotency_collision",assertThrows(Denied.class,()->
   store.book(actor("m02:book"),cmd,new Booking(key(),p,s))).code);
  admin.update("UPDATE synqoria_schedule.patient_proofs SET revoked_at=clock_timestamp() WHERE proof_id=?",p);
  UUID later=slot(res,8000);
  assertEquals("evidence_unavailable",assertThrows(Denied.class,()->
   appointment(later,p)).code);
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_schedule.patient_proofs SET revoked_at=NULL WHERE proof_id=?",p));
 }
}
