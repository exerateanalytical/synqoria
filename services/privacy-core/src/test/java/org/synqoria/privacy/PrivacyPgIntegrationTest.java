package org.synqoria.privacy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.privacy.Rules.*;

/** Real PostgreSQL18.6 integration only; no hospital, patient or institutional proof is used. */
class PrivacyPgIntegrationTest {
 private static final UUID CELL=UUID.fromString("32000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("32000000-0000-4000-8000-000000000002");
 private static final UUID SUBJECT=UUID.fromString("32000000-0000-4000-8000-000000000003");
 private static final UUID CREATOR=UUID.fromString("32000000-0000-4000-8000-000000000004");
 private static final UUID CHECKER=UUID.fromString("32000000-0000-4000-8000-000000000005");
 private static final UUID APPROVER=UUID.fromString("32000000-0000-4000-8000-000000000006");
 private static final UUID ISSUER=UUID.fromString("32000000-0000-4000-8000-000000000007");
 private JdbcTemplate admin;
 private PrivacyStore store;
 private Actor create,verify,approve,revoke,read,request,review,privacyCase,reviewCase,policy;
 private DriverManagerDataSource ds(String user,String pass){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_PRIVACY_TEST_JDBC_URL"),user,pass);
 }
 private static UUID key(){return UUID.randomUUID();}
 private static Map<String,Object> body(PrivacyStore.Reply r){
  @SuppressWarnings("unchecked")Map<String,Object> value=(Map<String,Object>)r.body();
  return value;
 }
 private static UUID rid(PrivacyStore.Reply r){return UUID.fromString((String)body(r).get("id"));}
 private static long ver(PrivacyStore.Reply r){return ((Number)body(r).get("version")).longValue();}
 private Actor a(UUID actor,String permission){return new Actor(actor,CELL,FAC,Set.of(permission));}
 @BeforeEach void setup()throws Exception{
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_PRIVACY_PG_TEST")));
  String user=System.getenv("SYNQORIA_PRIVACY_TEST_ADMIN_USER");
  String pass=System.getenv("SYNQORIA_PRIVACY_TEST_ADMIN_PASSWORD");
  var administrator=ds(user,pass);
  admin=new JdbcTemplate(administrator);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_privacy_runtime') "+
   "THEN CREATE ROLE synqoria_privacy_runtime LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS "+
   "NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD 'm32-ci-only';END IF;END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_privacy CASCADE");
  try(Connection c=administrator.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("db","migration","V001__m32_consent_privacy_governance.sql")));
  }
  var runtime=ds("synqoria_privacy_runtime","m32-ci-only");
  store=new PrivacyStore(new JdbcTemplate(runtime),JsonMapper.builder().build(),
   new DataSourceTransactionManager(runtime),Clock.systemUTC(),"synqoria_privacy_runtime");
  create=a(CREATOR,"m32:directive_create");
  verify=a(CHECKER,"m32:directive_verify");
  approve=a(APPROVER,"m32:directive_activate");
  revoke=a(CHECKER,"m32:directive_revoke");
  read=a(CREATOR,"m32:read");
  request=a(CREATOR,"m32:disclosure_request");
  review=a(CHECKER,"m32:disclosure_review");
  privacyCase=a(CREATOR,"m32:case_create");
  reviewCase=a(CHECKER,"m32:case_review");
  policy=a(APPROVER,"m32:policy_evaluate");
 }
 private UUID proof(UUID subject,String purpose,String recipient){
  UUID id=key();
  admin.update("INSERT INTO synqoria_privacy.authority_evidence"+
   "(proof_id,cell_id,facility_id,subject_ref,purpose,recipient,operation,source_ref,issuer,"+
   "approved_by,valid_from,valid_until,digest_sha256) "+
   "VALUES(?,?,?,?,? ,?,'READ','SYNTHETIC-M32-ATTESTATION','fixture-authority',?,"+
   "clock_timestamp()-interval '5 minutes',clock_timestamp()+interval '2 hours',repeat('a',64))",
   id,CELL,FAC,subject,purpose,recipient,ISSUER);
  return id;
 }
 private PrivacyStore.Reply draft(){
  return store.create(create,key(),new Directive(SUBJECT,"CARE","HOSPITAL","READ",
   Instant.now().minusSeconds(30),Instant.now().plusSeconds(3600)));
 }
 private PrivacyStore.Reply authorized(){
  var initial=draft();
  var verified=store.verify(verify,key(),rid(initial),
   new Verification(1,proof(SUBJECT,"CARE","HOSPITAL")));
  return store.activate(approve,key(),rid(verified),new Version(ver(verified)));
 }
 @Test void actualDatabaseEnforcesScopesProofsIndependenceAndRevocation(){
  var draft=draft();UUID id=rid(draft);
  assertEquals("DRAFT",body(draft).get("status"));
  assertFalse((Boolean)store.evaluate(policy,SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  assertEquals("authority_evidence_required",
    assertThrows(Problem.class,()->store.verify(verify,key(),id,new Verification(1,key()))).code());
  UUID wrong=proof(SUBJECT,"CARE","OTHER_RECIPIENT");
  assertEquals("authority_evidence_required",
    assertThrows(Problem.class,()->store.verify(verify,key(),id,new Verification(1,wrong))).code());
  UUID correct=proof(SUBJECT,"CARE","HOSPITAL");
  assertEquals("independent_checker_required",
    assertThrows(Problem.class,()->store.verify(a(CREATOR,"m32:directive_verify"),
     key(),id,new Verification(1,correct))).code());
  var v=store.verify(verify,key(),id,new Verification(1,correct));
  assertEquals("VERIFIED",body(v).get("status"));
  assertEquals("independent_checker_required",
   assertThrows(Problem.class,()->store.activate(a(CHECKER,"m32:directive_activate"),
    key(),id,new Version(2))).code());
  var active=store.activate(approve,key(),id,new Version(2));
  assertEquals("ACTIVE",body(active).get("status"));
  assertTrue((Boolean)store.evaluate(policy,SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  assertFalse((Boolean)store.evaluate(policy,SUBJECT,"CARE","UNRELATED","READ").get("allowed"));
  assertFalse((Boolean)store.evaluate(
   new Actor(APPROVER,CELL,UUID.randomUUID(),Set.of("m32:policy_evaluate")),
   SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  assertEquals("stale_version",
   assertThrows(Problem.class,()->store.revoke(revoke,key(),id,new Version(2))).code());
  var off=store.revoke(revoke,key(),id,new Version(3));
  assertEquals("REVOKED",body(off).get("status"));
  assertFalse((Boolean)store.evaluate(policy,SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  assertEquals("invalid_transition",
   assertThrows(Problem.class,()->store.activate(approve,key(),id,new Version(4))).code());
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_privacy.directives SET subject_ref=? WHERE id=?",UUID.randomUUID(),id));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_privacy.authority_evidence SET subject_ref=? WHERE proof_id=?",UUID.randomUUID(),correct));
  assertThrows(DataAccessException.class,()->admin.update(
   "DELETE FROM synqoria_privacy.authority_evidence WHERE proof_id=?",correct));
  assertEquals(4,admin.queryForObject("SELECT count(*) FROM synqoria_privacy.audit_events",Integer.class));
  assertEquals(4,admin.queryForObject("SELECT count(*) FROM synqoria_privacy.outbox",Integer.class));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_privacy.audit_events SET action='REWRITE'"));
  assertEquals(7,admin.queryForObject(
   "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "+
   "WHERE n.nspname='synqoria_privacy' AND c.relkind='r' "+
   "AND c.relrowsecurity AND c.relforcerowsecurity",Integer.class));
 }
 @Test void disclosureNeedsCurrentConsentAndSeparateReviewer(){
  var granted=authorized();UUID consent=rid(granted);
  var requestBody=new Disclosure(SUBJECT,consent,"CARE","HOSPITAL","READ","CLINICAL_SUMMARY");
  var requested=store.requestDisclosure(request,key(),requestBody);
  assertEquals("PENDING",body(requested).get("status"));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.reviewDisclosure(a(CREATOR,"m32:disclosure_review"),key(),rid(requested),
    new Decision(1,true,"AUTHORIZED"))).code());
  var reviewed=store.reviewDisclosure(review,key(),rid(requested),new Decision(1,true,"AUTHORIZED"));
  assertEquals("AUTHORIZED",body(reviewed).get("status"));
  store.revoke(revoke,key(),consent,new Version(3));
  assertEquals("consent_not_active",assertThrows(Problem.class,()->
   store.requestDisclosure(request,key(),requestBody)).code());
  assertEquals("invalid_transition",assertThrows(Problem.class,()->
   store.reviewDisclosure(review,key(),rid(requested),new Decision(2,true,"AUTHORIZED"))).code());
  // Authorization is M32 policy evidence only; no clinical export is ever performed here.
  assertEquals(1,store.listing(read,"disclosures",10).size());
 }
 @Test void revokingIndependentEvidenceFailsClosedWithoutRewritingConsent(){
  var active=authorized();UUID id=rid(active);
  UUID p=admin.queryForObject("SELECT proof_id FROM synqoria_privacy.directives WHERE id=?",UUID.class,id);
  assertTrue((Boolean)store.evaluate(policy,SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  admin.update("UPDATE synqoria_privacy.authority_evidence SET revoked_at=clock_timestamp() WHERE proof_id=?",p);
  assertFalse((Boolean)store.evaluate(policy,SUBJECT,"CARE","HOSPITAL","READ").get("allowed"));
  assertEquals("consent_not_active",assertThrows(Problem.class,()->
   store.requestDisclosure(request,key(),new Disclosure(SUBJECT,id,"CARE","HOSPITAL","READ","SUMMARY"))).code());
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_privacy.authority_evidence SET revoked_at=NULL WHERE proof_id=?",p));
 }
 @Test void privacyCaseRequiresIndependentClosureAndIsImmutable(){
  var created=store.openCase(privacyCase,key(),new PrivacyCase(SUBJECT,"COMPLAINT","PRIVACY_REVIEW"));
  UUID id=rid(created);
  assertEquals("OPEN",body(created).get("status"));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.closeCase(a(CREATOR,"m32:case_review"),key(),id,new Decision(1,true,"RESOLVED"))).code());
  var closed=store.closeCase(reviewCase,key(),id,new Decision(1,true,"RESOLVED"));
  assertEquals("CLOSED",body(closed).get("status"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_privacy.privacy_cases SET reason='ALTERED' WHERE id=?",id));
  assertEquals("invalid_transition",assertThrows(Problem.class,()->
   store.closeCase(reviewCase,key(),id,new Decision(2,true,"RESOLVED"))).code());
 }
 @Test void commandIdempotencyAndCrossFacilityCannotLeak(){
  UUID cmd=key();
  var request=new Directive(SUBJECT,"CARE","HOSPITAL","READ",
   Instant.now().minusSeconds(15),Instant.now().plusSeconds(3600));
  var first=store.create(create,cmd,request);
  var replay=store.create(create,cmd,request);
  assertEquals(rid(first).toString(),String.valueOf(body(replay).get("id")));
  assertEquals(1,store.listing(read,"directives",20).size());
  assertEquals("idempotency_collision",
   assertThrows(Problem.class,()->store.create(create,cmd,new Directive(SUBJECT,
    "RESEARCH","HOSPITAL","READ",request.effectiveAt(),request.expiresAt()))).code());
  var other=new Actor(CREATOR,CELL,UUID.randomUUID(),Set.of("m32:read"));
  assertTrue(store.listing(other,"directives",20).isEmpty());
  var rds=ds("synqoria_privacy_runtime","m32-ci-only");
  assertEquals(0,new JdbcTemplate(rds).queryForObject(
   "SELECT count(*) FROM synqoria_privacy.directives",Integer.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_privacy_runtime','synqoria_privacy.authority_evidence','INSERT')",Boolean.class));
 }
}
