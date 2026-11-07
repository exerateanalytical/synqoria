package org.synqoria.auditcore;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.auditcore.Rules.*;

/** Synthetic source attestations only. No clinical records or hospital signers. */
class AuditPgIntegrationTest {
 private static final UUID CELL=UUID.fromString("34000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("34000000-0000-4000-8000-000000000002");
 private static final UUID SOURCE=UUID.fromString("34000000-0000-4000-8000-000000000003");
 private static final UUID INGESTOR=UUID.fromString("34000000-0000-4000-8000-000000000004");
 private static final UUID REVIEWER=UUID.fromString("34000000-0000-4000-8000-000000000005");
 private static final UUID ISSUER=UUID.fromString("34000000-0000-4000-8000-000000000006");
 private static final UUID SUBJECT=UUID.fromString("34000000-0000-4000-8000-000000000007");
 private JdbcTemplate admin;private AuditStore store;
 private Actor ingest,read,search,timeline,request,review,manifest,requestRetention,reviewRetention,holds;
 private static UUID key(){return UUID.randomUUID();}
 private DriverManagerDataSource ds(String user,String password){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_AUDIT_TEST_JDBC_URL"),user,password);
 }
 private Actor actor(UUID id,String permission){return new Actor(id,CELL,FAC,Set.of(permission));}
 private static Map<String,Object> body(AuditStore.Reply r){
  @SuppressWarnings("unchecked")Map<String,Object> b=(Map<String,Object>)r.body();return b;
 }
 private static UUID id(AuditStore.Reply r){return UUID.fromString((String)body(r).get("id"));}
 private Ingest event(UUID stream,long seq){
  return new Ingest(stream,SOURCE,key(),INGESTOR,SUBJECT,seq,"CLINICAL_NOTE_SIGNED","SUCCESS",
   "a".repeat(64),key(),Instant.now().truncatedTo(ChronoUnit.MICROS));
 }
 @BeforeEach void setup() throws Exception {
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_AUDIT_PG_TEST")));
  var root=ds(System.getenv("SYNQORIA_AUDIT_TEST_ADMIN_USER"),
   System.getenv("SYNQORIA_AUDIT_TEST_ADMIN_PASSWORD"));
  admin=new JdbcTemplate(root);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_audit_runtime') "+
   "THEN CREATE ROLE synqoria_audit_runtime LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS "+
   "NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD 'm34-ci-only';END IF;END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_audit CASCADE");
  try(Connection c=root.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("db","migration","V001__m34_audit_provenance_evidence.sql")));
   s.execute(Files.readString(Path.of("db","migration","V002__m34_attested_envelope_and_chain_guards.sql")));
  }
  var runtime=ds("synqoria_audit_runtime","m34-ci-only");
  store=new AuditStore(new JdbcTemplate(runtime),JsonMapper.builder().build(),
   new DataSourceTransactionManager(runtime),Clock.systemUTC(),"synqoria_audit_runtime");
  ingest=actor(INGESTOR,"m34:ingest");read=actor(INGESTOR,"m34:read");
  search=actor(INGESTOR,"m34:search");timeline=actor(INGESTOR,"m34:timeline");
  request=actor(INGESTOR,"m34:export_request");review=actor(REVIEWER,"m34:export_review");
  manifest=actor(REVIEWER,"m34:manifest_read");
  requestRetention=actor(INGESTOR,"m34:retention_request");
  reviewRetention=actor(REVIEWER,"m34:retention_review");holds=actor(REVIEWER,"m34:hold_read");
 }
 private void proof(Ingest e){
  admin.update("INSERT INTO synqoria_audit.ingest_attestations(proof_id,cell_id,facility_id,"+
   "source_id,source_event_id,stream_id,sequence,payload_sha256,issuer,approved_by,"+
   "valid_from,valid_until,source_actor,subject_ref,event_action,event_outcome,occurred_at,envelope_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?,"+
   "clock_timestamp()-interval '5 minutes',clock_timestamp()+interval '2 hours',?,?,?,?,?,?)",
   e.proofId(),CELL,FAC,e.sourceId(),e.sourceEventId(),e.streamId(),e.sequence(),
   e.payloadSha256(),"synthetic-audit-issuer",ISSUER,e.sourceActor(),e.subjectRef(),
   e.action(),e.outcome(),Timestamp.from(e.occurredAt()),AuditStore.envelope(e));
 }
 private AuditStore.Reply accepted(Ingest e){
  proof(e);
  return store.ingest(ingest,key(),e);
 }
 @Test void exactAttestationAndAppendOnlyHashChainWithForcedRls(){
  UUID stream=key();var event=event(stream,1);
  assertEquals("audit_attestation_required",assertThrows(Problem.class,()->
   store.ingest(ingest,key(),event)).code());
  proof(event);
  UUID cmd=key();
  var first=store.ingest(ingest,cmd,event),again=store.ingest(ingest,cmd,event);
  assertEquals(id(first).toString(),String.valueOf(body(again).get("id")));
  assertTrue(Boolean.TRUE.equals(store.verify(timeline,stream).get("verified")));
  assertEquals(1L,store.verify(timeline,stream).get("count"));
  assertEquals(1,store.list(search,"evidence_events",10).size());
  assertEquals(1L,store.timeline(timeline,stream,10).size());
  assertNotNull(store.detail(read,id(first)).get("event_sha256"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.evidence_events SET action='REWRITE' WHERE id=?",id(first)));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.stream_heads SET last_hash=? WHERE stream_id=?","f".repeat(64),stream));
  assertThrows(DataAccessException.class,()->admin.update(
   "DELETE FROM synqoria_audit.evidence_events WHERE id=?",id(first)));
  assertEquals(9,admin.queryForObject(
   "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "+
   "WHERE n.nspname='synqoria_audit' AND c.relkind='r' "+
   "AND c.relrowsecurity AND c.relforcerowsecurity",Integer.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_audit_runtime','synqoria_audit.ingest_attestations','UPDATE')",Boolean.class));
  assertFalse(admin.queryForObject("SELECT has_table_privilege("+
   "'synqoria_audit_runtime','synqoria_audit.evidence_events','DELETE')",Boolean.class));
  assertEquals(0,new JdbcTemplate(ds("synqoria_audit_runtime","m34-ci-only"))
   .queryForObject("SELECT count(*) FROM synqoria_audit.evidence_events",Integer.class));
  var foreign=new Actor(INGESTOR,CELL,UUID.randomUUID(),Set.of("m34:search"));
  assertTrue(store.list(foreign,"evidence_events",10).isEmpty());
 }
 @Test void sourceEventAttestationCannotBeRetargetedAndOrderedStreamIsRequired(){
  UUID stream=key();var first=event(stream,1);
  proof(first);
  var forged=new Ingest(stream,first.sourceId(),first.sourceEventId(),
   first.sourceActor(),first.subjectRef(),1,"DELETE_DOCUMENT",first.outcome(),
   first.payloadSha256(),first.proofId(),first.occurredAt());
  assertEquals("audit_attestation_required",assertThrows(Problem.class,()->
   store.ingest(ingest,key(),forged)).code());
  accepted(event(stream,1));
  Ingest outOfOrder=event(stream,3);proof(outOfOrder);
  assertEquals("stale_source_sequence",assertThrows(Problem.class,()->
   store.ingest(ingest,key(),outOfOrder)).code());
  accepted(event(stream,2));
  assertTrue(Boolean.TRUE.equals(store.verify(timeline,stream).get("verified")));
  assertEquals(2L,store.verify(timeline,stream).get("count"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.ingest_attestations SET source_actor=? WHERE proof_id=?",
   REVIEWER,first.proofId()));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.ingest_attestations SET revoked_at=clock_timestamp() "+
   "WHERE proof_id=?",first.proofId()));
 }
 @Test void independentExportReviewOnlyProducesNonclinicalIntegrityManifest(){
  UUID stream=key();accepted(event(stream,1));
  var r=store.requestExport(request,key(),new ExportRequest(stream,"AUDIT_REVIEW","REGULATOR",
   1,1,Instant.now().plusSeconds(3600)));
  assertEquals("PROPOSED",body(r).get("status"));
  assertEquals("independent_checker_required",assertThrows(Problem.class,()->
   store.reviewExport(actor(INGESTOR,"m34:export_review"),key(),id(r),
    new Decision(1,true,"AUTHORIZED"))).code());
  var approved=store.reviewExport(review,key(),id(r),new Decision(1,true,"AUTHORIZED"));
  assertEquals("APPROVED",body(approved).get("status"));
  assertEquals(Boolean.FALSE,body(approved).get("clinicalDataExported"));
  var manifestBody=store.manifest(manifest,id(r));
  assertEquals(false,manifestBody.get("clinicalDataExported"));
  assertEquals("NOT_CONNECTED",manifestBody.get("signedObjectStatus"));
  assertEquals(1,store.list(manifest,"export_requests",10).size());
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.export_requests SET recipient='OTHER' WHERE id=?",id(r)));
 }
 @Test void legalHoldBlocksRetentionReviewAndNoDisposalOccurs(){
  UUID stream=key();accepted(event(stream,1));
  UUID hold=key();
  admin.update("INSERT INTO synqoria_audit.retention_holds(hold_id,cell_id,facility_id,"+
   "stream_id,reason,authority_ref,ordered_by) VALUES(?,?,?,?,?,?,?)",
   hold,CELL,FAC,stream,"LITIGATION","SIGNED-COURT-EVIDENCE",ISSUER);
  assertEquals(1,store.list(holds,"retention_holds",10).size());
  var request=store.requestRetention(requestRetention,key(),
   new RetentionReview(stream,"COUNTRY_POLICY","SCHEDULED_ELIGIBILITY"));
  assertEquals("active_legal_hold",assertThrows(Problem.class,()->
   store.reviewRetention(reviewRetention,key(),id(request),new Decision(1,true,"APPROVED"))).code());
  admin.update("UPDATE synqoria_audit.retention_holds SET rescinded_at=clock_timestamp() WHERE hold_id=?",hold);
  var approved=store.reviewRetention(reviewRetention,key(),id(request),new Decision(1,true,"APPROVED"));
  assertEquals(Boolean.FALSE,body(approved).get("recordsDisposed"));
  assertThrows(DataAccessException.class,()->admin.update(
   "UPDATE synqoria_audit.retention_holds SET rescinded_at=NULL WHERE hold_id=?",hold));
  assertThrows(DataAccessException.class,()->admin.update(
   "DELETE FROM synqoria_audit.retention_holds WHERE hold_id=?",hold));
  assertEquals(admin.queryForObject("SELECT count(*) FROM synqoria_audit.audit_actions",Integer.class),
   admin.queryForObject("SELECT count(*) FROM synqoria_audit.outbox",Integer.class));
 }
}
