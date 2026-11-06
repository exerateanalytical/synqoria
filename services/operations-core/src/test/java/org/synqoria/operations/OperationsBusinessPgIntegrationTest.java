package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.synqoria.operations.domain.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real disposable PostgreSQL 18.6 cross-domain lifecycle and safety integration.
 * No fabricated hospital credential, clinical record, actual third-party delivery
 * or production acceptance is implied by these synthetic test fixtures.
 */
final class OperationsBusinessPgIntegrationTest {
 private static final UUID CELL=UUID.fromString("61000000-0000-4000-8000-000000000001");
 private static final UUID FAC=UUID.fromString("62000000-0000-4000-8000-000000000002");
 private static final UUID A=UUID.fromString("63000000-0000-4000-8000-000000000003");
 private static final UUID B=UUID.fromString("64000000-0000-4000-8000-000000000004");
 private static final UUID DRIVER=UUID.fromString("65000000-0000-4000-8000-000000000005");
 private DataSource runtime(){return new DriverManagerDataSource(
  System.getenv("SYNQORIA_OPS_TEST_JDBC_URL"),System.getenv("SYNQORIA_OPS_TEST_DB_USER"),
  System.getenv("SYNQORIA_OPS_TEST_DB_PASSWORD"));}
 private JdbcTemplate admin(){return new JdbcTemplate(new DriverManagerDataSource(
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_JDBC_URL"),
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_USER"),
  System.getenv("SYNQORIA_OPS_TEST_ADMIN_PASSWORD")));}
 private OperationsStore store(){
  DataSource ds=runtime();
  return new OperationsStore(new JdbcTemplate(ds),JsonMapper.builder().build(),
   new DataSourceTransactionManager(ds),"synqoria_operations_runtime");
 }
 private OperationWorkflow.Actor actor(UUID id,String...permissions){
  return new OperationWorkflow.Actor(id,CELL,FAC,Set.of(permissions));
 }
 private static Map<String,Object> data(Object...values){
  if(values.length%2!=0)throw new IllegalArgumentException("pairs_required");
  Map<String,Object> out=new LinkedHashMap<>();
  for(int i=0;i<values.length;i+=2)out.put((String)values[i],values[i+1]);
  return out;
 }
 private OperationWorkflow.Item create(OperationsStore s,String m,String recordType,
                                      OperationWorkflow.Actor actor,Map<String,Object>fields){
  return s.create(actor,m,new OperationsApi.NewItem(recordType,fields,UUID.randomUUID()));
 }
 private OperationWorkflow.Item next(OperationsStore s,String m,OperationWorkflow.Item item,
                      String action,OperationWorkflow.Actor actor,Map<String,Object>fields){
  return s.transition(actor,m,item.id(),
   new OperationsApi.TransitionItem(action,item.version(),fields,UUID.randomUUID()));
 }
 private void evidence(UUID item,String action,long version,String kind,String reference){
  admin().update("INSERT INTO synqoria_operations.external_evidence " +
   "(evidence_id,cell_id,facility_id,item_id,for_action,expected_version,evidence_kind," +
   "external_ref,issuer_ref,approver_id,valid_from,valid_until,digest_sha256) " +
   "VALUES(?,?,?,?,?,?,?,?,?,?,clock_timestamp()-interval '1 minute'," +
   "clock_timestamp()+interval '1 hour',repeat('b',64))",
   UUID.randomUUID(),CELL,FAC,item,action,version,kind,reference,"external-protocol-synthetic",B);
 }
 private long count(String table,UUID item){
  // Table is a fixed test-only literal selected by test source.
  return admin().queryForObject("SELECT count(*) FROM synqoria_operations."+table+" WHERE item_id=?",
   Long.class,item);
 }
 private static void ready(){
  Assumptions.assumeTrue("1".equals(System.getenv("SYNQORIA_OPS_PG_TEST")));
 }
 @Test void m21QualifiedSupplierOrderGoodsReceiptInvoiceMatchAndClosureAreAtomic(){
  ready();var store=store();
  var maker=actor(A,"m21:create","m21:submit","m21:order","m21:receive","m21:close");
  var review=actor(B,"m21:qualify","m21:approve","m21:match");
  var supplier=create(store,"M21","SUPPLIER",maker,data("title","Qualified hospital supplier",
   "registrationRef","RC-2026-041","taxRef","NIU-041","qualificationRef","QUAL-2026-041"));
  assertThrows(WorkflowViolation.class,()->next(store,"M21",supplier,"QUALIFY",review,Map.of()));
  evidence(supplier.id(),"QUALIFY",1,"supplier_qualified","QUAL-2026-041");
  supplier=next(store,"M21",supplier,"QUALIFY",review,Map.of());
  assertEquals("SUPPLIER_QUALIFIED",supplier.status());
  var order=create(store,"M21","REQUISITION",maker,data("title","Hospital general-purpose chairs",
   "supplierRef",supplier.id().toString(),"amountMinor",1250000,"currency","XAF",
   "costCentre","facilities"));
  order=next(store,"M21",order,"SUBMIT",maker,Map.of());
  order=next(store,"M21",order,"APPROVE",review,Map.of());
  order=next(store,"M21",order,"ORDER",maker,Map.of("purchaseRef","PO-2026-041"));
  assertEquals(1,count("procurement_orders",order.id()));
  evidence(order.id(),"RECEIVE",4,"purchase_receipt","GRN-2026-041");
  order=next(store,"M21",order,"RECEIVE",maker,Map.of("receiptRef","GRN-2026-041"));
  evidence(order.id(),"MATCH",5,"invoice_matched","INV-2026-041");
  order=next(store,"M21",order,"MATCH",review,Map.of("invoiceRef","INV-2026-041"));
  order=next(store,"M21",order,"CLOSE",maker,Map.of());
  assertEquals("CLOSED",order.status());
  String state=admin().queryForObject(
   "SELECT state FROM synqoria_operations.procurement_orders WHERE item_id=?",String.class,order.id());
  assertEquals("CLOSED",state);
  assertEquals(7,count("audit_events",order.id()));
  assertEquals(7,count("outbox",order.id()));
 }
 @Test void m23BiomedicalWorkRequiresOfflineAssetAndIndependentVerification(){
  ready();var s=store();
  var maker=actor(A,"m23:create","m23:maintenance");
  var inspector=actor(B,"m23:commission","m23:activate","m23:maintenance","m23:return","m23:retire");
  var asset=create(s,"M23","ASSET",maker,data("title","Ventilator equipment","serial","BIO-X-41",
   "locationRef","ICU-01","inspectionRef","BIO-INSPECT-41"));
  evidence(asset.id(),"COMMISSION",1,"biomed_inspection","BIO-INSPECT-41");
  asset=next(s,"M23",asset,"COMMISSION",inspector,Map.of());
  asset=next(s,"M23",asset,"ACTIVATE",inspector,Map.of());
  asset=next(s,"M23",asset,"TAKE_OFFLINE",inspector,Map.of());
  var work=create(s,"M23","WORK_ORDER",maker,data("title","Safety inspection",
   "assetRef",asset.id().toString(),"dueAt","2026-11-08T12:00:00Z"));
  work=next(s,"M23",work,"OPEN_WORK",maker,Map.of());
  work=next(s,"M23",work,"ASSIGN_WORK",inspector,Map.of("technicianRef","biomed-42"));
  evidence(work.id(),"COMPLETE_WORK",3,"biomed_inspection","WORK-INSPECT-41");
  work=next(s,"M23",work,"COMPLETE_WORK",inspector,Map.of("inspectionRef","WORK-INSPECT-41"));
  evidence(work.id(),"VERIFY_WORK",4,"calibration_safe","CAL-41");
  work=next(s,"M23",work,"VERIFY_WORK",inspector,Map.of("workOrderRef","WO-41"));
  assertEquals("WORK_VERIFIED",work.status());
  evidence(asset.id(),"RETURN_TO_SERVICE",4,"biomed_inspection","BIO-INSPECT-41");
  asset=next(s,"M23",asset,"RETURN_TO_SERVICE",inspector,
   Map.of("workOrderRef",work.id().toString()));
  assertEquals("ACTIVE",asset.status());
  assertEquals(1,count("maintenance_orders",work.id()));
  assertEquals("VERIFIED",admin().queryForObject(
   "SELECT state FROM synqoria_operations.maintenance_orders WHERE item_id=?",String.class,work.id()));
 }
 @Test void m25OnlyQualifiedAvailableVehiclesWithShiftVerifiedDriversMayDispatch(){
  ready();var s=store();var maker=actor(A,"m25:create");
  var dispatch=actor(B,"m25:assign","m25:dispatch","m25:arrive","m25:handover","m25:complete","m25:cancel");
  var vehicle=create(s,"M25","VEHICLE",maker,data("title","Ambulance 4x4","plate","LT-0456",
   "vehicleType","ambulance","inspectionRef","VEHICLE-CERT-41"));
  evidence(vehicle.id(),"QUALIFY_VEHICLE",1,"vehicle_driver_qualified","VEHICLE-CERT-41");
  vehicle=next(s,"M25",vehicle,"QUALIFY_VEHICLE",dispatch,Map.of());
  admin().update("INSERT INTO synqoria_operations.workforce_grants " +
   "(cell_id,facility_id,actor_id,grant_revision,permissions,active,shift_from,shift_until," +
   "valid_until,approved_by,source_ref) VALUES(?,?,?,1,ARRAY['m25:dispatch']::text[],true," +
   "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 hour'," +
   "clock_timestamp()+interval '2 hours',?,'trusted-driver-roster-synthetic')",
   CELL,FAC,DRIVER,B);
  var trip=create(s,"M25","TRIP",maker,data("title","Interfacility patient transfer",
   "vehicleRef",vehicle.id().toString(),"driverRef",DRIVER.toString(),
   "originRef","FAC-01","destinationRef","FAC-02","handoverRef","HAND-041"));
  var second=create(s,"M25","TRIP",maker,data("title","Second transfer request",
   "vehicleRef",vehicle.id().toString(),"driverRef",DRIVER.toString(),
   "originRef","FAC-01","destinationRef","FAC-03"));
  evidence(trip.id(),"ASSIGN",1,"vehicle_driver_qualified",vehicle.id().toString());
  trip=next(s,"M25",trip,"ASSIGN",dispatch,Map.of());
  evidence(second.id(),"ASSIGN",1,"vehicle_driver_qualified",vehicle.id().toString());
  var pending=second;
  assertThrows(Exception.class,()->next(s,"M25",pending,"ASSIGN",dispatch,Map.of()));
  evidence(trip.id(),"DISPATCH",2,"vehicle_driver_qualified",vehicle.id().toString());
  trip=next(s,"M25",trip,"DISPATCH",dispatch,Map.of());
  trip=next(s,"M25",trip,"ARRIVE",dispatch,Map.of());
  evidence(trip.id(),"HANDOVER",4,"transport_handover","HAND-041");
  trip=next(s,"M25",trip,"HANDOVER",dispatch,Map.of());
  trip=next(s,"M25",trip,"COMPLETE",dispatch,Map.of());
  assertEquals("COMPLETED",trip.status());
  second=next(s,"M25",second,"ASSIGN",dispatch,Map.of());
  assertEquals("ASSIGNED",second.status());
  assertEquals(1,admin().queryForObject(
   "SELECT count(*) FROM synqoria_operations.transport_assignments "+
   "WHERE cell_id=? AND facility_id=? AND vehicle_ref=? AND active",
   Long.class,CELL,FAC,vehicle.id().toString()));
 }
 @Test void m29FacilityPublishingHasAnImmutableReviewedVersionAndSupersession(){
  ready();var s=store();var maker=actor(A,"m29:create","m29:supersede");
  var reviewer=actor(B,"m29:review","m29:publish");
  var config=create(s,"M29","CONFIGURATION",maker,data("title","Radiology service",
   "locationRef","LOC-RAD-041","schemaVersion","v3","changeImpact","Ward directory, referrals"));
  config=next(s,"M29",config,"REVIEW",reviewer,Map.of());
  evidence(config.id(),"PUBLISH",2,"impact_approved","IMPACT-041");
  config=next(s,"M29",config,"PUBLISH",reviewer,Map.of());
  assertEquals(1,count("facility_publications",config.id()));
  config=next(s,"M29",config,"SUPERSEDE",maker,Map.of());
  assertEquals("SUPERSEDED",config.status());
  assertEquals(1,admin().queryForObject(
   "SELECT count(*) FROM synqoria_operations.facility_publications WHERE item_id=? "+
   "AND superseded_at IS NOT NULL",Long.class,config.id()));
  var service=create(s,"M29","SERVICE",maker,data("title","Diagnostic imaging service",
   "serviceCode","RAD-IMAGING","changeImpact","Billing and ordering"));
  service=next(s,"M29",service,"REVIEW_SERVICE",reviewer,Map.of());
  evidence(service.id(),"PUBLISH_SERVICE",2,"impact_approved","IMPACT-SERVICE-041");
  service=next(s,"M29",service,"PUBLISH_SERVICE",reviewer,Map.of());
  assertEquals("SERVICE_PUBLISHED",service.status());
 }
 @Test void m31DocumentsRequirePublishedTemplateAndAppendOnlySignedAmendmentLineage(){
  ready();var s=store();var maker=actor(A,"m31:create");
  var reviewer=actor(B,"m31:approve","m31:publish","m31:sign","m31:amend");
  String digestA="a".repeat(64),digestB="b".repeat(64);
  var template=create(s,"M31","TEMPLATE",maker,data("title","Discharge form template",
   "schemaVersion","3.0","contentDigest",digestA,"storageRef","s3-template-041"));
  template=next(s,"M31",template,"APPROVE",reviewer,Map.of());
  template=next(s,"M31",template,"PUBLISH",reviewer,Map.of());
  var doc=create(s,"M31","DOCUMENT",maker,data("title","Verified document specimen",
   "templateRef",template.id().toString(),"contentDigest",digestA,"storageRef","s3-doc-v1"));
  evidence(doc.id(),"SIGN",1,"verified_signature","SIG-041");
  doc=next(s,"M31",doc,"SIGN",reviewer,Map.of("signatureRef","SIG-041"));
  evidence(doc.id(),"AMEND",2,"document_lineage","SIGNED-041");
  doc=next(s,"M31",doc,"AMEND",reviewer,
   Map.of("previousRef","SIGNED-041","amendmentReason","Corrections reviewed",
   "contentDigest",digestB,"storageRef","s3-doc-v2"));
  evidence(doc.id(),"RESIGN",3,"verified_signature","SIG-042");
  doc=next(s,"M31",doc,"RESIGN",reviewer,Map.of("signatureRef","SIG-042"));
  assertEquals("DOCUMENT_SIGNED",doc.status());
  assertEquals(3,count("document_versions",doc.id()));
  assertThrows(Exception.class,()->new JdbcTemplate(runtime()).update(
   "DELETE FROM synqoria_operations.document_versions WHERE item_id=?",doc.id()));
 }
 @Test void m37DeliveryReceiptIsNotAcknowledgementAndOnlyNamedRecipientCanAccept(){
  ready();var s=store();var maker=actor(A,"m37:create","m37:acknowledge");
  var recipient=actor(B,"m37:deliver","m37:acknowledge","m37:approve","m37:activate");
  var notification=create(s,"M37","MESSAGE",maker,data("title","Equipment maintenance",
   "recipientRef",B.toString(),"templateRef","TEMPLATE-MAINT-041","channel","internal"));
  assertEquals(1,count("notification_jobs",notification.id()));
  evidence(notification.id(),"DELIVER",1,"delivery_receipt","DELIVER-041");
  notification=next(s,"M37",notification,"DELIVER",recipient,Map.of("deliveryRef","DELIVER-041"));
  var delivered=notification;
  assertThrows(WorkflowViolation.class,()->next(s,"M37",delivered,"ACKNOWLEDGE",maker,Map.of()));
  notification=next(s,"M37",notification,"ACKNOWLEDGE",recipient,Map.of());
  assertEquals("ACKNOWLEDGED",notification.status());
  var route=create(s,"M37","ROUTE",maker,data("title","Escalation routing",
   "routingRef","ESC-041","retryPolicy","BACKOFF-3","channel","internal"));
  route=next(s,"M37",route,"REVIEW_ROUTE",recipient,Map.of());
  route=next(s,"M37",route,"ACTIVATE_ROUTE",recipient,Map.of());
  assertEquals("ROUTE_ACTIVE",route.status());
  assertEquals("ACKNOWLEDGED",admin().queryForObject(
   "SELECT state FROM synqoria_operations.notification_jobs WHERE item_id=?",String.class,notification.id()));
 }
}
