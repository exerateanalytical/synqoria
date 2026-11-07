package org.synqoria.operations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * Durable handoffs for existing M21/M22/M25/M29/M37 production-domain services.
 * Reuses OperationsStore.scoped(): one audited least-privileged runtime transaction,
 * hospital-cell/facility RLS, never consumer-provided cell, no external claims.
 */
@Service
public final class OperationsConnectorService {
 private final OperationsStore store;
 private final JdbcTemplate jdbc;
 public OperationsConnectorService(OperationsStore store, JdbcTemplate jdbc){
  this.store=Objects.requireNonNull(store);this.jdbc=Objects.requireNonNull(jdbc);
 }
 private static void limit(int n){if(n<1||n>100)throw new WorkflowViolation("invalid_limit");}
 private static void code(String s){
  if(s==null||!s.matches("[A-Za-z0-9_.:/-]{1,160}"))
   throw new WorkflowViolation("invalid_reference");
 }
 private static String hash(String s){
  try{
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
    .digest(s.getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable",e);}
 }

 public record ProcurementExport(UUID exportId,UUID itemId,long itemVersion,String purchaseRef,
  String receiptRef,String invoiceRef,long amountMinor,String currency,String digest,String state){}
 private ProcurementExport exportRow(java.sql.ResultSet rs,int index)throws java.sql.SQLException{
  return new ProcurementExport(rs.getObject("export_id",UUID.class),
   rs.getObject("item_id",UUID.class),rs.getLong("item_version"),
   rs.getString("purchase_ref"),rs.getString("receipt_ref"),rs.getString("invoice_ref"),
   rs.getLong("amount_minor"),rs.getString("currency").trim(),rs.getString("export_digest"),
   rs.getString("state"));
 }
 /**
  * M21 approved internal invoice match is staged in an immutable finance export
  * queue. No bank settlement, supplier approval or external acknowledgment is claimed.
  * Repeated calls return the same export at the same aggregate version.
  */
 public ProcurementExport enqueueProcurement(OperationWorkflow.Actor actor,UUID itemId){
  actor.require("m21:close");
  if(itemId==null)throw new WorkflowViolation("invalid_reference");
  return store.scoped(actor,()->{
   var rows=jdbc.queryForList(
    "SELECT i.version,i.status,p.state,p.purchase_ref,p.receipt_ref,p.invoice_ref,"+
    "p.amount_minor,p.currency FROM synqoria_operations.items i "+
    "JOIN synqoria_operations.procurement_orders p ON p.item_id=i.id "+
    "AND p.cell_id=i.cell_id AND p.facility_id=i.facility_id "+
    "WHERE i.cell_id=? AND i.facility_id=? AND i.id=? AND i.module_id='M21' "+
    "AND i.record_type='REQUISITION' FOR UPDATE OF i",
    actor.cell(),actor.facility(),itemId);
   if(rows.size()!=1)throw new WorkflowViolation("not_found");
   var row=rows.getFirst();
   String state=(String)row.get("state");
   if(!Set.of("MATCHED","CLOSED").contains(state))throw new WorkflowViolation("invoice_not_matched");
   String purchase=(String)row.get("purchase_ref"),receipt=(String)row.get("receipt_ref"),
    invoice=(String)row.get("invoice_ref"),currency=((String)row.get("currency")).trim();
   if(receipt==null||invoice==null)throw new WorkflowViolation("invoice_not_matched");
   long amount=((Number)row.get("amount_minor")).longValue();
   long version=((Number)row.get("version")).longValue();
   String digest=hash(actor.cell()+"|"+actor.facility()+"|"+itemId+"|"+version+
    "|"+purchase+"|"+receipt+"|"+invoice+"|"+amount+"|"+currency);
   jdbc.update("INSERT INTO synqoria_operations.procurement_export_queue "+
    "(export_id,cell_id,facility_id,item_id,item_version,purchase_ref,receipt_ref,invoice_ref,"+
    "amount_minor,currency,export_digest) VALUES (?,?,?,?,?,?,?,?,?,?,?) "+
    "ON CONFLICT (cell_id,facility_id,item_id,item_version) DO NOTHING",
    UUID.randomUUID(),actor.cell(),actor.facility(),itemId,version,purchase,receipt,invoice,
    amount,currency,digest);
   var queued=jdbc.query(
    "SELECT * FROM synqoria_operations.procurement_export_queue "+
    "WHERE cell_id=? AND facility_id=? AND item_id=? AND item_version=?",
    this::exportRow,actor.cell(),actor.facility(),itemId,version);
   if(queued.size()!=1 || !digest.equals(queued.getFirst().digest()))
    throw new WorkflowViolation("export_snapshot_mismatch");
   return queued.getFirst();
  });
 }
 public List<ProcurementExport> pendingProcurement(OperationWorkflow.Actor actor,int max){
  actor.require("m21:read");limit(max);
  return store.scoped(actor,()->jdbc.query(
   "SELECT * FROM synqoria_operations.procurement_export_queue "+
   "WHERE cell_id=? AND facility_id=? AND state='PENDING' "+
   "ORDER BY created_at,export_id LIMIT ?",this::exportRow,
   actor.cell(),actor.facility(),max));
 }

 public record BarcodeStock(UUID itemId,String sku,String lot,String bin,long onHand,
  long reserved,long stockVersion){}
 /**
  * M22 only identifies an exact SKU/lot/bin stock row; no scanner is trusted
  * as an authority and a duplicate SKU mapping is denied, not guessed.
  */
 public BarcodeStock resolveBarcode(OperationWorkflow.Actor actor,String sku,String lot,String bin){
  actor.require("m22:read");code(sku);code(lot);code(bin);
  return store.scoped(actor,()->{
   var found=jdbc.query(
    "SELECT i.id,i.fields->>'sku' AS sku,b.lot_code,b.bin_code,b.on_hand,b.reserved,b.version "+
    "FROM synqoria_operations.items i "+
    "JOIN synqoria_operations.stock_balances b ON b.item_id=i.id "+
    "AND b.cell_id=i.cell_id AND b.facility_id=i.facility_id "+
    "WHERE i.cell_id=? AND i.facility_id=? AND i.module_id='M22' "+
    "AND i.record_type='STOCK_ITEM' AND i.fields->>'sku'=? "+
    "AND b.lot_code=? AND b.bin_code=? LIMIT 2",
    (rs,i)->new BarcodeStock(rs.getObject("id",UUID.class),rs.getString("sku"),
     rs.getString("lot_code"),rs.getString("bin_code"),rs.getLong("on_hand"),
     rs.getLong("reserved"),rs.getLong("version")),
    actor.cell(),actor.facility(),sku,lot,bin);
   if(found.isEmpty())throw new WorkflowViolation("not_found");
   if(found.size()!=1)throw new WorkflowViolation("ambiguous_stock_identifier");
   return found.getFirst();
  });
 }

 public record VehicleReservation(UUID reservationId,UUID vehicleId,Instant startAt,
  Instant endAt,String reservationRef,String status){}
 private VehicleReservation reservationRow(java.sql.ResultSet rs,int i)throws java.sql.SQLException{
  return new VehicleReservation(rs.getObject("reservation_id",UUID.class),
   rs.getObject("vehicle_id",UUID.class),rs.getTimestamp("start_at").toInstant(),
   rs.getTimestamp("end_at").toInstant(),rs.getString("reservation_ref"),
   rs.getString("status"));
 }
 public record ReserveVehicleRequest(UUID vehicleId,Instant startAt,Instant endAt,String reservationRef){}
 /**
  * M25 scheduling reservation: PostgreSQL GiST exclusion prevents overlapping
  * reservations for the same hospital and vehicle under concurrent requests.
  * This is not GPS/clinical dispatch and never auto-dispatches a patient.
  */
 public VehicleReservation reserveVehicle(OperationWorkflow.Actor actor,ReserveVehicleRequest req){
  actor.require("m25:assign");
  if(req==null||req.vehicleId()==null||req.startAt()==null||req.endAt()==null)
   throw new WorkflowViolation("invalid_reservation");
  code(req.reservationRef());
  if(!req.startAt().isBefore(req.endAt()) ||
    Duration.between(req.startAt(),req.endAt()).compareTo(Duration.ofHours(24))>0 ||
    req.startAt().isBefore(Instant.now().minusSeconds(60)) ||
    req.startAt().isAfter(Instant.now().plus(Duration.ofDays(90))))
   throw new WorkflowViolation("invalid_reservation_window");
  return store.scoped(actor,()->{
   var previous=jdbc.query("SELECT * FROM synqoria_operations.vehicle_reservations "+
    "WHERE cell_id=? AND facility_id=? AND reservation_ref=? FOR UPDATE",
    this::reservationRow,actor.cell(),actor.facility(),req.reservationRef());
   if(!previous.isEmpty()){
    var p=previous.getFirst();
    if(!p.vehicleId().equals(req.vehicleId())||!p.startAt().equals(req.startAt())||
      !p.endAt().equals(req.endAt()))throw new WorkflowViolation("idempotency_collision");
    return p;
   }
   var vehicle=jdbc.queryForList("SELECT status FROM synqoria_operations.items "+
    "WHERE cell_id=? AND facility_id=? AND id=? AND module_id='M25' AND "+
    "record_type='VEHICLE' FOR UPDATE",actor.cell(),actor.facility(),req.vehicleId());
   if(vehicle.size()!=1)throw new WorkflowViolation("not_found");
   if(!"VEHICLE_AVAILABLE".equals(vehicle.getFirst().get("status")))
    throw new WorkflowViolation("vehicle_not_available");
   Long assigned=jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.transport_assignments "+
    "WHERE cell_id=? AND facility_id=? AND vehicle_ref=? AND active",Long.class,
    actor.cell(),actor.facility(),req.vehicleId().toString());
   if(assigned==null||assigned>0)throw new WorkflowViolation("vehicle_assigned");
   UUID id=UUID.randomUUID();
   try{
    jdbc.update("INSERT INTO synqoria_operations.vehicle_reservations "+
     "(reservation_id,cell_id,facility_id,vehicle_id,requested_by,start_at,end_at,reservation_ref) "+
     "VALUES(?,?,?,?,?,?,?,?)",
     id,actor.cell(),actor.facility(),req.vehicleId(),actor.actor(),
     Timestamp.from(req.startAt()),Timestamp.from(req.endAt()),req.reservationRef());
   }catch(DataIntegrityViolationException e){throw new WorkflowViolation("vehicle_reservation_conflict");}
   return new VehicleReservation(id,req.vehicleId(),req.startAt(),req.endAt(),
    req.reservationRef(),"RESERVED");
  });
 }
 public VehicleReservation cancelReservation(OperationWorkflow.Actor actor,UUID reservationId){
  actor.require("m25:cancel");
  if(reservationId==null)throw new WorkflowViolation("invalid_reservation");
  return store.scoped(actor,()->{
   var current=jdbc.query("SELECT * FROM synqoria_operations.vehicle_reservations "+
    "WHERE reservation_id=? AND cell_id=? AND facility_id=? FOR UPDATE",this::reservationRow,
    reservationId,actor.cell(),actor.facility());
   if(current.size()!=1)throw new WorkflowViolation("not_found");
   if(!"RESERVED".equals(current.getFirst().status()))
    throw new WorkflowViolation("reservation_already_cancelled");
   jdbc.update("UPDATE synqoria_operations.vehicle_reservations SET status='CANCELLED',cancelled_by=? "+
    "WHERE reservation_id=? AND cell_id=? AND facility_id=? AND status='RESERVED'",
    actor.actor(),reservationId,actor.cell(),actor.facility());
   var r=current.getFirst();
   return new VehicleReservation(r.reservationId(),r.vehicleId(),r.startAt(),r.endAt(),
    r.reservationRef(),"CANCELLED");
  });
 }

 public record PublicationCursor(Instant at,UUID id){}
 public record PublicationPage(List<Map<String,Object>> publications,PublicationCursor nextCursor){}
 /**
  * M29 composite cursor avoids missing same-timestamp publications.
  * Consumers must retain per-cell/facility checkpoint atomically with their own
  * application; this is immutable publication transport only, not a publisher.
  */
 public PublicationPage publicationPage(OperationWorkflow.Actor actor,String afterAt,String afterId,int max){
  actor.require("m29:read");limit(max);
  Instant since;UUID id;
  try{
   if((afterAt==null)!=(afterId==null))throw new WorkflowViolation("invalid_cursor");
   since=afterAt==null?Instant.EPOCH:Instant.parse(afterAt);
   id=afterId==null?new UUID(0,0):UUID.fromString(afterId);
  }catch(DateTimeParseException|IllegalArgumentException e){
   throw new WorkflowViolation("invalid_cursor");
  }
  final Instant cutoff=since;final UUID prior=id;
  return store.scoped(actor,()->{
   var rows=jdbc.queryForList("SELECT publication_id,item_id,config_key,schema_version,"+
    "item_version,content_digest,published_at,superseded_at "+
    "FROM synqoria_operations.facility_publications WHERE cell_id=? AND facility_id=? "+
    "AND (published_at>? OR (published_at=? AND publication_id>?)) "+
    "ORDER BY published_at,publication_id LIMIT ?",
    actor.cell(),actor.facility(),Timestamp.from(cutoff),Timestamp.from(cutoff),prior,max);
   if(rows.isEmpty())return new PublicationPage(List.of(),new PublicationCursor(cutoff,prior));
   var last=rows.getLast();
   Instant ts=((Timestamp)last.get("published_at")).toInstant();
   return new PublicationPage(List.copyOf(rows),
    new PublicationCursor(ts,(UUID)last.get("publication_id")));
  });
 }

 public record NotificationLease(UUID itemId,UUID leaseId,String channel,String recipientRef,
  String templateRef,int attempt,Instant leaseUntil){}
 /**
  * M37 bounded worker leasing. This only claims delivery jobs; it does NOT
  * send email/SMS/push, record provider success or forge evidence.
  */
 public List<NotificationLease> leaseNotifications(OperationWorkflow.Actor actor,int max){
  actor.require("m37:deliver");
  if(max<1||max>25)throw new WorkflowViolation("invalid_limit");
  return store.scoped(actor,()->{
   UUID lease=UUID.randomUUID();
   return jdbc.query("WITH picked AS ("+
    "SELECT n.item_id FROM synqoria_operations.notification_jobs n "+
    "JOIN synqoria_operations.items i ON i.cell_id=n.cell_id "+
    "AND i.facility_id=n.facility_id AND i.id=n.item_id "+
    "WHERE n.cell_id=? AND n.facility_id=? AND i.module_id='M37' "+
    "AND i.record_type='MESSAGE' AND n.state IN ('QUEUED','FAILED','RETRYING') "+
    "AND n.delivery_attempts<20 AND (n.next_attempt_at IS NULL OR n.next_attempt_at<=clock_timestamp()) "+
    "AND (n.lease_until IS NULL OR n.lease_until<clock_timestamp()) "+
    "ORDER BY n.created_at,n.item_id LIMIT ? FOR UPDATE OF n SKIP LOCKED) "+
    "UPDATE synqoria_operations.notification_jobs n SET lease_id=?, "+
    "lease_until=clock_timestamp()+interval '90 seconds', "+
    "next_attempt_at=clock_timestamp()+interval '5 minutes', "+
    "delivery_attempts=n.delivery_attempts+1,updated_at=clock_timestamp() "+
    "FROM picked WHERE n.cell_id=? AND n.facility_id=? AND n.item_id=picked.item_id "+
    "RETURNING n.item_id,n.lease_id,n.channel,n.recipient_ref,n.template_ref,"+
    "n.delivery_attempts,n.lease_until",
    (rs,i)->new NotificationLease(rs.getObject("item_id",UUID.class),
     rs.getObject("lease_id",UUID.class),rs.getString("channel"),
     rs.getString("recipient_ref"),rs.getString("template_ref"),
     rs.getInt("delivery_attempts"),rs.getTimestamp("lease_until").toInstant()),
    actor.cell(),actor.facility(),max,lease,actor.cell(),actor.facility());
  });
 }
}
