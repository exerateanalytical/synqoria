package org.synqoria.operations;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.synqoria.operations.domain.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Actually uses disposable PG16 with forced RLS, not an in-memory mock. */
final class OperationsStorePgIntegrationTest {
 private static final UUID CELL=UUID.fromString("10000000-1000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("20000000-2000-4000-8000-000000000002");
 private static final UUID OTHER_FAC=UUID.fromString("30000000-3000-4000-8000-000000000003");
 private static final UUID MAKER=UUID.fromString("40000000-4000-4000-8000-000000000004");
 private static final UUID REVIEWER=UUID.fromString("50000000-5000-4000-8000-000000000005");
 private DataSource source(){
  return new DriverManagerDataSource(
   System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),
   System.getenv("SYNQORIA_OPS_TEST_DB_USER"),
   System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));
 }
 private OperationsStore store(DataSource ds){
  return new OperationsStore(new JdbcTemplate(ds),JsonMapper.builder().build(),
   new DataSourceTransactionManager(ds),"synqoria_operations_runtime");
 }
 private OperationWorkflow.Actor maker(){
  return new OperationWorkflow.Actor(MAKER,CELL,FAC,Set.of("m21:create","m21:submit","m21:read"));
 }
 private OperationWorkflow.Actor reviewer(){
  return new OperationWorkflow.Actor(REVIEWER,CELL,FAC,Set.of("m21:approve","m21:read"));
 }
 @Test void realPg16CommittedIdempotentWritesAndMakerCheckerAreScopedAndAudited()throws Exception{
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
  DataSource ds=source();OperationsStore store=store(ds);
  UUID createId=UUID.randomUUID();
  Map<String,Object> fields=Map.of("title","General hospital desks","currency","XAF","amountMinor",100000,"supplierRef","supplier-39","costCentre","operations");
  OperationsApi.NewItem req=new OperationsApi.NewItem("REQUISITION",fields,createId);
  var created=store.create(maker(),"M21",req);
  assertEquals("DRAFT",created.status());assertEquals(1,created.version());
  var repeated=store.create(maker(),"M21",req);
  assertEquals(created.id(),repeated.id());
  assertThrows(WorkflowViolation.class,()->store.create(maker(),"M21",
   new OperationsApi.NewItem("REQUISITION",Map.of("title","Changed"),createId)));
  assertEquals(created.id(),store.one(maker(),"M21",created.id()).id());
  assertEquals(1,store.list(maker(),"M21",10).stream()
   .filter(item->item.id().equals(created.id())).count());
  var foreign=new OperationWorkflow.Actor(MAKER,CELL,OTHER_FAC,Set.of("m21:read"));
  assertThrows(WorkflowViolation.class,()->store.one(foreign,"M21",created.id()));
  assertTrue(store.list(foreign,"M21",10).stream()
   .noneMatch(item->item.id().equals(created.id())));
  var submitted=store.transition(maker(),"M21",created.id(),
   new OperationsApi.TransitionItem("SUBMIT",1,Map.of(),UUID.randomUUID()));
  assertEquals("SUBMITTED",submitted.status());assertEquals(2,submitted.version());
  assertThrows(WorkflowViolation.class,()->store.transition(maker(),"M21",created.id(),
   new OperationsApi.TransitionItem("APPROVE",2,Map.of(),UUID.randomUUID())));
  var approved=store.transition(reviewer(),"M21",created.id(),
   new OperationsApi.TransitionItem("APPROVE",2,Map.of(),UUID.randomUUID()));
  assertEquals("APPROVED",approved.status());assertEquals(3,approved.version());
  assertThrows(WorkflowViolation.class,()->store.transition(reviewer(),"M21",created.id(),
   new OperationsApi.TransitionItem("APPROVE",2,Map.of(),UUID.randomUUID())));
  try(Connection c=ds.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement cell=c.prepareStatement("SELECT set_config('synqoria.cell',?,true)")){
    cell.setString(1,CELL.toString());cell.execute();
   }
   try(PreparedStatement fac=c.prepareStatement("SELECT set_config('synqoria.facility',?,true)")){
    fac.setString(1,FAC.toString());fac.execute();
   }
   for(String table:List.of("audit_events","outbox")){
    try(PreparedStatement count=c.prepareStatement(
      "SELECT count(*) FROM synqoria_operations."+table+" WHERE item_id=?")){
     count.setObject(1,created.id());
     try(ResultSet rs=count.executeQuery()){assertTrue(rs.next());assertEquals(3,rs.getInt(1));}
    }
   }
   try(PreparedStatement update=c.prepareStatement(
      "UPDATE synqoria_operations.audit_events SET action='FORGED' WHERE item_id=?")){
    update.setObject(1,created.id());
    assertThrows(SQLException.class,update::executeUpdate);
   }
   c.rollback();
  }
 }
}
