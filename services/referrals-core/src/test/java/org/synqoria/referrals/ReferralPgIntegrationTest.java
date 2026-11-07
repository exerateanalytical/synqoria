package org.synqoria.referrals;
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
import static org.synqoria.referrals.ReferralApplication.*;
import static org.synqoria.referrals.ReferralStore.*;

class ReferralPgIntegrationTest{
 private static final UUID CELL=UUID.fromString("16000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("16000000-0000-4000-8000-000000000002");
 private static final UUID DEST=UUID.fromString("16000000-0000-4000-8000-000000000003");
 private static final UUID PATIENT=UUID.fromString("16000000-0000-4000-8000-000000000004");
 private static final UUID ISSUER=UUID.fromString("16000000-0000-4000-8000-000000000005");
 private static final UUID CREATOR=UUID.fromString("16000000-0000-4000-8000-000000000006");
 private static final UUID AUTHOR=UUID.fromString("16000000-0000-4000-8000-000000000007");
 private static final UUID SENDER=UUID.fromString("16000000-0000-4000-8000-000000000008");
 private static final UUID RECEIVER=UUID.fromString("16000000-0000-4000-8000-000000000009");
 private static final UUID TRANSFER=UUID.fromString("16000000-0000-4000-8000-000000000010");
 private static final UUID ATTEND=UUID.fromString("16000000-0000-4000-8000-000000000011");
 private static final UUID CLOSER=UUID.fromString("16000000-0000-4000-8000-000000000012");
 private JdbcTemplate admin;private ReferralStore store;
 private static UUID key(){return UUID.randomUUID();}
 private static Map<String,Object> body(Reply r){
  @SuppressWarnings("unchecked")Map<String,Object> x=(Map<String,Object>)r.body();return x;
 }
 private static UUID id(Reply r){return UUID.fromString((String)body(r).get("id"));}
 private Actor actor(UUID id,String permission){return new Actor(id,CELL,FAC,Set.of(permission));}
 private DriverManagerDataSource ds(String user,String password){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_M16_TEST_JDBC_URL"),user,password);
 }
 @BeforeEach void setup()throws Exception{
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_M16_PG_TEST")));
  var adminDb=ds(System.getenv("SYNQORIA_M16_TEST_ADMIN_USER"),
      System.getenv("SYNQORIA_M16_TEST_ADMIN_PASSWORD"));
  admin=new JdbcTemplate(adminDb);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_referral_runtime') "+
   "THEN CREATE ROLE synqoria_referral_runtime LOGIN NOINHERIT NOSUPERUSER "+
   "NOBYPASSRLS NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD 'm16-ci-only';END IF;END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_referral CASCADE");
  try(Connection c=adminDb.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("db","migration","V001__m16_referral_transition_evidence.sql")));
   s.execute(Files.readString(Path.of("db","migration","V002__m16_recorded_lifecycle_guards.sql")));
  }
  var runtime=ds("synqoria_referral_runtime","m16-ci-only");
  store=new ReferralStore(new JdbcTemplate(runtime),JsonMapper.builder().build(),
   new DataSourceTransactionManager(runtime),Clock.systemUTC(),"synqoria_referral_runtime");
 }
 private UUID proof(UUID ref,String kind,UUID referral,UUID receiver){
  UUID id=key();
  admin.update("INSERT INTO synqoria_referral.authority_proofs "+
   "(proof_id,cell_id,facility_id,patient_ref,referral_id,recipient_facility,kind,"+
   "external_actor,issuer,valid_from,valid_until,digest_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,?,clock_timestamp()-interval '5 minutes',"+
   "clock_timestamp()+interval '5 hours',repeat('a',64))",
   id,CELL,FAC,ref,referral,receiver,kind,ISSUER,"synthetic-independent-issuer");
  return id;
 }
 private Reply create(UUID patient,UUID dest,UUID consent){
  return store.create(actor(CREATOR,"m16:create"),key(),
   new Request(patient,dest,"SPECIALIST_REVIEW","b".repeat(64),consent));
 }
 private Reply action(UUID user,UUID referral,long version,String name,UUID proof){
  String permission=switch(name){
   case "authorize"->"m16:authorize";case "send"->"m16:send";
   case "accept","reject"->"m16:receiving_review";case "transfer"->"m16:transfer";
   case "attend"->"m16:attend";case "close"->"m16:close";default->"m16:cancel";
  };
  return store.act(actor(user,permission),key(),referral,name,new Action(version,proof));
 }
 @Test void referralNeedsRealScopeEvidenceAndCannotBeReassigned(){
  UUID consent=proof(PATIENT,"PATIENT_CONSENT",null,DEST);
  var created=create(PATIENT,DEST,consent);
  UUID id=id(created);
  assertEquals("DRAFT",body(created).get("status"));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   action(CREATOR,id,1,"authorize",null)).code);
  var auth=action(AUTHOR,id,1,"authorize",null);
  assertEquals("AUTHORIZED",body(auth).get("status"));
  assertEquals("stale_version",assertThrows(Problem.class,()->
   action(SENDER,id,1,"send",null)).code);
  action(SENDER,id,2,"send",null);
  assertEquals("evidence_unavailable",assertThrows(Problem.class,()->
   action(RECEIVER,id,3,"accept",UUID.randomUUID())).code);
  UUID wrong=proof(UUID.randomUUID(),"RECEIVING_DECISION",id,DEST);
  assertEquals("evidence_unavailable",assertThrows(Problem.class,()->
   action(RECEIVER,id,3,"accept",wrong)).code);
  UUID receive=proof(PATIENT,"RECEIVING_DECISION",id,DEST);
  var accepted=action(RECEIVER,id,3,"accept",receive);
  assertEquals("ACCEPTED",body(accepted).get("status"));
  UUID handover=proof(PATIENT,"HANDOVER_RECEIPT",id,DEST);
  var transfer=action(TRANSFER,id,4,"transfer",handover);
  assertEquals("TRANSFERRED",body(transfer).get("status"));
  UUID followup=proof(PATIENT,"FOLLOWUP_VERIFICATION",id,DEST);
  var attended=action(ATTEND,id,5,"attend",followup);
  assertEquals("ATTENDED",body(attended).get("status"));
  var closed=action(CLOSER,id,6,"close",null);
  assertEquals("CLOSED",body(closed).get("status"));
  assertEquals(7,store.transitions(actor(CREATOR,"m16:read"),id,25).size());
  assertEquals(7,admin.queryForObject("SELECT count(*) FROM synqoria_referral.audit_events",Integer.class));
  assertEquals(7,admin.queryForObject("SELECT count(*) FROM synqoria_referral.outbox",Integer.class));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_referral.referrals SET recipient_facility=? WHERE id=?",key(),id));
  assertThrows(DataAccessException.class,()->admin.update(
   "DELETE FROM synqoria_referral.transition_events WHERE referral_id=?",id));
 }
 @Test void wrongRecipientAndRevokedConsentFailClosed(){
  UUID wrong=proof(PATIENT,"PATIENT_CONSENT",null,UUID.randomUUID());
  assertEquals("evidence_unavailable",assertThrows(Problem.class,()->create(PATIENT,DEST,wrong)).code);
  UUID consent=proof(PATIENT,"PATIENT_CONSENT",null,DEST);
  Reply draft=create(PATIENT,DEST,consent);
  admin.update("UPDATE synqoria_referral.authority_proofs SET revoked_at=clock_timestamp() "+
    "WHERE proof_id=?",consent);
  assertEquals("evidence_unavailable",assertThrows(Problem.class,()->
   action(AUTHOR,id(draft),1,"authorize",null)).code);
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_referral.authority_proofs SET revoked_at=NULL WHERE proof_id=?",consent));
 }
 @Test void isolationIdempotencyAndDatabaseForgeryDenials(){
  UUID consent=proof(PATIENT,"PATIENT_CONSENT",null,DEST);
  UUID key=key();
  Request req=new Request(PATIENT,DEST,"ORTHOPEDIC_CONSULT","c".repeat(64),consent);
  Actor creator=actor(CREATOR,"m16:create");
  var first=store.create(creator,key,req);
  var replay=store.create(creator,key,req);
  assertEquals(id(first),id(replay));
  assertEquals("idempotency_collision",assertThrows(Problem.class,()->
   store.create(creator,key,new Request(PATIENT,DEST,"OTHER","c".repeat(64),consent))).code);
  var foreign=new Actor(CREATOR,CELL,UUID.randomUUID(),Set.of("m16:read"));
  assertTrue(store.list(foreign,10).isEmpty());
  assertEquals(0,new JdbcTemplate(ds("synqoria_referral_runtime","m16-ci-only"))
   .queryForObject("SELECT count(*) FROM synqoria_referral.referrals",Integer.class));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_referral.referrals SET status='AUTHORIZED',version=2,authorized_by=? WHERE id=?",
   AUTHOR,id(first)));
  assertEquals(6,admin.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n "+
    "ON n.oid=c.relnamespace WHERE n.nspname='synqoria_referral' AND c.relkind='r' "+
    "AND c.relrowsecurity AND c.relforcerowsecurity",Integer.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_referral_runtime','synqoria_referral.authority_proofs','INSERT')",Boolean.class));
 }
}
