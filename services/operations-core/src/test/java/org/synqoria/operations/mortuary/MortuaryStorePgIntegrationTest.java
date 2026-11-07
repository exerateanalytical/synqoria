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
  evidence(subject,caseId,action,version,kind,ref,approver,null,null);
 }
 private void evidence(UUID subject,UUID caseId,String action,long version,
                        String kind,String ref,UUID approver,String storageCode){
  evidence(subject,caseId,action,version,kind,ref,approver,storageCode,null);
 }
 private void evidence(UUID subject,UUID caseId,String action,long version,
                        String kind,String ref,UUID approver,String storageCode,String recipient){
  UUID receiver=Set.of("STORAGE_SAFE","CUSTODY_ACCEPTED").contains(kind)?RECEIVER:null;
  UUID witness=Set.of("HANDOVER_RECEIPT","BELONGINGS_RELEASED").contains(kind)?RECEIVER:null;
  String boundRecipient="RECIPIENT_VERIFIED".equals(kind)?ref:recipient;
  admin().update("INSERT INTO synqoria_operations.mortuary_authority_evidence " +
   "(proof_id,cell_id,facility_id,decedent_ref,case_id,for_action,expected_version,"+
   "proof_kind,external_ref,authority_issuer,approved_by,valid_from,valid_until,digest_sha256,"+
   "storage_code,receiving_staff_id,witness_staff_id,recipient_ref) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?,?,clock_timestamp()-interval '5 minutes',"+
   "clock_timestamp()+interval '2 hours',repeat('a',64),?,?,?,?)",
   UUID.randomUUID(),CELL,FAC,subject,caseId,action,version,kind,ref,
   "synthetic-authority-ci",approver,storageCode,receiver,witness,boundRecipient);
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
  String assignedBay="BAY-"+c.caseId().toString().substring(0,8);
  evidence(c.decedentRef(),c.caseId(),"STORE",c.version(),"STORAGE_SAFE","SAFE-BAY-M27",ISSUER,assignedBay);
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
  evidence(subject,id,"TRANSFER",c.version(),"CUSTODY_ACCEPTED","ACCEPT-M27",ISSUER,"BAY-02");
  c=s.change(who(OFFICER,"m27:transfer"),id,
   cmd("TRANSFER",c.version(),"ACCEPT-M27","BAY-02",RECEIVER,null,null));
  assertEquals("BAY-02",c.currentStorage());
  final String recipient="RECIPIENT-VERIFY-M27",legal="RELEASE-AUTH-M27";
  var makerAttempt=cmd("REVIEW_RELEASE",c.version(),null,null,null,recipient,legal);
  assertThrows(WorkflowViolation.class,()->s.change(who(INTAKE,"m27:review"),id,makerAttempt));
  evidence(subject,id,"REVIEW_RELEASE",c.version(),"LEGAL_RELEASE",legal,ISSUER,null,recipient);
  evidence(subject,id,"REVIEW_RELEASE",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  c=s.change(who(REVIEWER,"m27:review"),id,
   cmd("REVIEW_RELEASE",c.version(),null,null,null,recipient,legal));
  assertEquals("RELEASE_REVIEWED",c.state());
  var sameReviewerAttempt=cmd("AUTHORIZE_RELEASE",c.version(),null,null,null,recipient,legal);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:authorize"),id,
   sameReviewerAttempt));
  evidence(subject,id,"AUTHORIZE_RELEASE",c.version(),"RELEASE_AUTHORIZATION",legal,ISSUER,null,recipient);
  evidence(subject,id,"AUTHORIZE_RELEASE",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  c=s.change(who(APPROVER,"m27:authorize"),id,
   cmd("AUTHORIZE_RELEASE",c.version(),null,null,null,recipient,legal));
  assertEquals("RELEASE_AUTHORIZED",c.state());
  evidence(subject,id,"HANDOVER",c.version(),"HANDOVER_RECEIPT","HANDOVER-M27",ISSUER,null,recipient);
  evidence(subject,id,"HANDOVER",c.version(),"RECIPIENT_VERIFIED",recipient,ISSUER);
  evidence(subject,id,"HANDOVER",c.version(),"BELONGINGS_RELEASED","BELONGINGS-RELEASE-M27",ISSUER,null,recipient);
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
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),"LEGAL_RELEASE","LEGAL-M27",ISSUER,null,"RECIPIENT-M27");
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),"RECIPIENT_VERIFIED","RECIPIENT-M27",ISSUER);
  assertThrows(WorkflowViolation.class,()->s.change(who(REVIEWER,"m27:review"),heldCase,
   cmd("REVIEW_RELEASE",heldVersion,null,null,null,"RECIPIENT-M27","LEGAL-M27")));
  // Court hold metadata is visible to an authorized release reviewer only.
  var authorizedHolds=s.legalHolds(who(REVIEWER,"m27:review"),heldCase);
  assertEquals(1,authorizedHolds.size());
  assertEquals(hold,authorizedHolds.getFirst().holdId());
  assertTrue(authorizedHolds.getFirst().active());
  assertEquals("HOLD-M27",authorizedHolds.getFirst().authorityRef());
  assertThrows(WorkflowViolation.class,()->s.legalHolds(who(OFFICER,"m27:read"),heldCase));
  var otherFacility=new OperationWorkflow.Actor(REVIEWER,CELL,CROSS,Set.of("m27:review"));
  assertThrows(WorkflowViolation.class,()->s.legalHolds(otherFacility,heldCase));
  admin().update("UPDATE synqoria_operations.mortuary_legal_holds SET active=false,"+
   "rescinded_at=clock_timestamp() WHERE hold_id=?",hold);
  var rescindedHolds=s.legalHolds(who(REVIEWER,"m27:review"),heldCase);
  assertFalse(rescindedHolds.getFirst().active());
  assertNotNull(rescindedHolds.getFirst().rescindedAt());
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
   "STORAGE_SAFE","SAFE-BAY-M27",ISSUER,first.currentStorage());
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
 @Test void revokedDeathOrIdentityBeforeReleaseAlwaysFailsClosed(){
  pgOnly();var service=store();
  var first=store(service,identify(service,receive(service,UUID.randomUUID())));
  admin().update("UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET revoked_at=clock_timestamp() "+
   "WHERE cell_id=? AND facility_id=? AND decedent_ref=? "+
   "AND for_action='RECEIVE' AND proof_kind='DEATH_CONFIRMED'",
   CELL,FAC,first.decedentRef());
  evidence(first.decedentRef(),first.caseId(),"REVIEW_RELEASE",first.version(),
   "LEGAL_RELEASE","LEGAL-REVOCATION-CI",ISSUER,null,"RECIPIENT-REVOCATION-CI");
  evidence(first.decedentRef(),first.caseId(),"REVIEW_RELEASE",first.version(),
   "RECIPIENT_VERIFIED","RECIPIENT-REVOCATION-CI",ISSUER);
  final var deniedDeath=first;
  assertThrows(WorkflowViolation.class,()->service.change(who(REVIEWER,"m27:review"),
   deniedDeath.caseId(),cmd("REVIEW_RELEASE",deniedDeath.version(),null,null,null,
    "RECIPIENT-REVOCATION-CI","LEGAL-REVOCATION-CI")));
  assertEquals("STORED",service.one(who(OFFICER,"m27:read"),first.caseId()).state());
  var second=store(service,identify(service,receive(service,UUID.randomUUID())));
  admin().update("UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET revoked_at=clock_timestamp() "+
   "WHERE cell_id=? AND facility_id=? AND decedent_ref=? "+
   "AND for_action='IDENTIFY' AND proof_kind='IDENTITY_VERIFIED'",
   CELL,FAC,second.decedentRef());
  final var deniedIdentity=second;
  assertThrows(WorkflowViolation.class,()->service.change(who(REVIEWER,"m27:review"),
   deniedIdentity.caseId(),cmd("REVIEW_RELEASE",deniedIdentity.version(),null,null,null,
    "RECIPIENT-REVOCATION-CI","LEGAL-REVOCATION-CI")));
  assertEquals("STORED",service.one(who(OFFICER,"m27:read"),second.caseId()).state());
 }
 @Test void independentLegalReviewProofRevokedAfterReviewPreventsSecondAuthorization(){
  pgOnly();var service=store();
  var item=store(service,identify(service,receive(service,UUID.randomUUID())));
  final String legal="LEGAL-REVOKE-AFTER-REVIEW",recipient="RECIP-REVOKE-CI";
  evidence(item.decedentRef(),item.caseId(),"REVIEW_RELEASE",item.version(),
   "LEGAL_RELEASE",legal,ISSUER,null,recipient);
  evidence(item.decedentRef(),item.caseId(),"REVIEW_RELEASE",item.version(),
   "RECIPIENT_VERIFIED",recipient,ISSUER);
  item=service.change(who(REVIEWER,"m27:review"),item.caseId(),
   cmd("REVIEW_RELEASE",item.version(),null,null,null,recipient,legal));
  assertEquals("RELEASE_REVIEWED",item.state());
  evidence(item.decedentRef(),item.caseId(),"AUTHORIZE_RELEASE",item.version(),
   "RELEASE_AUTHORIZATION",legal,ISSUER,null,recipient);
  evidence(item.decedentRef(),item.caseId(),"AUTHORIZE_RELEASE",item.version(),
   "RECIPIENT_VERIFIED",recipient,ISSUER);
  admin().update("UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET revoked_at=clock_timestamp() "+
   "WHERE cell_id=? AND facility_id=? AND case_id=? "+
   "AND for_action='REVIEW_RELEASE' AND proof_kind='LEGAL_RELEASE'",
   CELL,FAC,item.caseId());
  final var pending=item;
  assertThrows(WorkflowViolation.class,()->service.change(who(APPROVER,"m27:authorize"),
   pending.caseId(),cmd("AUTHORIZE_RELEASE",pending.version(),null,null,null,recipient,legal)));
  assertEquals("RELEASE_REVIEWED",service.one(who(OFFICER,"m27:read"),item.caseId()).state());
 }

 @Test void authorityEvidenceAndLegalHoldsCannotBeRetargetedErasedOrReactivated(){
  pgOnly();
  var service=store();
  var item=receive(service,UUID.randomUUID());
  var admin=admin();
  final UUID decedent=item.decedentRef(), caseId=item.caseId();
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_authority_evidence SET proof_kind='IDENTITY_ATTESTED' "+
   "WHERE decedent_ref=? AND proof_kind='DEATH_CONFIRMED'",decedent));
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_authority_evidence SET digest_sha256=repeat('b',64) "+
   "WHERE decedent_ref=? AND proof_kind='DEATH_CONFIRMED'",decedent));
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET valid_until=valid_until+interval '1 day' WHERE decedent_ref=? "+
   "AND proof_kind='DEATH_CONFIRMED'",decedent));
  assertThrows(Exception.class,()->admin.update(
   "DELETE FROM synqoria_operations.mortuary_authority_evidence WHERE decedent_ref=?",decedent));
  assertEquals(1,admin.update(
   "UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET revoked_at=clock_timestamp() WHERE decedent_ref=? "+
   "AND proof_kind='DEATH_CONFIRMED'",decedent));
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET revoked_at=NULL WHERE decedent_ref=? AND proof_kind='DEATH_CONFIRMED'",decedent));
  UUID hold=UUID.randomUUID();
  admin.update("INSERT INTO synqoria_operations.mortuary_legal_holds "+
   "(hold_id,cell_id,facility_id,case_id,authority_ref,reason_code,ordered_by) "+
   "VALUES(?,?,?,?,?,?,?)",hold,CELL,FAC,caseId,"COURT-ORDER-V1","COURT_REVIEW",AUTHORITY);
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_legal_holds SET authority_ref='COURT-ORDER-V2' "+
   "WHERE hold_id=?",hold));
  assertThrows(Exception.class,()->admin.update(
   "DELETE FROM synqoria_operations.mortuary_legal_holds WHERE hold_id=?",hold));
  assertEquals(1,admin.update(
   "UPDATE synqoria_operations.mortuary_legal_holds "+
   "SET active=false,rescinded_at=clock_timestamp() WHERE hold_id=?",hold));
  assertThrows(Exception.class,()->admin.update(
   "UPDATE synqoria_operations.mortuary_legal_holds "+
   "SET active=true,rescinded_at=NULL WHERE hold_id=?",hold));
  assertFalse(service.legalHolds(who(REVIEWER,"m27:review"),caseId).getFirst().active());
 }


 @Test void storageAuthorityEvidenceMustBindExactPhysicalDestination(){
  pgOnly();var service=store();
  var identified=identify(service,receive(service,UUID.randomUUID()));
  String approvedBay="ATTESTED-BAY-"+identified.caseId().toString().substring(0,8);
  String unapprovedBay="OTHER-BAY-"+identified.caseId().toString().substring(0,8);
  evidence(identified.decedentRef(),identified.caseId(),"STORE",identified.version(),
   "STORAGE_SAFE","STORAGE-ATTEST-CI",ISSUER,approvedBay);
  final var pending=identified;
  assertThrows(WorkflowViolation.class,()->service.change(who(OFFICER,"m27:store"),
   pending.caseId(),cmd("STORE",pending.version(),"STORAGE-ATTEST-CI",unapprovedBay,
    RECEIVER,null,null)));
  assertEquals("IDENTIFIED",service.one(who(OFFICER,"m27:read"),pending.caseId()).state());
  var stored=service.change(who(OFFICER,"m27:store"),pending.caseId(),
   cmd("STORE",pending.version(),"STORAGE-ATTEST-CI",approvedBay,RECEIVER,null,null));
  assertEquals(approvedBay,stored.currentStorage());
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_authority_evidence "+
   "SET storage_code=? WHERE case_id=? AND for_action='STORE'",unapprovedBay,pending.caseId()));
 }


 @Test void authorityParticipantBindingsRejectWrongCustodyReceiverAndWitness(){
  pgOnly();
  var service=store();
  var c=identify(service,receive(service,UUID.randomUUID()));
  UUID otherReceiver=UUID.fromString("27000000-0000-4000-8000-000000000012");
  admin().update("INSERT INTO synqoria_operations.workforce_grants "+
   "(cell_id,facility_id,actor_id,grant_revision,permissions,active,"+
   "shift_from,shift_until,valid_until,approved_by,source_ref) "+
   "VALUES(?,?,?,1,ARRAY['m27:custody_receive']::text[],true,"+
   "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 hour',"+
   "clock_timestamp()+interval '2 hours',?,'independent-roster-ci')",
   CELL,FAC,otherReceiver,AUTHORITY);
  String bay="BOUND-BAY-"+c.caseId().toString().substring(0,8);
  evidence(c.decedentRef(),c.caseId(),"STORE",c.version(),"STORAGE_SAFE",
   "BOUND-RECEIVER-CI",ISSUER,bay);
  final var pending=c;
  assertThrows(WorkflowViolation.class,()->service.change(who(OFFICER,"m27:store"),
   pending.caseId(),cmd("STORE",pending.version(),"BOUND-RECEIVER-CI",bay,otherReceiver,null,null)));
  c=service.change(who(OFFICER,"m27:store"),pending.caseId(),
   cmd("STORE",pending.version(),"BOUND-RECEIVER-CI",bay,RECEIVER,null,null));
  assertEquals("STORED",c.state());
  final String recipient="BOUND-RECIPIENT-CI",legal="BOUND-LEGAL-CI";
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),
   "LEGAL_RELEASE",legal,ISSUER,null,recipient);
  evidence(c.decedentRef(),c.caseId(),"REVIEW_RELEASE",c.version(),
   "RECIPIENT_VERIFIED",recipient,ISSUER);
  c=service.change(who(REVIEWER,"m27:review"),c.caseId(),
   cmd("REVIEW_RELEASE",c.version(),null,null,null,recipient,legal));
  evidence(c.decedentRef(),c.caseId(),"AUTHORIZE_RELEASE",c.version(),
   "RELEASE_AUTHORIZATION",legal,ISSUER,null,recipient);
  evidence(c.decedentRef(),c.caseId(),"AUTHORIZE_RELEASE",c.version(),
   "RECIPIENT_VERIFIED",recipient,ISSUER);
  c=service.change(who(APPROVER,"m27:authorize"),c.caseId(),
   cmd("AUTHORIZE_RELEASE",c.version(),null,null,null,recipient,legal));
  evidence(c.decedentRef(),c.caseId(),"HANDOVER",c.version(),
   "HANDOVER_RECEIPT","BOUND-HANDOVER-CI",ISSUER,null,recipient);
  evidence(c.decedentRef(),c.caseId(),"HANDOVER",c.version(),
   "RECIPIENT_VERIFIED",recipient,ISSUER);
  evidence(c.decedentRef(),c.caseId(),"HANDOVER",c.version(),
   "BELONGINGS_RELEASED","BELONGINGS-RELEASE-M27",ISSUER,null,recipient);
  final var authorized=c;
  var spoof=new Change("HANDOVER",authorized.version(),UUID.randomUUID(),
   "BOUND-HANDOVER-CI",null,null,recipient,legal,null,null,otherReceiver,
   "BELONGINGS-RELEASE-M27");
  assertThrows(WorkflowViolation.class,()->service.change(who(HANDOVER,"m27:handover"),
   authorized.caseId(),spoof));
  assertEquals("RELEASE_AUTHORIZED",service.one(who(OFFICER,"m27:read"),authorized.caseId()).state());
  var correct=service.change(who(HANDOVER,"m27:handover"),authorized.caseId(),
   cmd("HANDOVER",authorized.version(),"BOUND-HANDOVER-CI",null,null,recipient,legal));
  assertEquals("RELEASED",correct.state());
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_authority_evidence SET witness_staff_id=? "+
   "WHERE case_id=? AND proof_kind='HANDOVER_RECEIPT'",otherReceiver,authorized.caseId()));
 }


 @Test void databaseLifecycleDeniesForgedDirectSqlAndUnloggedTransitions(){
  pgOnly();
  var service=store();
  var c=receive(service,UUID.randomUUID());
  UUID id=c.caseId();
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_cases SET decedent_ref=? WHERE case_id=?",
   UUID.randomUUID(),id));
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_cases SET state='RELEASED',"+
   "version=version+1,released_at=clock_timestamp(),last_actor=?,updated_at=clock_timestamp() "+
   "WHERE case_id=?",OFFICER,id));
  // Even a valid-looking transition is rolled back if not accompanied by its
  // matching transactionally committed command, audit event and outbox event.
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_cases SET state='IDENTIFIED',"+
   "version=version+1,last_actor=?,updated_at=clock_timestamp() WHERE case_id=?",
   OFFICER,id));
  assertEquals("RECEIVED",service.one(who(OFFICER,"m27:read"),id).state());
  var identified=identify(service,c);
  assertEquals("IDENTIFIED",identified.state());
  var stored=store(service,identified);
  assertEquals("STORED",stored.state());
  assertThrows(Exception.class,()->admin().update(
   "UPDATE synqoria_operations.mortuary_cases SET current_storage='UNATTESTED-BAY',"+
   "version=version+1,updated_at=clock_timestamp() WHERE case_id=?",id));
  assertEquals(stored.currentStorage(),
   service.one(who(OFFICER,"m27:read"),id).currentStorage());
 }

}
