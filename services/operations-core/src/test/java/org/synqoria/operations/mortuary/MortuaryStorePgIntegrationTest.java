package org.synqoria.operations.mortuary;

import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.operations.mortuary.MortuaryModels.*;

/** Independent synthetic authority data; real PG18.6 role, transaction and RLS tests. */
final class MortuaryStorePgIntegrationTest {
 private static final UUID CELL=UUID.fromString("27000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("27000000-0000-4000-8000-000000000002");
 private static final UUID INTAKE=UUID.fromString("27000000-0000-4000-8000-000000000003");
 private static final UUID OFFICER=UUID.fromString("27000000-0000-4000-8000-000000000004");
 private static final UUID RECEIVER=UUID.fromString("27000000-0000-4000-8000-000000000005");
 private static final UUID REVIEWER=UUID.fromString("27000000-0000-4000-8000-000000000006");
 private static final UUID APPROVER=UUID.fromString("27000000-0000-4000-8000-000000000007");
 private static final UUID HANDOVER=UUID.fromString("27000000-0000-4000-8000-000000000008");
 private static final UUID AUTHORITY=UUID.fromString("27000000-0000-4000-8000-000000000009");
 private static final UUID ISSUER=UUID.fromString("27000000-0000-4000-8000-000000000010");
 private static final UUID CROSS=UUID.fromString("27000000-0000-4000-8000-000000000011");
 private static final String DEATH="DEATH-ATTEST-M27";
 private static final String INTAKE_REF="INCOMING-ATTEST-M27";
 private DataSource runtime(){
  return new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),
    System.getenv("SYNQORIA_OPS_TEST_DB_USER"),System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));
 }
 private JdbcTemplate admin(){
  return new JdbcTemplate(new DriverManagerDataSource(
   System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),
   System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),
   System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD")));
 }
 private MortuaryStore store(){
  var ds=runtime();
  return new MortuaryStore(new JdbcTemplate(ds),new DataSourceTransactionManager(ds),
   JsonMapper.builder().build(),"synqoria_operations_runtime");
 }
 private OperationWorkflow.Actor who(UUID id,String...permissions){
  return new OperationWorkflow.Actor(id,CELL,FAC,Set.of(permissions));
 }
 private static void pgOnly(){
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
 }
 private void evidence(UUID subject,UUID caseId,String action,long version,
                        String kind,String ref,UUID approver){
  admin().update("INSERT INTO synqoria_operations.mortuary_authority_evidence " +
   "(proof_id,cell_id,facility_id,decedent_ref,case_id,for_action,expected_version,"+
   "proof_kind,external_ref,authority_issuer,approved_by,valid_from,valid_until,digest_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?,?,clock_timestamp()-interval '5 minutes',"+
   "clock_timestamp()+interval '2 hours',repeat('a',64))",
   UUID.randomUUID(),CELL,FAC,subject,caseId,action,version,kind,ref,"synthetic-authority-ci",approver);
 }
 private void incoming(UUID subject){
  roster();
  evidence(subject,null,"RECEIVE",0,"DEATH_CONFIRMED",DEATH,ISSUER);
  evidence(subject,null,"RECEIVE",0,"IDENTITY_ATTESTED",INTAKE_REF,ISSUER);
  evidence(subject,null,"RECEIVE",0,"INTAKE_AUTHORIZED","INTAKE-AUTH-M27",ISSUER);
  evidence(subject,null,"RECEIVE",0,"BELONGINGS_SEALED","BELONG-SEAL-M27",ISSUER);
 }
 private CaseView receive(MortuaryStore s,UUID subject){
  incoming(subject);
  return s.receive(who(INTAKE,"m27:intake"),
   new Intake(subject,INTAKE_REF,DEATH,"UNIT-ICU","INTAKE-AUTH-M27","BELONG-SEAL-M27",RECEIVER,UUID.randomUUID()));
 }
 private CaseView identify(MortuaryStore s,CaseView c){
  evidence(c.decedentRef(),c.caseId(),"IDENTIFY",c.version(),
   "IDENTITY_VERIFIED","ID-VERIFIED-M27",ISSUER);
  return s.change(who(OFFICER,"m27:identify"),c.caseId(),
   cmd("IDENTIFY",c.version(),"ID-VERIFIED-M27",null,null,null,null));
 }
 private void roster(){
  // Only independent workforce authority could provision this in production.
  admin().update("INSERT INTO synqoria_operations.workforce_grants "+
   "(cell_id,facility_id,actor_id,grant_revision,permissions,active,"+
   "shift_from,shift_until,valid_until,approved_by,source_ref) "+
   "VALUES(?,?,?,1,ARRAY['m27:custody_receive']::text[],true,"+
   "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 hour',"+
   "clock_timestamp()+interval '2 hours',?,'signed-workforce-m27-ci') "+
   "ON CONFLICT(cell_id,facility_id,actor_id) DO UPDATE SET active=true,"+
   "shift_from=clock_timestamp()-interval '1 hour',shift_until=clock_timestamp()+interval '1 hour',"+
   "valid_until=clock_timestamp()+interval '2 hours'",
   CELL,FAC,RECEIVER,AUTHORITY);
 }
 private CaseView store(MortuaryStore s,CaseView c){
  roster();
  evidence(c.decedentRef(),c.caseId(),"STORE",c.version(),"STORAGE_SAFE","SAFE-BAY-M27",ISSUER);
  String assignedBay="BAY-"+c.caseId().toString().substring(0,8);
  return s.change(who(OFFICER,"m27:store"),c.caseId(),cmd("STORE",c.version(),
   "SAFE-BAY-M27",assignedBay,RECEIVER,null,null));
 }
 private Change cmd(String action,long v,String proof,String bay,UUID receive,
                    String recipient,String authority){
  String seal=Set.of("STORE","TRANSFER").contains(action)?"CASE-SEAL-M27":null;
  String condition=Set.of("STORE","TRANSFER").contains(action)?"SEALED_OK":null;
  UUID witness=action.equals("HANDOVER")?RECEIVER:null;
  String belongings=action.equals("HANDOVER")?"BELONGINGS-RELEASE-M27":null;
  return new Change(action,v,UUID.randomUUID(),proof,bay,receive,recipient,authority,
   seal,condition,witness,belongings);
 }
 private long rows(String table,UUID id){
  final Set<String> allowed=Set.of("mortuary_custody_events","mortuary_release_reviews",
   "mortuary_audit_events","mortuary_outbox","mortuary_cases");
  if(!allowed.contains(table))throw new IllegalArgumentException("invalid table");
  return admin().queryForObject(
   "SELECT count(*) FROM synqoria_operations."+table+" WHERE case_id=?",Long.class,id);
 }
 @Test void cannotIntakeWithoutIndependentDeathAndIdentityProofOrRewriteRecords(){
  pgOnly();
  var s=store();
  UUID subject=UUID.randomUUID();
  var input=new Intake(subject,INTAKE_REF,DEATH,"UNIT-ICU","INTAKE-AUTH-M27","BELONG-SEAL-M27",RECEIVER,UUID.randomUUID());
  assertThrows(WorkflowViolation.class,()->s.receive(who(INTAKE,"m27:intake"),input));
  incoming(subject);
  var accepted=s.receive(who(INTAKE,"m27:intake"),input);
  assertEquals("RECEIVED",accepted.state());
  assertEquals(1,accepted.version());
  assertEquals(accepted.caseId(),s.receive(who(INTAKE,"m27:intake"),input).caseId());
  assertThrows(WorkflowViolation.class,()->s.receive(who(INTAKE,"m27:intake"),
   new Intake(subject,INTAKE_REF,"CHANGED-DEATH","UNIT-ICU","INTAKE-AUTH-M27","BELONG-SEAL-M27",RECEIVER,input.commandId())));
  assertThrows(Exception.class,()->new JdbcTemplate(runtime()).update(
   "DELETE FROM synqoria_operations.mortuary_custody_events WHERE case_id=?",accepted.caseId()));
  assertThrows(Exception.class,()->new JdbcTemplate(runtime()).update(
   "UPDATE synqoria_operations.mortuary_authority_evidence SET revoked_at=now() "+
   "WHERE decedent_ref=?",subject));
  assertEquals(1,rows("mortuary_custody_events",accepted.caseId()));
  var cross=new OperationWorkflow.Actor(INTAKE,CELL,CROSS,Set.of("m27:read"));
  assertThrows(WorkflowViolation.class,()->s.one(cross,accepted.caseId()));
 }
 @Test void completeM27ReleaseRequiresCustodyTwoApproversAndIndependentHandover(){
  pgOnly();
  var s=store();var c=receive(s,UUID.randomUUID());
  final UUID id=c.caseId(),subject=c.decedentRef();
  c=identify(s,c);
  assertEquals("IDENTIFIED",c.state());
  c=store(s,c);
  assertEquals("STORED",c.state());
  assertEquals("BAY-"+id.toString().substring(0,8),c.currentStorage());
  var invalidTransfer=cmd("TRANSFER",c.version(),"ACCEPT-M27",c.currentStorage(),RECEIVER,null,null);
  assertThrows(WorkflowViolation.class,()->s.change(who(OFFICER,"m27:transfer"),id,invalidTransfer));
  evidence(subject,id,"TRANSFER",c.version(),"CUSTODY_ACCEPTED","ACCEPT-M27",ISSUER);
  c=s.change(who(OFFICER,"m27:transfer"),id,
   cmd("TRANSFER",c.version(),"ACCEPT-M27","BAY-02",RECEIVER,null,null));
  assertEquals("BAY-02",c.currentStorage());
  final String recipient="RECIPIENT-VERIFY-M27",legal="RELEASE-AUTH-M27";
  var makerAttempt=cmd("REVIEW_RELEASE",c.version(),null,null,null,recipient,legal);
  assertThrows(WorkflowViolation.class,()->s.change(who(INTAKE,"m27:review"),id,makerAttempt));
  evidence(subject,id,"REVIEW_RELEASE",c.version(),"LEGAL_RELEASE",legal,ISSUER);
  evidence(subject,id,"REVIEW_RELEASE",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  c=s.change(who(REVIEWER,"m27:review"),id,
   cmd("REVIEW_RELEASE",c.version(),null,null,null,recipient,legal));
  assertEquals("RELEASE_REVIEWED",c.state());
  var sameReviewerAttempt=cmd("AUTHORIZE_RELEASE",c.version(),null,null,null,recipient,legal);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:authorize"),id,
   sameReviewerAttempt));
  evidence(subject,id,"AUTHORIZE_RELEASE",c.version(),"RELEASE_AUTHORIZATION",legal,ISSUER);
  evidence(subject,id,"AUTHORIZE_RELEASE",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  c=s.change(who(APPROVER,"m27:authorize"),id,
   cmd("AUTHORIZE_RELEASE",c.version(),null,null,null,recipient,legal));
  assertEquals("RELEASE_AUTHORIZED",c.state());
  evidence(subject,id,"HANDOVER",c.version(),"HANDOVER_RECEIPT","HANDOVER-M27",ISSUER);
  evidence(subject,id,"HANDOVER",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  evidence(subject,id,"HANDOVER",c.version(),"BELONGINGS_RELEASED","BELONGINGS-RELEASE-M27",ISSUER);
  var sameReviewerHandover=cmd("HANDOVER",c.version(),"HANDOVER-M27",null,null,recipient,legal);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:handover"),id,
   sameReviewerHandover));
  c=s.change(who(HANDOVER,"m27:handover"),id,
   cmd("HANDOVER",c.version(),"HANDOVER-M27",null,null,recipient,legal));
  assertEquals("RELEASED",c.state());
  // External legal authorities may not retroactively activate a hold on a released body.
  final UUID completedCase=id;
  assertThrows(Exception.class,()->admin().update(
   "INSERT INTO synqoria_operations.mortuary_legal_holds "+
   "(hold_id,cell_id,facility_id,case_id,authority_ref,reason_code,ordered_by) "+
   "VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),CELL,FAC,completedCase,
   "RETROACTIVE-HOLD-DENIED","COURT_REVIEW",AUTHORITY));
  assertNotNull(c.releasedAt());
  assertNull(c.currentStorage());
  assertEquals(4,rows("mortuary_custody_events",id));
  assertEquals(2,rows("mortuary_release_reviews",id));
  assertEquals(7,rows("mortuary_audit_events",id));
  assertEquals(7,rows("mortuary_outbox",id));
  assertEquals(4,s.custodyHistory(who(OFFICER,"m27:read"),id).size());
  assertEquals(2,s.approvals(who(REVIEWER,"m27:review"),id).size());
  assertEquals(7,s.audit(who(APPROVER,"m27:audit"),id).size());
  long finalizedVersion=c.version();
  assertThrows(WorkflowViolation.class,()->s.change(who(HANDOVER,"m27:handover"),id,
   cmd("HANDOVER",finalizedVersion,null,null,null,recipient,legal)));
 }
 @Test void legalHoldAndStaleVersionBlockReleaseAndCustodyCannotBeSelfReceived(){
  pgOnly();var s=store();var c=store(s,identify(s,receive(s,UUID.randomUUID())));
  var immutable=c;
  final UUID heldCase=c.caseId();
  final long heldVersion=c.version();
  assertThrows(WorkflowViolation.class,()->s.change(who(OFFICER,"m27:transfer"),heldCase,
   cmd("TRANSFER",heldVersion,"CUSTODY","BAY-02",OFFICER,null,null)));
  UUID hold=UUID.randomUUID();
  admin().update("INSERT INTO synqoria_operations.mortuary_legal_holds "+
   "(hold_id,cell_id,facility_id,case_id,authority_ref,reason_code,ordered_by) "+
   "VALUES(?,?,?,?,?,?,?)",hold,CELL,FAC,c.caseId(),"HOLD-M27","INVESTIGATION",AUTHORITY);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:review"),heldCase,
   cmd("REVIEW_RELEASE",heldVersion,null,null,null,"RECIPIENT-M27","LEGAL-M27")));
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),"LEGAL_RELEASE","LEGAL-M27",ISSUER);
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),"RECIPIENT_VERIFIED","RECIPIENT-M27",ISSUER);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:review"),heldCase,
   cmd("REVIEW_RELEASE",heldVersion,null,null,null,"RECIPIENT-M27","LEGAL-M27")));
  admin().update("UPDATE synqoria_operations.mortuary_legal_holds SET active=false,"+
   "rescinded_at=clock_timestamp() WHERE hold_id=?",hold);
  c=s.change(who(REVIEWER,"m27:review"),c.caseId(),
   cmd("REVIEW_RELEASE",c.version(),null,null,null,"RECIPIENT-M27","LEGAL-M27"));
  assertEquals("RELEASE_REVIEWED",c.state());
  assertThrows(WorkflowViolation.class,()->s.change(who(APPROVER,"m27:authorize"),heldCase,
   cmd("AUTHORIZE_RELEASE",immutable.version(),null,null,null,"RECIPIENT-M27","LEGAL-M27")));
 }
 @Test void exclusivePhysicalBayAndTransactionRollbackPreventsDoubleCustody(){
  pgOnly();var s=store();
  var first=store(s,identify(s,receive(s,UUID.randomUUID())));
  assertEquals("STORED",first.state());
  var second=identify(s,receive(s,UUID.randomUUID()));
  final var pending=second;
  // Deliberately request the same occupied physical bay while all ordinary cases
  // receive unique bay fixtures. The case write, custody and audit must roll back.
  evidence(pending.decedentRef(),pending.caseId(),"STORE",pending.version(),
   "STORAGE_SAFE","SAFE-BAY-M27",ISSUER);
  final var sameBay=cmd("STORE",pending.version(),"SAFE-BAY-M27",
   first.currentStorage(),RECEIVER,null,null);
  assertThrows(Exception.class,()->s.change(who(OFFICER,"m27:store"),
   pending.caseId(),sameBay));
  var after=s.one(who(OFFICER,"m27:read"),pending.caseId());
  assertEquals("IDENTIFIED",after.state());
  assertEquals(2,after.version());
  assertEquals(1,rows("mortuary_custody_events",pending.caseId()));
  assertEquals(2,rows("mortuary_audit_events",pending.caseId()));
 }
 @Test void mismatchedBodyIdentityOrExpiredAuthorityCannotApproveTransfer(){
  pgOnly();var s=store();
  var accepted=receive(s,UUID.randomUUID());
  final var pending=accepted;
  evidence(UUID.randomUUID(),pending.caseId(),"IDENTIFY",pending.version(),
   "IDENTITY_VERIFIED","WRONG-SUBJECT-M27",ISSUER);
  assertThrows(WorkflowViolation.class,()->s.change(who(OFFICER,"m27:identify"),
   pending.caseId(),cmd("IDENTIFY",pending.version(),"WRONG-SUBJECT-M27",null,null,null,null)));
  var current=s.one(who(OFFICER,"m27:read"),pending.caseId());
  assertEquals("RECEIVED",current.state());
  assertEquals(1,rows("mortuary_audit_events",pending.caseId()));
 }
}
