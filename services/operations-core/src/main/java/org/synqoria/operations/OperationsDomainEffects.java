package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.synqoria.operations.domain.*;

/**
 * Transactional downstream tables for seven administrative bounded contexts.
 * Called only inside OperationsStore's scoped, RLS-verified JDBC transaction.
 * All SQL is static/parameterized. Any failed invariant rolls back aggregate,
 * audit, idempotency and outbox together.
 */
final class OperationsDomainEffects {
 private OperationsDomainEffects(){}
 private static String str(OperationWorkflow.Item item,String field){
  Object o=item.fields().get(field);
  if(!(o instanceof String value)||value.isBlank())
   throw new WorkflowViolation("required_business_field_missing");
  return value;
 }
 private static UUID ref(OperationWorkflow.Item item,String field){
  try{return UUID.fromString(str(item,field));}
  catch(IllegalArgumentException e){throw new WorkflowViolation("invalid_resource_reference");}
 }
 private static Instant time(OperationWorkflow.Item item,String field){
  try{return Instant.parse(str(item,field));}
  catch(RuntimeException e){throw new WorkflowViolation("invalid_work_order_time");}
 }
 private static long amount(OperationWorkflow.Item item,String field){
  Object raw=item.fields().get(field);
  if(!(raw instanceof Number number)||number.longValue()<0)
   throw new WorkflowViolation("invalid_amount");
  return number.longValue();
 }
 private static int update(JdbcTemplate jdbc,String sql,Object... args){
  int count=jdbc.update(sql,args);
  if(count!=1)throw new WorkflowViolation("downstream_state_inconsistent");
  return count;
 }
 static void onCreate(JdbcTemplate jdbc,OperationWorkflow.Item item){
  if(item.module().equals("M37")&&item.recordType().equals("MESSAGE")){
   update(jdbc,"INSERT INTO synqoria_operations.notification_jobs "+
    "(cell_id,facility_id,item_id,recipient_ref,template_ref,channel) VALUES (?,?,?,?,?,?)",
    item.cell(),item.facility(),item.id(),str(item,"recipientRef"),str(item,"templateRef"),
    str(item,"channel"));
  }
 }
 static void onTransition(JdbcTemplate jdbc,OperationWorkflow.Item before,
                          OperationWorkflow.Item after,String action){
  String module=after.module(),type=after.recordType();
  if(module.equals("M21")&&type.equals("REQUISITION"))procurement(jdbc,after,action);
  if(module.equals("M23")&&type.equals("WORK_ORDER"))maintenance(jdbc,after,action);
  if(module.equals("M23")&&type.equals("ASSET")&&action.equals("RETURN_TO_SERVICE")){
   UUID work=ref(after,"workOrderRef");
   Long verified=jdbc.queryForObject(
    "SELECT count(*) FROM synqoria_operations.maintenance_orders " +
    "WHERE cell_id=? AND facility_id=? AND item_id=? AND asset_ref=? AND state='VERIFIED'",
    Long.class,after.cell(),after.facility(),work,after.id());
   if(verified==null||verified!=1)throw new WorkflowViolation("maintenance_clearance_not_verified");
  }
  if(module.equals("M25")&&type.equals("TRIP"))transport(jdbc,after,action);
  if(module.equals("M29"))configuration(jdbc,after,action);
  if(module.equals("M31")&&type.equals("DOCUMENT"))document(jdbc,after,action);
  if(module.equals("M37")&&type.equals("MESSAGE"))notification(jdbc,after,action);
 }
 private static void procurement(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  switch(action){
   case "ORDER" -> {
    UUID supplier=ref(item,"supplierRef");
    Long qualified=jdbc.queryForObject(
     "SELECT count(*) FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? "+
     "AND id=? AND module_id='M21' AND record_type='SUPPLIER' AND status='SUPPLIER_QUALIFIED'",
     Long.class,item.cell(),item.facility(),supplier);
    if(qualified==null||qualified!=1)throw new WorkflowViolation("supplier_not_qualified");
    update(jdbc,"INSERT INTO synqoria_operations.procurement_orders "+
     "(cell_id,facility_id,item_id,supplier_ref,purchase_ref,amount_minor,currency) "+
     "VALUES(?,?,?,?,?,?,?)",
     item.cell(),item.facility(),item.id(),supplier.toString(),str(item,"purchaseRef"),
     amount(item,"amountMinor"),str(item,"currency"));
   }
   case "RECEIVE" -> update(jdbc,"UPDATE synqoria_operations.procurement_orders "+
    "SET receipt_ref=?,state='RECEIVED' WHERE item_id=? AND cell_id=? AND facility_id=? AND state='ORDERED'",
    str(item,"receiptRef"),item.id(),item.cell(),item.facility());
   case "MATCH" -> update(jdbc,"UPDATE synqoria_operations.procurement_orders "+
    "SET invoice_ref=?,state='MATCHED' WHERE item_id=? AND cell_id=? AND facility_id=? AND state='RECEIVED'",
    str(item,"invoiceRef"),item.id(),item.cell(),item.facility());
   case "CLOSE" -> update(jdbc,"UPDATE synqoria_operations.procurement_orders "+
    "SET state='CLOSED' WHERE item_id=? AND cell_id=? AND facility_id=? AND state='MATCHED'",
    item.id(),item.cell(),item.facility());
   default -> {}
  }
 }
 private static void maintenance(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  switch(action){
   case "OPEN_WORK" -> {
    UUID asset=ref(item,"assetRef");
    Long count=jdbc.queryForObject(
     "SELECT count(*) FROM synqoria_operations.items WHERE id=? AND cell_id=? AND facility_id=? "+
     "AND module_id='M23' AND record_type='ASSET' AND status='MAINTENANCE'",
     Long.class,asset,item.cell(),item.facility());
    if(count==null||count!=1)throw new WorkflowViolation("asset_not_offline_for_work");
    update(jdbc,"INSERT INTO synqoria_operations.maintenance_orders "+
     "(cell_id,facility_id,item_id,asset_ref,due_at) VALUES(?,?,?,?,?)",
     item.cell(),item.facility(),item.id(),asset,java.sql.Timestamp.from(time(item,"dueAt")));
   }
   case "ASSIGN_WORK" -> update(jdbc,"UPDATE synqoria_operations.maintenance_orders "+
    "SET technician_ref=?,state='ASSIGNED' WHERE item_id=? AND cell_id=? AND facility_id=? AND state='OPEN'",
    str(item,"technicianRef"),item.id(),item.cell(),item.facility());
   case "COMPLETE_WORK" -> update(jdbc,"UPDATE synqoria_operations.maintenance_orders "+
    "SET inspection_ref=?,state='COMPLETED' WHERE item_id=? AND cell_id=? AND facility_id=? AND state='ASSIGNED'",
    str(item,"inspectionRef"),item.id(),item.cell(),item.facility());
   case "VERIFY_WORK" -> update(jdbc,"UPDATE synqoria_operations.maintenance_orders "+
    "SET state='VERIFIED',verified_at=clock_timestamp() "+
    "WHERE item_id=? AND cell_id=? AND facility_id=? AND state='COMPLETED'",
    item.id(),item.cell(),item.facility());
   default -> {}
  }
 }
 private static void transport(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  switch(action){
   case "ASSIGN" -> {
    UUID vehicle=ref(item,"vehicleRef");
    Long ready=jdbc.queryForObject(
     "SELECT count(*) FROM synqoria_operations.items WHERE id=? AND cell_id=? AND facility_id=? "+
     "AND module_id='M25' AND record_type='VEHICLE' AND status='VEHICLE_AVAILABLE'",
     Long.class,vehicle,item.cell(),item.facility());
    if(ready==null||ready!=1)throw new WorkflowViolation("vehicle_not_qualified");
    UUID driver=ref(item,"driverRef");
    Long onShift=jdbc.queryForObject(
     "SELECT count(*) FROM synqoria_operations.workforce_grants WHERE cell_id=? AND facility_id=? " +
     "AND actor_id=? AND active AND shift_from<=clock_timestamp() AND " +
     "shift_until>clock_timestamp() AND valid_until>clock_timestamp()",
     Long.class,item.cell(),item.facility(),driver);
    if(onShift==null||onShift!=1)throw new WorkflowViolation("driver_shift_not_authorized");
    update(jdbc,"INSERT INTO synqoria_operations.transport_assignments "+
     "(cell_id,facility_id,item_id,vehicle_ref,driver_ref,origin_ref,destination_ref) VALUES(?,?,?,?,?,?,?)",
     item.cell(),item.facility(),item.id(),vehicle.toString(),str(item,"driverRef"),
     str(item,"originRef"),str(item,"destinationRef"));
   }
   case "DISPATCH","ARRIVE","HANDOVER" -> update(jdbc,"UPDATE synqoria_operations.transport_assignments "+
     "SET state=? WHERE item_id=? AND cell_id=? AND facility_id=? AND active",
     switch(action){case "DISPATCH"->"DISPATCHED";case "ARRIVE"->"ARRIVED";default->"HANDED_OVER";},
     item.id(),item.cell(),item.facility());
   case "COMPLETE","CANCEL" -> {
    // CANCEL is valid even before ASSIGN; no existing vehicle reservation then.
    jdbc.update("UPDATE synqoria_operations.transport_assignments "+
     "SET state=?,active=false,ended_at=clock_timestamp() "+
     "WHERE item_id=? AND cell_id=? AND facility_id=? AND active",
     action.equals("COMPLETE")?"COMPLETED":"CANCELLED",
     item.id(),item.cell(),item.facility());
   }
   case "ESCALATE" -> update(jdbc,"UPDATE synqoria_operations.transport_assignments "+
    "SET state='ESCALATED' WHERE item_id=? AND cell_id=? AND facility_id=? AND active",
    item.id(),item.cell(),item.facility());
   default -> {}
  }
 }
 private static void configuration(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  if(Set.of("PUBLISH","PUBLISH_SERVICE").contains(action)){
   String key=item.recordType().equals("SERVICE")?str(item,"serviceCode"):
    item.fields().containsKey("serviceCode")?str(item,"serviceCode"):str(item,"locationRef");
   String version=item.recordType().equals("SERVICE")?
    String.valueOf(item.version()):str(item,"schemaVersion");
   update(jdbc,"INSERT INTO synqoria_operations.facility_publications "+
    "(publication_id,cell_id,facility_id,item_id,config_key,schema_version,item_version,content_digest,published_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?)",
    UUID.randomUUID(),item.cell(),item.facility(),item.id(),key,version,
    item.version(),contentDigest(item),item.lastActor());
  }
  if(Set.of("SUPERSEDE","DEPRECATE_SERVICE").contains(action)){
   update(jdbc,"UPDATE synqoria_operations.facility_publications "+
    "SET superseded_at=clock_timestamp() "+
    "WHERE item_id=? AND cell_id=? AND facility_id=? AND superseded_at IS NULL",
    item.id(),item.cell(),item.facility());
  }
 }
 private static String contentDigest(OperationWorkflow.Item item){
  try{
   byte[] value=java.security.MessageDigest.getInstance("SHA-256").digest(
    new TreeMap<>(item.fields()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   return HexFormat.of().formatHex(value);
  }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable");}
 }
 private static void document(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  if(!Set.of("SIGN","AMEND","RESIGN").contains(action))return;
  if(action.equals("SIGN")){
   UUID template=ref(item,"templateRef");
   Long approved=jdbc.queryForObject(
    "SELECT count(*) FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? " +
    "AND id=? AND module_id='M31' AND record_type='TEMPLATE' AND status='PUBLISHED'",
    Long.class,item.cell(),item.facility(),template);
   if(approved==null||approved!=1)throw new WorkflowViolation("document_template_not_published");
  }
  String disposition=action.equals("SIGN")?"SIGNED":action.equals("AMEND")?"AMENDMENT":"RESIGNED";
  String digest=str(item,"contentDigest");
  String signature=action.equals("AMEND")?null:str(item,"signatureRef");
  String previous=action.equals("AMEND")?str(item,"previousRef"):null;
  update(jdbc,"INSERT INTO synqoria_operations.document_versions "+
   "(version_id,cell_id,facility_id,item_id,document_version,disposition,content_digest," +
   "storage_ref,signature_ref,previous_ref,actor_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),item.cell(),item.facility(),item.id(),item.version(),disposition,
   digest,str(item,"storageRef"),signature,previous,item.lastActor());
 }
 private static void notification(JdbcTemplate jdbc,OperationWorkflow.Item item,String action){
  String state=switch(action){
   case "DELIVER" -> "DELIVERED";case "ACKNOWLEDGE"->"ACKNOWLEDGED";
   case "FAIL"->"FAILED";case "RETRY"->"RETRYING";
   case "ESCALATE"->"ESCALATED";default->null;
  };
  if(state==null)return;
  if(action.equals("ACKNOWLEDGE")&&!item.lastActor().equals(ref(item,"recipientRef")))
   throw new WorkflowViolation("notification_recipient_mismatch");
  String delivery=action.equals("DELIVER")?str(item,"deliveryRef"):null;
  UUID acknowledged=action.equals("ACKNOWLEDGE")?item.lastActor():null;
  update(jdbc,"UPDATE synqoria_operations.notification_jobs "+
   "SET state=?,delivery_ref=coalesce(?,delivery_ref),"+
   "acknowledged_by=coalesce(?,acknowledged_by),"+
   "delivery_attempts=delivery_attempts+?,updated_at=clock_timestamp() "+
   "WHERE item_id=? AND cell_id=? AND facility_id=?",
   state,delivery,acknowledged,action.equals("RETRY")?1:0,
   item.id(),item.cell(),item.facility());
 }
}
