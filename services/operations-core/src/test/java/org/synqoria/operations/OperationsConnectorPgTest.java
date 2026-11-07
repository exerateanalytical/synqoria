package org.synqoria.operations;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Real disposable PostgreSQL18.6 + FORCE RLS, no simulated external deliveries. */
final class OperationsConnectorPgTest {
 private final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID(),
  maker=UUID.randomUUID(),reviewer=UUID.randomUUID();
 private void ready(){Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));}
 private JdbcTemplate admin(){
  return new JdbcTemplate(new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),
   System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD")));
 }
 private OperationsConnectorService svc(){
  DataSource ds=new DriverManagerDataSource(System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),
   System.getenv("SYNQORIA_OPS_TEST_DB_USER"),System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));
  JdbcTemplate jdbc=new JdbcTemplate(ds);
  return new OperationsConnectorService(new OperationsStore(jdbc,JsonMapper.builder().build(),
   new DataSourceTransactionManager(ds),"synqoria_operations_runtime"),jdbc);
 }
 private OperationWorkflow.Actor actor(UUID who,String...grants){
  return new OperationWorkflow.Actor(who,cell,facility,Set.of(grants));
 }
 private UUID item(String module,String type,String status,String fields){
  UUID id=UUID.randomUUID();
  admin().update("INSERT INTO synqoria_operations.items "+
   "(id,cell_id,facility_id,module_id,record_type,status,version,created_by,last_actor,fields) "+
   "VALUES(?,?,?,?,?,?,1,?,?,?::jsonb)",id,cell,facility,module,type,status,maker,maker,fields);
  return id;
 }
 @Test void m21ExportIsExactIdempotentCurrencyAwareAndUnmatchedOrdersCannotExport(){
  ready();
  UUID matched=item("M21","REQUISITION","MATCHED","{\"title\":\"nonclinical supplies\"}");
  UUID unready=item("M21","REQUISITION","ORDERED","{\"title\":\"not matched\"}");
  admin().update("INSERT INTO synqoria_operations.procurement_orders "+
   "(cell_id,facility_id,item_id,supplier_ref,purchase_ref,amount_minor,currency,state,receipt_ref,invoice_ref) "+
   "VALUES(?,?,?,?,?,?,?,'MATCHED',?,?)",cell,facility,matched,
   "supplier-1","PO-1",12345,"XAF","GRN-1","INV-1");
  admin().update("INSERT INTO synqoria_operations.procurement_orders "+
   "(cell_id,facility_id,item_id,supplier_ref,purchase_ref,amount_minor,currency,state) "+
   "VALUES(?,?,?,?,?,?,?,'ORDERED')",cell,facility,unready,"supplier-2","PO-2",100,"USD");
  var service=svc();
  var x=service.enqueueProcurement(actor(maker,"m21:close"),matched);
  assertEquals(x.exportId(),service.enqueueProcurement(actor(maker,"m21:close"),matched).exportId());
  assertEquals(12345L,x.amountMinor());assertEquals("XAF",x.currency());
  assertEquals(64,x.digest().length());
  assertEquals("PENDING",x.state());
  assertThrows(WorkflowViolation.class,()->service.enqueueProcurement(actor(maker,"m21:close"),unready));
  assertTrue(service.pendingProcurement(actor(maker,"m21:read"),100).stream()
   .anyMatch(v->v.exportId().equals(x.exportId())));
  var other=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m21:read"));
  assertTrue(service.pendingProcurement(other,100).stream()
   .noneMatch(v->v.exportId().equals(x.exportId())));
 }
 @Test void m22BarcodeRequiresOneExactSkuLotBinAndNeverMutatesStock(){
  ready();
  UUID stock=item("M22","STOCK_ITEM","AVAILABLE","{\"title\":\"gauze\",\"sku\":\"GAUZE-01\"}");
  admin().update("INSERT INTO synqoria_operations.stock_balances "+
   "(cell_id,facility_id,item_id,lot_code,bin_code,on_hand,reserved,version) "+
   "VALUES(?,?,?,?,?,?,?,?)",cell,facility,stock,"L1","B1",17,4,2);
  var service=svc();var a=actor(maker,"m22:read");
  var r=service.resolveBarcode(a,"GAUZE-01","L1","B1");
  assertEquals(stock,r.itemId());assertEquals(13,r.onHand()-r.reserved());
  assertThrows(WorkflowViolation.class,()->service.resolveBarcode(a,"GAUZE-01","L1","B2"));
  UUID duplicate=item("M22","STOCK_ITEM","AVAILABLE","{\"title\":\"duplicate\",\"sku\":\"GAUZE-01\"}");
  admin().update("INSERT INTO synqoria_operations.stock_balances "+
   "(cell_id,facility_id,item_id,lot_code,bin_code,on_hand,reserved,version) "+
   "VALUES(?,?,?,?,?,?,?,?)",cell,facility,duplicate,"L1","B1",1,0,1);
  assertEquals("ambiguous_stock_identifier",
   assertThrows(WorkflowViolation.class,()->service.resolveBarcode(a,"GAUZE-01","L1","B1")).getMessage());
 }
 @Test void m25ReservationGuardsOverlapsIdempotencyAndScopedCancellation(){
  ready();UUID vehicle=item("M25","VEHICLE","VEHICLE_AVAILABLE",
   "{\"title\":\"vehicle\",\"plate\":\"LTA-123\",\"vehicleType\":\"ambulance\"}");
  var service=svc();var a=actor(maker,"m25:assign","m25:cancel");
  Instant start=Instant.now().plus(3,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
  Instant end=start.plus(1,ChronoUnit.HOURS);
  var req=new OperationsConnectorService.ReserveVehicleRequest(vehicle,start,end,"RES-1");
  var saved=service.reserveVehicle(a,req);
  assertEquals(saved.reservationId(),service.reserveVehicle(a,req).reservationId());
  assertThrows(WorkflowViolation.class,()->service.reserveVehicle(a,
   new OperationsConnectorService.ReserveVehicleRequest(vehicle,start.plus(10,ChronoUnit.MINUTES),
   end.plus(1,ChronoUnit.HOURS),"RES-2")));
  var other=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m25:cancel"));
  assertThrows(WorkflowViolation.class,()->service.cancelReservation(other,saved.reservationId()));
  assertEquals("CANCELLED",service.cancelReservation(a,saved.reservationId()).status());
  var fresh=service.reserveVehicle(a,new OperationsConnectorService.ReserveVehicleRequest(
   vehicle,start,end,"RES-2"));
  assertEquals("RESERVED",fresh.status());
 }
 @Test void m29CompositeCursorNeverSkipsEqualTimestampPublication(){
  ready();
  Instant same=Instant.now().truncatedTo(ChronoUnit.SECONDS);
  UUID aId=UUID.randomUUID(),bId=UUID.randomUUID();
  for(UUID pub:List.of(aId,bId)){
   UUID config=item("M29","CONFIGURATION","PUBLISHED","{\"title\":\"ward config\"}");
   admin().update("INSERT INTO synqoria_operations.facility_publications "+
    "(publication_id,cell_id,facility_id,item_id,config_key,schema_version,item_version,"+
    "content_digest,published_by,published_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
    pub,cell,facility,config,"ward-"+pub,"1",1,"a".repeat(64),maker,Timestamp.from(same));
  }
  var service=svc();var grant=actor(maker,"m29:read");
  var first=service.publicationPage(grant,null,null,1);
  assertEquals(1,first.publications().size());
  var second=service.publicationPage(grant,first.nextCursor().at().toString(),
   first.nextCursor().id().toString(),1);
  assertEquals(1,second.publications().size());
  assertNotEquals(first.nextCursor().id(),second.nextCursor().id());
  assertThrows(WorkflowViolation.class,()->service.publicationPage(grant,same.toString(),null,5));
  var other=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m29:read"));
  assertTrue(service.publicationPage(other,null,null,10).publications().isEmpty());
 }
 @Test void m37LeaseIsAtomicScopedAndCannotClaimUndueOrAlreadyLeasedJob(){
  ready();
  UUID due=item("M37","MESSAGE","QUEUED","{\"title\":\"synthetic no-payload notice\"}");
  UUID later=item("M37","MESSAGE","QUEUED","{\"title\":\"future notice\"}");
  admin().update("INSERT INTO synqoria_operations.notification_jobs "+
   "(cell_id,facility_id,item_id,recipient_ref,template_ref,channel) "+
   "VALUES(?,?,?,?,?,?)",cell,facility,due,reviewer.toString(),"tmpl-1","internal");
  admin().update("INSERT INTO synqoria_operations.notification_jobs "+
   "(cell_id,facility_id,item_id,recipient_ref,template_ref,channel,next_attempt_at) "+
   "VALUES(?,?,?,?,?,?,clock_timestamp()+interval '1 day')",
   cell,facility,later,reviewer.toString(),"tmpl-1","sms");
  var service=svc();var worker=actor(maker,"m37:deliver");
  var leases=service.leaseNotifications(worker,10);
  assertEquals(1,leases.size());
  assertEquals(due,leases.getFirst().itemId());
  assertEquals(1,leases.getFirst().attempt());
  assertTrue(service.leaseNotifications(worker,10).isEmpty());
  var status=admin().queryForMap("SELECT state,delivery_ref,lease_id FROM "+
   "synqoria_operations.notification_jobs WHERE item_id=?",due);
  assertEquals("QUEUED",status.get("state"));assertNull(status.get("delivery_ref"));
  assertThrows(WorkflowViolation.class,()->service.leaseNotifications(actor(maker,"m37:read"),10));
  var foreign=new OperationWorkflow.Actor(maker,cell,UUID.randomUUID(),Set.of("m37:deliver"));
  assertTrue(service.leaseNotifications(foreign,10).isEmpty());
 }
}
