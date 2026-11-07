package org.synqoria.operations;

import static org.junit.jupiter.api.Assertions.*;

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

/** Real PostgreSQL18.6 FORCE RLS checks for all five extensions. Synthetic fixtures only. */
final class OperationsIntegrationsPgTest {
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID(),maker=UUID.randomUUID(),reviewer=UUID.randomUUID();
 void ready(){Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));}
 JdbcTemplate admin(){return new JdbcTemplate(new DriverManagerDataSource(
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD")));}
 OperationsIntegrationService service(){
  DataSource ds=new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),
   System.getenv("SYNQORIA_OPS_TEST_DB_USER"),System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));
  JdbcTemplate jdbc=new JdbcTemplate(ds);
  OperationsStore store=new OperationsStore(jdbc,JsonMapper.builder().build(),
   new DataSourceTransactionManager(ds),"synqoria_operations_runtime");
  return new OperationsIntegrationService(store,jdbc);
 }
 OperationWorkflow.Actor actor(UUID user,String...grants){
  return new OperationWorkflow.Actor(user,cell,facility,Set.of(grants));
 }
 UUID item(String module,String type,String status,String fields){
  UUID id=UUID.randomUUID();
  admin().update("INSERT INTO synqoria_operations.items "+
   "(id,cell_id,facility_id,module_id,record_type,status,version,created_by,last_actor,fields) "+
   "VALUES(?,?,?,?,?,?,1,?,?,?::jsonb)",id,cell,facility,module,type,status,maker,maker,fields);
  return id;
 }
 @Test void m21ReconciliationReadsOnlyVerifiedInternalStateWithoutInventingInvoiceApproval(){
  ready();UUID order=item("M21","REQUISITION","ORDERED","{\"title\":\"synthetic order\"}");
  admin().update("INSERT INTO synqoria_operations.procurement_orders "+
   "(cell_id,facility_id,item_id,supplier_ref,purchase_ref,amount_minor,currency) "+
   "VALUES (?,?,?,?,?,?,?)",cell,facility,order,"supplier-abc","PO-0001",12500,"XAF");
  var svc=service();var p=svc.procurementStatus(actor(maker,"m21:read"),20);
  var found=p.stream().filter(x->x.itemId().equals(order)).findFirst().orElseThrow();
  assertEquals("AWAITING_GOODS_RECEIPT",found.disposition());
  assertEquals("XAF",found.currency());assertEquals(12500,found.amountMinor());
  var other=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m21:read"));
  assertTrue(svc.procurementStatus(other,100).stream().noneMatch(x->x.itemId().equals(order)));
  assertThrows(WorkflowViolation.class,()->svc.procurementStatus(actor(maker,"m22:read"),50));
 }
 @Test void m22CycleCountIsIdempotentMakerCheckerAndNeverAltersPhysicalStock(){
  ready();var svc=service();
  UUID stock=item("M22","STOCK_ITEM","AVAILABLE","{\"title\":\"synthetic surgical gloves\",\"sku\":\"GLOVE100\"}");
  admin().update("INSERT INTO synqoria_operations.stock_balances "+
   "(cell_id,facility_id,item_id,lot_code,bin_code,on_hand,reserved,version) VALUES(?,?,?,?,?,?,?,?)",
   cell,facility,stock,"LOT-1","BIN-01",40,7,5);
  UUID cmd=UUID.randomUUID();
  var request=new OperationsIntegrationService.CountRequest("LOT-1","BIN-01",38,cmd);
  var counter=actor(maker,"m22:count");var approver=actor(reviewer,"m22:adjust");
  var count=svc.captureCount(counter,stock,request);
  assertEquals(40,count.expectedOnHand());assertEquals(7,count.expectedReserved());
  assertEquals(-2,count.variance());
  assertEquals(count.countId(),svc.captureCount(counter,stock,request).countId());
  assertThrows(WorkflowViolation.class,()->svc.captureCount(counter,stock,
   new OperationsIntegrationService.CountRequest("LOT-1","BIN-01",36,cmd)));
  assertThrows(WorkflowViolation.class,()->svc.reviewCount(counter,count.countId(),true));
  var reviewed=svc.reviewCount(approver,count.countId(),true);
  assertEquals("REVIEWED",reviewed.state());
  assertEquals(reviewer,reviewed.reviewedBy());
  assertThrows(WorkflowViolation.class,()->svc.reviewCount(approver,count.countId(),true));
  var balance=admin().queryForMap("SELECT on_hand,reserved,version FROM synqoria_operations.stock_balances "+
   "WHERE item_id=? AND lot_code='LOT-1' AND bin_code='BIN-01'",stock);
  assertEquals(40L,((Number)balance.get("on_hand")).longValue());
  assertEquals(5L,((Number)balance.get("version")).longValue());
 }
 @Test void m22RejectsCountReviewWhenStockVersionChanges(){
  ready();UUID stock=item("M22","STOCK_ITEM","AVAILABLE","{\"title\":\"synthetic inventory\"}");
  admin().update("INSERT INTO synqoria_operations.stock_balances "+
   "(cell_id,facility_id,item_id,lot_code,bin_code,on_hand,reserved,version) VALUES(?,?,?,?,?,?,?,?)",
   cell,facility,stock,"LOT-2","BIN-02",8,0,2);
  var svc=service();
  var count=svc.captureCount(actor(maker,"m22:count"),stock,
   new OperationsIntegrationService.CountRequest("LOT-2","BIN-02",7,UUID.randomUUID()));
  admin().update("UPDATE synqoria_operations.stock_balances SET version=version+1 WHERE item_id=?",stock);
  assertThrows(WorkflowViolation.class,()->svc.reviewCount(actor(reviewer,"m22:adjust"),count.countId(),true));
 }
 @Test void m25AvailabilityExcludesCurrentlyAssignedAmbulances(){
  ready();UUID available=item("M25","VEHICLE","VEHICLE_AVAILABLE","{\"title\":\"ambulance A\",\"plate\":\"A123\",\"vehicleType\":\"ambulance\"}");
  UUID assigned=item("M25","VEHICLE","VEHICLE_AVAILABLE","{\"title\":\"ambulance B\",\"plate\":\"B123\",\"vehicleType\":\"ambulance\"}");
  UUID trip=item("M25","TRIP","ASSIGNED","{\"title\":\"synthetic dispatch\"}");
  admin().update("INSERT INTO synqoria_operations.transport_assignments "+
   "(cell_id,facility_id,item_id,vehicle_ref,driver_ref,origin_ref,destination_ref) "+
   "VALUES(?,?,?,?,?,?,?)",cell,facility,trip,assigned.toString(),reviewer.toString(),"ward","referral");
  var rows=service().availableVehicles(actor(maker,"m25:read"),100);
  assertTrue(rows.stream().anyMatch(x->available.equals(x.get("vehicle_id"))));
  assertTrue(rows.stream().noneMatch(x->assigned.equals(x.get("vehicle_id"))));
 }
 @Test void m29PublicationFeedOnlyExposesCurrentFacilityAndBoundedCursor(){
  ready();UUID item=item("M29","CONFIGURATION","PUBLISHED","{\"title\":\"synthetic location\"}");
  UUID publication=UUID.randomUUID();
  admin().update("INSERT INTO synqoria_operations.facility_publications "+
   "(publication_id,cell_id,facility_id,item_id,config_key,schema_version,item_version,content_digest,published_by) "+
   "VALUES(?,?,?,?,?,?,?,?,?)",publication,cell,facility,item,"ward-a","1",1,"b".repeat(64),maker);
  var svc=service();var rows=svc.publishedChanges(actor(maker,"m29:read"),null,100);
  assertTrue(rows.stream().anyMatch(x->publication.equals(x.get("publication_id"))));
  assertTrue(svc.publishedChanges(actor(maker,"m29:read"),"2999-01-01T00:00:00Z",100).isEmpty());
  assertThrows(WorkflowViolation.class,()->svc.publishedChanges(actor(maker,"m29:read"),"not-a-date",10));
  var other=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m29:read"));
  assertTrue(svc.publishedChanges(other,null,100).stream().noneMatch(x->publication.equals(x.get("publication_id"))));
 }
 @Test void m37QueueHidesPayloadAndFiltersRetriesByActualDueTime(){
  ready();UUID due=item("M37","MESSAGE","QUEUED","{\"title\":\"synthetic notice\"}");
  UUID later=item("M37","MESSAGE","QUEUED","{\"title\":\"synthetic deferred notice\"}");
  admin().update("INSERT INTO synqoria_operations.notification_jobs "+
   "(cell_id,facility_id,item_id,recipient_ref,template_ref,channel) VALUES(?,?,?,?,?,?)",
   cell,facility,due,reviewer.toString(),"template-01","internal");
  admin().update("INSERT INTO synqoria_operations.notification_jobs "+
   "(cell_id,facility_id,item_id,recipient_ref,template_ref,channel,next_attempt_at) "+
   "VALUES(?,?,?,?,?,?,clock_timestamp()+interval '1 day')",
   cell,facility,later,reviewer.toString(),"template-01","sms");
  var svc=service();var list=svc.dueNotifications(actor(maker,"m37:deliver"),100);
  assertTrue(list.stream().anyMatch(x->due.equals(x.get("item_id"))));
  assertTrue(list.stream().noneMatch(x->later.equals(x.get("item_id"))));
  assertTrue(list.stream().noneMatch(x->x.containsKey("fields")||x.containsKey("body")||x.containsKey("bearer")));
  assertThrows(WorkflowViolation.class,()->svc.dueNotifications(actor(maker,"m37:read"),50));
 }
}
