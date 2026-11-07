package org.synqoria.integrationmanagement;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.integrationmanagement.IntegrationApplication.*;
import static org.synqoria.integrationmanagement.IntegrationStore.*;

class IntegrationPgTest{
 private static final UUID CELL=UUID.fromString("35000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("35000000-0000-4000-8000-000000000002");
 private static final UUID CREATOR=UUID.fromString("35000000-0000-4000-8000-000000000003");
 private static final UUID CHECKER=UUID.fromString("35000000-0000-4000-8000-000000000004");
 private static final UUID APPROVER=UUID.fromString("35000000-0000-4000-8000-000000000005");
 private static final UUID ISSUER=UUID.fromString("35000000-0000-4000-8000-000000000006");
 private IntegrationStore store;private JdbcTemplate admin;
 private static UUID key(){return UUID.randomUUID();}
 private Actor actor(UUID user,String perm){return new Actor(user,CELL,FAC,Set.of(perm));}
 private static Map<String,Object> body(Reply r){
  @SuppressWarnings("unchecked")Map<String,Object> x=(Map<String,Object>)r.body();return x;
 }
 private static UUID id(Reply r){return UUID.fromString((String)body(r).get("id"));}
 private DriverManagerDataSource ds(String user,String pass){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_M35_TEST_JDBC_URL"),user,pass);
 }
 @BeforeEach void setup()throws Exception{
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_M35_PG_TEST")));
  var root=ds(System.getenv("SYNQORIA_M35_TEST_ADMIN_USER"),
   System.getenv("SYNQORIA_M35_TEST_ADMIN_PASSWORD"));
  admin=new JdbcTemplate(root);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_integration_runtime') "+
   "THEN CREATE ROLE synqoria_integration_runtime LOGIN NOINHERIT NOSUPERUSER "+
   "NOBYPASSRLS NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD 'm35-ci-only';END IF;END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_integration CASCADE");
  try(Connection c=root.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("db","migration","V001__m35_partner_integration_control.sql")));
   s.execute(Files.readString(Path.of("db","migration","V002__m35_immutable_control_transitions.sql")));
  }
  var runtime=ds("synqoria_integration_runtime","m35-ci-only");
  store=new IntegrationStore(new JdbcTemplate(runtime),JsonMapper.builder().build(),
   new DataSourceTransactionManager(runtime),"synqoria_integration_runtime");
 }
 private UUID proof(String partner,String type,UUID externalEvent,String hash){
  UUID p=key();
  admin.update("INSERT INTO synqoria_integration.partner_proofs(proof_id,cell_id,facility_id,"+
   "partner_code,proof_type,source_event_id,source_payload_sha256,external_actor,issuer,"+
   "valid_from,valid_until) VALUES(?,?,?,?,?,?,?,?,?,"+
   "clock_timestamp()-interval '2 minutes',clock_timestamp()+interval '2 hours')",
   p,CELL,FAC,partner,type,externalEvent,hash,ISSUER,"synthetic-kyb-authority");
  return p;
 }
 private UUID enabledPartner(String code){
  UUID id=id(store.partner(actor(CREATOR,"m35:partner_create"),key(),new Partner(code,"HOSPITAL")));
  UUID att=proof(code,"PARTNER_KYB",null,null);
  store.partnerAction(actor(CHECKER,"m35:partner_verify"),key(),id,"verify",new Version(1,att));
  var result=store.partnerAction(actor(APPROVER,"m35:partner_enable"),key(),id,"enable",new Version(2,null));
  assertEquals("ENABLED",body(result).get("status"));
  return id;
 }
 @Test void partnerKYBMakerCheckerAndDbImmutableStates(){
  UUID id=id(store.partner(actor(CREATOR,"m35:partner_create"),key(),new Partner("CLINIC_A","HOSPITAL")));
  assertEquals("proof_unavailable",assertThrows(Problem.class,()->
   store.partnerAction(actor(CHECKER,"m35:partner_verify"),key(),id,"verify",
    new Version(1,key()))).code);
  UUID att=proof("CLINIC_A","PARTNER_KYB",null,null);
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.partnerAction(actor(CREATOR,"m35:partner_verify"),key(),id,"verify",
    new Version(1,att))).code);
  store.partnerAction(actor(CHECKER,"m35:partner_verify"),key(),id,"verify",new Version(1,att));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.partnerAction(actor(CHECKER,"m35:partner_enable"),key(),id,"enable",
    new Version(2,null))).code);
  store.partnerAction(actor(APPROVER,"m35:partner_enable"),key(),id,"enable",new Version(2,null));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_integration.partners SET partner_code='IMPERSONATOR' WHERE id=?",id));
  assertThrows(DataAccessException.class,()->admin.update(
   "DELETE FROM synqoria_integration.audit_events"));
  assertEquals(10,admin.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n "+
   "ON n.oid=c.relnamespace WHERE n.nspname='synqoria_integration' AND c.relkind='r' "+
   "AND c.relrowsecurity AND c.relforcerowsecurity",Integer.class));
 }
 @Test void apiAndAppsCannotIssueSecretsOrBypassIndependentApproval(){
  UUID partner=enabledPartner("CLINIC_B");
  UUID product=id(store.product(actor(CREATOR,"m35:api_create"),key(),
   new ApiProduct("FHIR_R4","V1","a".repeat(64))));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.publish(actor(CREATOR,"m35:api_publish"),key(),product,new Version(1,null))).code);
  store.publish(actor(CHECKER,"m35:api_publish"),key(),product,new Version(1,null));
  UUID app=id(store.application(actor(CREATOR,"m35:app_request"),key(),
   new DeveloperApp(partner,product,"EHR_CONNECTOR")));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.appAction(actor(CREATOR,"m35:app_review"),key(),app,"approve",new Version(1,null))).code);
  var approved=store.appAction(actor(CHECKER,"m35:app_review"),key(),app,"approve",new Version(1,null));
  assertEquals("APPROVED",body(approved).get("status"));
  assertEquals("NOT_ISSUED",admin.queryForObject(
   "SELECT credential_status FROM synqoria_integration.developer_apps WHERE id=?",String.class,app));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_integration.developer_apps SET credential_status='ISSUED' WHERE id=?",app));
 }
 @Test void mappingsRequireTwoIndependentReviews(){
  UUID map=id(store.mapping(actor(CREATOR,"m35:mapping_create"),key(),
   new Mapping("LAB_LIS_HL7","V1","b".repeat(64),"LIS","FHIR")));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.mappingAction(actor(CREATOR,"m35:mapping_validate"),key(),map,"validate",
    new Version(1,null))).code);
  store.mappingAction(actor(CHECKER,"m35:mapping_validate"),key(),map,"validate",new Version(1,null));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.mappingAction(actor(CHECKER,"m35:mapping_approve"),key(),map,"approve",
    new Version(2,null))).code);
  var accepted=store.mappingAction(actor(APPROVER,"m35:mapping_approve"),key(),map,"approve",
   new Version(2,null));
  assertEquals("APPROVED",body(accepted).get("status"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_integration.mapping_versions SET contract_sha256=repeat('f',64) WHERE id=?",map));
 }
 @Test void signedMessageQuarantineAndReplayApprovalNeverDispatch(){
  UUID partner=enabledPartner("LAB_X");
  UUID source=key();String digest="a".repeat(64);
  UUID att=proof("LAB_X","SOURCE_MESSAGE",source,digest);
  var created=store.ingest(actor(CREATOR,"m35:message_ingest"),key(),
   new Ingest(partner,source,"LAB_RESULT",digest,att));
  UUID message=id(created);
  assertEquals("QUARANTINED",body(created).get("status"));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.reviewMessage(actor(CREATOR,"m35:message_review"),key(),message,
    new Version(1,null))).code);
  UUID replay=id(store.replay(actor(CREATOR,"m35:replay_request"),key(),
   new Replay(message,"TRANSIENT_FAILURE")));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.reviewReplay(actor(CREATOR,"m35:replay_review"),key(),replay,
    new Decision(1,true))).code);
  var review=store.reviewReplay(actor(CHECKER,"m35:replay_review"),key(),replay,
    new Decision(1,true));
  assertEquals(Boolean.FALSE,body(review).get("dispatched"));
  assertEquals(Boolean.FALSE,body(review).get("credentialsIssued"));
  var accepted=store.reviewMessage(actor(CHECKER,"m35:message_review"),key(),message,
    new Version(1,null));
  assertEquals("ACCEPTED",body(accepted).get("status"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_integration.messages SET payload_sha256=repeat('f',64) WHERE id=?",message));
 }
 @Test void scopeAndIdempotentCommandEnvelope(){
  var a=actor(CREATOR,"m35:partner_create");
  UUID requestKey=key();
  var body=new Partner("INSURANCE_B","PAYER");
  var first=store.partner(a,requestKey,body);
  var replay=store.partner(a,requestKey,body);
  assertEquals(id(first),id(replay));
  assertEquals("idempotency_collision",assertThrows(Problem.class,()->
   store.partner(a,requestKey,new Partner("OTHER","PAYER"))).code);
  var outsider=new Actor(CREATOR,CELL,key(),Set.of("m35:read"));
  assertTrue(store.list(outsider,"partners",50).isEmpty());
  assertEquals(0,new JdbcTemplate(ds("synqoria_integration_runtime","m35-ci-only"))
   .queryForObject("SELECT count(*) FROM synqoria_integration.partners",Integer.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_integration_runtime','synqoria_integration.partner_proofs','INSERT')",Boolean.class));
 }
}
