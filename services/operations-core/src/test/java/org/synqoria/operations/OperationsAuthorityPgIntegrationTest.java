package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.operations.domain.*;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual PostgreSQL 18.6 role/RLS/transaction integration. JWT test object is
 * synthetic and deliberately bypasses HTTP; signed issuer/audience is enforced
 * separately by Spring Security in the real API. No hospital accounts are seeded.
 */
final class OperationsAuthorityPgIntegrationTest {
 private static final UUID CELL=UUID.fromString("11000000-1000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("22000000-2000-4000-8000-000000000002");
 private static final UUID ACTOR=UUID.fromString("33000000-3000-4000-8000-000000000003");
 private static final UUID REVIEWER=UUID.fromString("44000000-4000-4000-8000-000000000004");
 private static final UUID OTHER_FAC=UUID.fromString("55000000-5000-4000-8000-000000000005");
 private DataSource runtime(){return new DriverManagerDataSource(
  System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_DB_USER"),
  System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));}
 private DataSource owner(){return new DriverManagerDataSource(
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD"));}
 private OperationWorkflow.Actor actor(UUID id,String...caps){
  return new OperationWorkflow.Actor(id,CELL,FAC,Set.of(caps));
 }
 private OperationsStore store(DataSource ds){
  return new OperationsStore(new JdbcTemplate(ds),JsonMapper.builder().build(),
   new DataSourceTransactionManager(ds),"synqoria_operations_runtime");
 }
 private void attest(JdbcTemplate admin,UUID item,UUID approvedBy,String action,long version,
                      String kind,String ref){
  admin.update("INSERT INTO synqoria_operations.external_evidence " +
   "(evidence_id,cell_id,facility_id,item_id,for_action,expected_version,evidence_kind," +
   "external_ref,issuer_ref,approver_id,valid_from,valid_until,digest_sha256) " +
   "VALUES (?,?,?,?,?,?,?,?,?,?,clock_timestamp()-interval '1 minute'," +
   "clock_timestamp()+interval '1 hour',repeat('a',64))",
   UUID.randomUUID(),CELL,FAC,item,action,version,kind,ref,"verified-hospital-integration",approvedBy);
 }
 private Jwt token(UUID id,UUID facility,long revision,List<String> grants){
  Instant now=Instant.now();
  return Jwt.withTokenValue("synthetic-jwt-not-accepted-by-http")
   .header("alg","RS256").subject(id.toString()).issuedAt(now.minusSeconds(30))
   .expiresAt(now.plusSeconds(600))
   .claim("synqoria_cell",CELL.toString()).claim("synqoria_facility",facility.toString())
   .claim("synqoria_permissions",grants).claim("synqoria_grant_expires",now.getEpochSecond()+600)
   .claim("synqoria_grant_revision",revision).build();
 }
 @Test void authoritativeRosterRequiresCurrentShiftRevisionAndScopeAndRuntimeCannotWriteIt(){
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
  var admin=new JdbcTemplate(owner());DataSource runtimeDataSource=runtime();
  var runtimeJdbc=new JdbcTemplate(runtimeDataSource);
  var admission=new WorkforceAdmission(runtimeJdbc,new DataSourceTransactionManager(runtimeDataSource));
  // An independent DBA/integration authority provisions this fixture, NEVER API users.
  admin.update("INSERT INTO synqoria_operations.workforce_grants " +
   "(cell_id,facility_id,actor_id,grant_revision,permissions,active,shift_from,shift_until," +
   "valid_until,approved_by,source_ref) VALUES (?,?,?,1,ARRAY['m21:read','m21:create']::text[],true," +
   "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 hour'," +
   "clock_timestamp()+interval '2 hours',?,'trusted-roster-test')",
   CELL,FAC,ACTOR,REVIEWER);
  var valid=token(ACTOR,FAC,1,List.of("m21:read","m21:create"));
  assertEquals(ACTOR,admission.requireAuthoritativeGrant(valid).actor());
  assertThrows(WorkflowViolation.class,()->admission.requireAuthoritativeGrant(
   token(ACTOR,OTHER_FAC,1,List.of("m21:read"))));
  assertThrows(WorkflowViolation.class,()->admission.requireAuthoritativeGrant(
   token(ACTOR,FAC,2,List.of("m21:read"))));
  assertThrows(WorkflowViolation.class,()->admission.requireAuthoritativeGrant(
   token(ACTOR,FAC,1,List.of("m23:retire"))));
  assertThrows(Exception.class,()->runtimeJdbc.update(
   "INSERT INTO synqoria_operations.workforce_grants(cell_id,facility_id,actor_id,grant_revision,"+
   "permissions,active,shift_from,shift_until,valid_until,approved_by,source_ref) "+
   "VALUES (?,?,?,1,ARRAY['m21:read']::text[],true,now(),now()+interval '1 hour',"+
   "now()+interval '1 day',?,'forged')",CELL,FAC,UUID.randomUUID(),REVIEWER));
  admin.update("UPDATE synqoria_operations.workforce_grants SET active=false,grant_revision=2 "+
   "WHERE cell_id=? AND facility_id=? AND actor_id=?",CELL,FAC,ACTOR);
  assertThrows(WorkflowViolation.class,()->admission.requireAuthoritativeGrant(valid));
 }
 @Test void approvedVersionBoundEvidenceDrivesBiomedicalSafetyButNeverClientAssertions(){
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
  var admin=new JdbcTemplate(owner());var service=store(runtime());
  var maker=actor(ACTOR,"m23:create");
  var reviewer=actor(REVIEWER,"m23:commission");
  var asset=service.create(maker,"M23",new OperationsApi.NewItem("ASSET",
   Map.of("title","Mobile X-ray device","serial","BIO-X-99","locationRef","RAD-1",
   "inspectionRef","INSP-2026-99"),UUID.randomUUID()));
  var cmd=new OperationsApi.TransitionItem("COMMISSION",1,Map.of(),UUID.randomUUID());
  assertThrows(WorkflowViolation.class,()->service.transition(reviewer,"M23",asset.id(),cmd));
  attest(admin,asset.id(),REVIEWER,"COMMISSION",3,"biomed_inspection","wrong-version");
  assertThrows(WorkflowViolation.class,()->service.transition(reviewer,"M23",asset.id(),cmd));
  attest(admin,asset.id(),REVIEWER,"COMMISSION",1,"biomed_inspection","INSP-2026-99");
  var approved=service.transition(reviewer,"M23",asset.id(),cmd);
  assertEquals("COMMISSIONED",approved.status());assertEquals(2,approved.version());
  assertThrows(Exception.class,()->new JdbcTemplate(runtime()).update(
   "UPDATE synqoria_operations.external_evidence SET revoked_at=now() WHERE item_id=?",asset.id()));
  var other=actor(REVIEWER,"m23:commission");
  assertThrows(WorkflowViolation.class,()->service.transition(other,"M23",asset.id(),
   new OperationsApi.TransitionItem("COMMISSION",2,Map.of(),UUID.randomUUID())));
 }
 @Test void actualM22LotBinStockMovesAreAtomicIdempotentAndNeverNegative(){
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
  var admin=new JdbcTemplate(owner());var service=store(runtime());
  var maker=actor(ACTOR,"m22:create","m22:read");
  var worker=actor(REVIEWER,"m22:release","m22:receive","m22:reserve","m22:issue",
   "m22:return","m22:read");
  var stock=service.create(maker,"M22",new OperationsApi.NewItem("STOCK_ITEM",
   Map.of("title","Non-medication sterile storage bin","sku","STOR-9","lot","L001",
   "bin","MAIN","unit","piece","quantity",12,"receiptRef","GRN-2026-9"),
   UUID.randomUUID()));
  assertThrows(WorkflowViolation.class,()->service.transition(worker,"M22",stock.id(),
   new OperationsApi.TransitionItem("RELEASE",1,Map.of(),UUID.randomUUID())));
  attest(admin,stock.id(),REVIEWER,"RELEASE",1,"stock_qc","QC-2026-9");
  var available=service.transition(worker,"M22",stock.id(),
   new OperationsApi.TransitionItem("RELEASE",1,Map.of(),UUID.randomUUID()));
  assertEquals("AVAILABLE",available.status());
  var receive=new OperationsStore.StockMovementRequest("RECEIVE","L001","MAIN",12,
   "GRN-2026-9",2,UUID.randomUUID());
  assertThrows(WorkflowViolation.class,()->service.moveStock(worker,stock.id(),receive));
  attest(admin,stock.id(),REVIEWER,"RECEIVE_STOCK",2,"stock_receipt","GRN-2026-9");
  var received=service.moveStock(worker,stock.id(),receive);
  assertEquals(12,received.onHand());assertEquals(0,received.reserved());
  assertEquals(12,service.moveStock(worker,stock.id(),receive).onHand());
  var reserved=service.moveStock(worker,stock.id(),new OperationsStore.StockMovementRequest(
   "RESERVE","L001","MAIN",8,"REQ-8",3,UUID.randomUUID()));
  assertEquals(8,reserved.reserved());assertEquals(4,reserved.available());
  assertThrows(WorkflowViolation.class,()->service.moveStock(worker,stock.id(),
   new OperationsStore.StockMovementRequest("RESERVE","L001","MAIN",5,"REQ-9",4,UUID.randomUUID())));
  var issued=service.moveStock(worker,stock.id(),new OperationsStore.StockMovementRequest(
   "ISSUE","L001","MAIN",5,"ISSUE-5",4,UUID.randomUUID()));
  assertEquals(7,issued.onHand());assertEquals(3,issued.reserved());
  assertEquals(4,issued.available());
  assertThrows(WorkflowViolation.class,()->service.moveStock(worker,stock.id(),
   new OperationsStore.StockMovementRequest("RETURN","L001","MAIN",1,"RET-1",5,UUID.randomUUID())));
  attest(admin,stock.id(),REVIEWER,"RETURN_STOCK",5,"stock_return","RET-1");
  var returned=service.moveStock(worker,stock.id(),new OperationsStore.StockMovementRequest(
   "RETURN","L001","MAIN",1,"RET-1",5,UUID.randomUUID()));
  assertEquals(8,returned.onHand());assertEquals(3,returned.reserved());
  assertEquals(1,service.balances(worker,stock.id()).size());
  assertThrows(WorkflowViolation.class,()->service.moveStock(worker,stock.id(),
   new OperationsStore.StockMovementRequest("ADJUST","L001","MAIN",1,"UNREVIEWED",6,UUID.randomUUID())));
  var cross=new OperationWorkflow.Actor(REVIEWER,CELL,OTHER_FAC,Set.of("m22:read"));
  assertThrows(WorkflowViolation.class,()->service.balances(cross,stock.id()));
 }
}
