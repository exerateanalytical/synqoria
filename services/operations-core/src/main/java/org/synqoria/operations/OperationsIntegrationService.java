package org.synqoria.operations;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * Bounded M21/M22/M25/M29/M37 integration operations.
 * Reuses OperationsStore's restrictive transaction, verified current workforce
 * authorization (at the controller), and PostgreSQL FORCE RLS. Does not synthesize
 * external supplier, stock-adjustment, vehicle GPS, or delivery-provider evidence.
 */
@Service
public final class OperationsIntegrationService {
 private final OperationsStore store;
 private final JdbcTemplate jdbc;
 public OperationsIntegrationService(OperationsStore store,JdbcTemplate jdbc){
  this.store=Objects.requireNonNull(store);this.jdbc=Objects.requireNonNull(jdbc);
 }
 private static void permission(OperationWorkflow.Actor a,String grant){a.require(grant);}
 private static void bounded(int limit){if(limit<1||limit>100)throw new WorkflowViolation("invalid_limit");}
 private static void code(String code){
  if(code==null||!code.matches("[A-Za-z0-9_.:/-]{1,160}"))throw new WorkflowViolation("invalid_reference");
 }
 public record ProcurementStatus(UUID itemId,String state,String purchaseRef,String receiptRef,
  String invoiceRef,long amountMinor,String currency,String disposition){}
 /** M21: immutable, cell-scoped purchase/receipt/invoice state; never claims external finance verification. */
 public List<ProcurementStatus> procurementStatus(OperationWorkflow.Actor actor,int limit){
  permission(actor,"m21:read");bounded(limit);
  return store.scoped(actor,()->jdbc.query(
   "SELECT p.item_id,p.state,p.purchase_ref,p.receipt_ref,p.invoice_ref,p.amount_minor,p.currency "+
   "FROM synqoria_operations.procurement_orders p "+
   "WHERE p.cell_id=? AND p.facility_id=? ORDER BY p.ordered_at DESC,p.item_id LIMIT ?",
   (rs,i)->{
    String state=rs.getString("state");
    String disposition= switch(state){
     case "ORDERED" -> "AWAITING_GOODS_RECEIPT";
     case "RECEIVED" -> "AWAITING_INVOICE_MATCH";
     case "MATCHED" -> "INTERNAL_MATCH_RECORDED";
     case "CLOSED" -> "INTERNAL_CLOSURE_RECORDED";
     default -> "UNKNOWN_REQUIRES_REVIEW";
    };
    return new ProcurementStatus(rs.getObject("item_id",UUID.class),state,rs.getString("purchase_ref"),
     rs.getString("receipt_ref"),rs.getString("invoice_ref"),rs.getLong("amount_minor"),
     rs.getString("currency").trim(),disposition);
   },actor.cell(),actor.facility(),limit));
 }
 public record StockCount(UUID countId,UUID itemId,String lot,String bin,long expectedOnHand,
  long expectedReserved,long countedOnHand,long stockVersion,String state,UUID requestedBy,
  UUID reviewedBy,long variance){}
 public record CountRequest(String lot,String bin,long countedOnHand,UUID commandId){}
 private StockCount fromCount(java.sql.ResultSet rs)throws java.sql.SQLException{
  long expected=rs.getLong("expected_on_hand"),counted=rs.getLong("counted_on_hand");
  return new StockCount(rs.getObject("count_id",UUID.class),rs.getObject("item_id",UUID.class),
   rs.getString("lot_code"),rs.getString("bin_code"),expected,rs.getLong("expected_reserved"),
   counted,rs.getLong("stock_version"),rs.getString("state"),
   rs.getObject("requested_by",UUID.class),rs.getObject("reviewed_by",UUID.class),counted-expected);
 }
 private StockCount oneCount(UUID id){
  var rows=jdbc.query("SELECT * FROM synqoria_operations.stock_counts WHERE count_id=?",
   (rs,n)->fromCount(rs),id);
  if(rows.size()!=1)throw new WorkflowViolation("not_found");
  return rows.getFirst();
 }
 /**
  * M22: actual barcode/lot/bin cycle-count snapshot, never a direct stock adjustment.
  * One idempotency key per workforce actor; review must be independent of counter.
  */
 public StockCount captureCount(OperationWorkflow.Actor actor,UUID stockItem,CountRequest req){
  permission(actor,"m22:count");
  if(stockItem==null||req==null||req.commandId()==null||req.countedOnHand()<0||
    req.countedOnHand()>1_000_000_000)throw new WorkflowViolation("invalid_stock_count");
  code(req.lot());code(req.bin());
  return store.scoped(actor,()->{
   var prev=jdbc.query("SELECT * FROM synqoria_operations.stock_counts "+
    "WHERE cell_id=? AND facility_id=? AND requested_by=? AND command_id=? FOR UPDATE",
    (rs,n)->fromCount(rs),actor.cell(),actor.facility(),actor.actor(),req.commandId());
   if(!prev.isEmpty()){
    var c=prev.getFirst();
    if(!stockItem.equals(c.itemId())||!req.lot().equals(c.lot())||
      !req.bin().equals(c.bin())||req.countedOnHand()!=c.countedOnHand())
     throw new WorkflowViolation("idempotency_collision");
    return c;
   }
   var item=jdbc.queryForList("SELECT record_type,status FROM synqoria_operations.items "+
    "WHERE id=? AND cell_id=? AND facility_id=? AND module_id='M22'",
    stockItem,actor.cell(),actor.facility());
   if(item.size()!=1)throw new WorkflowViolation("not_found");
   if(!"STOCK_ITEM".equals(item.getFirst().get("record_type"))||
     !"AVAILABLE".equals(item.getFirst().get("status")))throw new WorkflowViolation("stock_not_released");
   var b=jdbc.queryForList("SELECT on_hand,reserved,version FROM synqoria_operations.stock_balances "+
    "WHERE cell_id=? AND facility_id=? AND item_id=? AND lot_code=? AND bin_code=? FOR UPDATE",
    actor.cell(),actor.facility(),stockItem,req.lot(),req.bin());
   if(b.size()!=1)throw new WorkflowViolation("stock_lot_not_found");
   var stock=b.getFirst();UUID count=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_operations.stock_counts "+
    "(count_id,cell_id,facility_id,item_id,lot_code,bin_code,expected_on_hand,expected_reserved,"+
    "counted_on_hand,stock_version,requested_by,command_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
    count,actor.cell(),actor.facility(),stockItem,req.lot(),req.bin(),
    ((Number)stock.get("on_hand")).longValue(),((Number)stock.get("reserved")).longValue(),
    req.countedOnHand(),((Number)stock.get("version")).longValue(),actor.actor(),req.commandId());
   return oneCount(count);
  });
 }
 /** Maker-checker review records variance but cannot alter the immutable stock journal. */
 public StockCount reviewCount(OperationWorkflow.Actor actor,UUID countId,boolean accept){
  permission(actor,"m22:adjust");
  if(countId==null)throw new WorkflowViolation("invalid_stock_count");
  return store.scoped(actor,()->{
   var r=jdbc.query("SELECT * FROM synqoria_operations.stock_counts WHERE count_id=? FOR UPDATE",
    (rs,n)->fromCount(rs),countId);
   if(r.size()!=1)throw new WorkflowViolation("not_found");
   var c=r.getFirst();
   if(!"PENDING".equals(c.state()))throw new WorkflowViolation("count_already_reviewed");
   if(c.requestedBy().equals(actor.actor()))throw new WorkflowViolation("maker_checker_denied");
   Long v=jdbc.queryForObject("SELECT version FROM synqoria_operations.stock_balances "+
    "WHERE cell_id=? AND facility_id=? AND item_id=? AND lot_code=? AND bin_code=?",
    Long.class,actor.cell(),actor.facility(),c.itemId(),c.lot(),c.bin());
   if(v==null||v!=c.stockVersion())throw new WorkflowViolation("stock_changed_since_count");
   // Recorded variance is a finding, NOT authorization to mutate stock.
   jdbc.update("UPDATE synqoria_operations.stock_counts SET state=?,reviewed_by=?,reviewed_at=clock_timestamp() "+
    "WHERE count_id=? AND cell_id=? AND facility_id=? AND state='PENDING'",
    accept?"REVIEWED":"REJECTED",actor.actor(),countId,actor.cell(),actor.facility());
   return oneCount(countId);
  });
 }
 /** M25: real vehicle availability; active assignments always override a vehicle's "available" label. */
 public List<Map<String,Object>> availableVehicles(OperationWorkflow.Actor actor,int limit){
  permission(actor,"m25:read");bounded(limit);
  return store.scoped(actor,()->jdbc.queryForList(
   "SELECT v.id AS vehicle_id,v.fields->>'plate' AS plate,v.fields->>'vehicleType' AS vehicle_type,"+
   "v.version FROM synqoria_operations.items v WHERE v.cell_id=? AND v.facility_id=? "+
   "AND v.module_id='M25' AND v.record_type='VEHICLE' AND v.status='VEHICLE_AVAILABLE' "+
   "AND NOT EXISTS(SELECT 1 FROM synqoria_operations.transport_assignments t "+
   "WHERE t.cell_id=v.cell_id AND t.facility_id=v.facility_id "+
   "AND t.vehicle_ref=v.id::text AND t.active) ORDER BY v.updated_at DESC,v.id LIMIT ?",
   actor.cell(),actor.facility(),limit));
 }
 /** M29: bounded immutable publication feed; last seen timestamp is an exclusive high-water mark. */
 public List<Map<String,Object>> publishedChanges(OperationWorkflow.Actor actor,String after,int limit){
  permission(actor,"m29:read");bounded(limit);
  Instant since;
  try{since=after==null?Instant.EPOCH:Instant.parse(after);}
  catch(DateTimeParseException e){throw new WorkflowViolation("invalid_cursor");}
  return store.scoped(actor,()->jdbc.queryForList(
   "SELECT publication_id,item_id,config_key,schema_version,item_version,content_digest,"+
   "published_by,published_at,superseded_at FROM synqoria_operations.facility_publications "+
   "WHERE cell_id=? AND facility_id=? AND published_at>? "+
   "ORDER BY published_at,publication_id LIMIT ?",
   actor.cell(),actor.facility(),java.sql.Timestamp.from(since),limit));
 }
 /** M37: due, cell-scoped delivery candidates, without message body, bearer or clinical data. */
 public List<Map<String,Object>> dueNotifications(OperationWorkflow.Actor actor,int limit){
  permission(actor,"m37:deliver");bounded(limit);
  return store.scoped(actor,()->jdbc.queryForList(
   "SELECT n.item_id,n.channel,n.state,n.recipient_ref,n.template_ref,"+
   "n.delivery_attempts,n.next_attempt_at FROM synqoria_operations.notification_jobs n "+
   "JOIN synqoria_operations.items i ON i.id=n.item_id AND i.cell_id=n.cell_id "+
   "AND i.facility_id=n.facility_id WHERE n.cell_id=? AND n.facility_id=? "+
   "AND i.module_id='M37' AND i.record_type='MESSAGE' "+
   "AND n.state IN ('QUEUED','FAILED','RETRYING') AND n.delivery_attempts<20 "+
   "AND (n.next_attempt_at IS NULL OR n.next_attempt_at<=clock_timestamp()) "+
   "ORDER BY n.next_attempt_at NULLS FIRST,n.created_at,n.item_id LIMIT ?",
   actor.cell(),actor.facility(),limit));
 }
}
