package org.synqoria.operations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.synqoria.operations.domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict PostgreSQL cell/facility boundary. Prepared arguments only, a single
 * physical JDBC transaction for scope, state, idempotency, audit and outbox.
 * Runtime DB role must be a separate non-owner, non-BYPASSRLS principal.
 * Live API remains DENIED by WorkforceAdmission until trusted grant evidence.
 */
@Service
public final class OperationsStore {
 private final JdbcTemplate jdbc;
 private final JsonMapper json;
 private final TransactionTemplate tx;
 private final String expectedRole;
 public OperationsStore(JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager manager,
  @Value("${synqoria.ops.db-runtime-role}") String expectedRole) {
  this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);
  if(!"synqoria_operations_runtime".equals(expectedRole))
   throw new IllegalStateException("operations_db_role_must_be_least_privileged");
  this.expectedRole=expectedRole;
 }
 private <T> T scoped(OperationWorkflow.Actor actor,Supplier<T> operation) {
  return Objects.requireNonNull(tx.execute(t ->{
   Boolean safe=jdbc.queryForObject(
    "SELECT rolcanlogin AND NOT rolinherit AND NOT rolsuper AND NOT rolbypassrls " +
    "AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication " +
    "FROM pg_roles WHERE rolname = current_user",Boolean.class);
   String principal=jdbc.queryForObject("SELECT current_user",String.class);
   if(!expectedRole.equals(principal)||!Boolean.TRUE.equals(safe))
    throw new WorkflowViolation("database_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   return operation.get();
  }));
 }
 private String encode(Object obj){return json.writeValueAsString(obj);}
 private String sha(String text){
  try{
   byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
   return HexFormat.of().formatHex(digest);
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable");}
 }
 private String fingerprint(String kind,String module,UUID item,long ver,String action,Map<String,Object>fields){
  if(fields==null)throw new WorkflowViolation("invalid_fields");
  return sha(kind+"|"+module+"|"+(item==null?"create":item)+"|"+ver+"|"+action+
   "|"+encode(new TreeMap<>(fields)));
 }
 private Map<String,Object> decode(String fields){
  @SuppressWarnings("unchecked")
  Map<String,Object> decoded=json.readValue(fields,Map.class);
  return decoded;
 }
 private OperationWorkflow.Item fromRow(ResultSet rs)throws SQLException{
  return new OperationWorkflow.Item(
   rs.getObject("id",UUID.class),rs.getObject("cell_id",UUID.class),
   rs.getObject("facility_id",UUID.class),
   rs.getString("module_id"),rs.getString("record_type"),rs.getString("status"),
   rs.getLong("version"),rs.getObject("created_by",UUID.class),
   rs.getObject("last_actor",UUID.class),decode(rs.getString("fields")));
 }
 private List<OperationWorkflow.Item> queryItems(String sql,Object...args){
  return jdbc.query(sql,(rs,row)->fromRow(rs),args);
 }
 private OperationWorkflow.Item getLocked(String module,UUID id,boolean forUpdate){
  String sql="SELECT * FROM synqoria_operations.items WHERE module_id=? AND id=?"+
    (forUpdate?" FOR UPDATE":"");
  List<OperationWorkflow.Item> found=queryItems(sql,module,id);
  if(found.size()!=1)throw new WorkflowViolation("not_found");
  return found.getFirst();
 }
 /** Returns previously-completed aggregate ID only if replay hash matches. */
 private UUID beginCommand(OperationWorkflow.Actor actor,UUID commandId,String requestHash){
  if(commandId==null||requestHash==null)throw new WorkflowViolation("idempotency_key_required");
  jdbc.update("INSERT INTO synqoria_operations.command_idempotency " +
   "(cell_id,facility_id,actor_id,command_id,request_hash) VALUES (?,?,?,?,?) " +
   "ON CONFLICT (cell_id,facility_id,actor_id,command_id) DO NOTHING",
   actor.cell(),actor.facility(),actor.actor(),commandId,requestHash);
  Map<String,Object> row=jdbc.queryForMap(
   "SELECT request_hash,item_id,result_version FROM synqoria_operations.command_idempotency " +
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
   actor.cell(),actor.facility(),actor.actor(),commandId);
  if(!requestHash.equals(row.get("request_hash")))throw new WorkflowViolation("idempotency_collision");
  if(row.get("item_id")!=null){
   if(row.get("result_version")==null)throw new WorkflowViolation("idempotency_collision");
   return (UUID)row.get("item_id");
  }
  return null;
 }
 private void completeCommand(OperationWorkflow.Actor actor,UUID command,OperationWorkflow.Item item){
  if(jdbc.update("UPDATE synqoria_operations.command_idempotency SET item_id=?, result_version=? " +
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? AND result_version IS NULL",
   item.id(),item.version(),actor.cell(),actor.facility(),actor.actor(),command)!=1)
   throw new WorkflowViolation("idempotency_collision");
 }
 private void recordChange(OperationWorkflow.Actor actor,UUID command,OperationWorkflow.Item item,
                           String action,String before){
  UUID id=UUID.randomUUID();
  String fieldsDigest=sha(encode(new TreeMap<>(item.fields())));
  jdbc.update("INSERT INTO synqoria_operations.audit_events " +
   "(event_id,cell_id,facility_id,item_id,module_id,action,from_status,to_status," +
   "item_version,actor_id,command_id,field_digest) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
   id,actor.cell(),actor.facility(),item.id(),item.module(),action,before,item.status(),
   item.version(),actor.actor(),command,fieldsDigest);
  String payload=encode(Map.of("itemId",item.id().toString(),"module",item.module(),
   "version",item.version(),"state",item.status()));
  jdbc.update("INSERT INTO synqoria_operations.outbox " +
   "(event_id,cell_id,facility_id,item_id,module_id,event_type,event_version,payload) " +
   "VALUES (?,?,?,?,?,?,?,?::jsonb)",
   id,actor.cell(),actor.facility(),item.id(),item.module(),action,item.version(),payload);
 }
 public OperationWorkflow.Item create(OperationWorkflow.Actor actor,String module,OperationsApi.NewItem req){
  ModuleCatalog.require(module);
  OperationWorkflow.Item draft=OperationWorkflow.create(module,req.recordType(),
   UUID.randomUUID(),actor,req.fields());
  String fingerprint=fingerprint("CREATE",module,null,0,req.recordType(),req.fields());
  return scoped(actor,()->{
   UUID previous=beginCommand(actor,req.commandId(),fingerprint);
   if(previous!=null)return getVersionedReplay(module,previous,1);
   jdbc.update("INSERT INTO synqoria_operations.items " +
    "(id,cell_id,facility_id,module_id,record_type,status,version,created_by,last_actor,fields) " +
    "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)",
    draft.id(),draft.cell(),draft.facility(),draft.module(),draft.recordType(),
    draft.status(),draft.version(),draft.createdBy(),draft.lastActor(),encode(draft.fields()));
   OperationsDomainEffects.onCreate(jdbc,draft);
   recordChange(actor,req.commandId(),draft,"CREATE",null);
   completeCommand(actor,req.commandId(),draft);
   return draft;
  });
 }
 private OperationWorkflow.Item getVersionedReplay(String module,UUID itemId,long version){
  OperationWorkflow.Item item=getLocked(module,itemId,false);
  if(item.version()!=version)throw new WorkflowViolation("idempotency_result_superseded");
  return item;
 }
 /**
  * A patient or staff API request cannot create an evidence claim.
  * Only an independent evidence issuer/migration role may INSERT rows.
  * Proof is bound to aggregate version AND action to prevent reuse after changes.
  */
 private OperationWorkflow.TrustedEvidence authoritativeEvidence(OperationWorkflow.Actor actor,
                            OperationWorkflow.Item current,ModuleCatalog.Step step,
                            Map<String,Object> incoming){
  if(step.evidence().isEmpty())return OperationWorkflow.TrustedEvidence.none();
  String referenceField=switch(step.evidence()){
   case "verified_signature" -> "signatureRef";
   case "document_lineage" -> "previousRef";
   case "biomed_inspection" -> "inspectionRef";
   case "transport_handover" -> "handoverRef";
   case "delivery_receipt" -> "deliveryRef";
   case "purchase_receipt" -> "receiptRef";
   case "invoice_matched" -> "invoiceRef";
   case "supplier_qualified" -> "qualificationRef";
   case "vehicle_driver_qualified" -> current.recordType().equals("VEHICLE")?"inspectionRef":"vehicleRef";
   default -> null;
  };
  Object reference=referenceField==null?null:
   incoming.containsKey(referenceField)?incoming.get(referenceField):
   current.fields().get(referenceField);
  if(referenceField!=null&&(!(reference instanceof String value)||value.isBlank()))
   throw new WorkflowViolation("trusted_evidence_reference_required");
  Long count=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.external_evidence " +
   "WHERE cell_id=? AND facility_id=? AND item_id=? AND for_action=? AND expected_version=? " +
   "AND evidence_kind=? AND (?::text IS NULL OR external_ref=?) " +
   "AND approver_id <> ? AND revoked_at IS NULL " +
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,actor.cell(),actor.facility(),current.id(),step.action(),current.version(),
   step.evidence(),reference,reference,current.createdBy());
  if(count==null||count<1)throw new WorkflowViolation("trusted_evidence_required");
  return new OperationWorkflow.TrustedEvidence(Set.of(step.evidence()));
 }
 public OperationWorkflow.Item transition(OperationWorkflow.Actor actor,String module,
                                          UUID id,OperationsApi.TransitionItem req){
  ModuleCatalog.require(module);
  String hash=fingerprint("TRANSITION",module,id,req.expectedVersion(),req.action(),req.fields());
  return scoped(actor,()->{
   UUID previous=beginCommand(actor,req.commandId(),hash);
   if(previous!=null)return getVersionedReplay(module,previous,req.expectedVersion()+1);
   OperationWorkflow.Item before=getLocked(module,id,true);
   ModuleCatalog.Step required=ModuleCatalog.require(module).transition(before.status(),req.action());
   OperationWorkflow.TrustedEvidence admitted=authoritativeEvidence(actor,before,required,req.fields());
   var change=OperationWorkflow.advance(before,req.action(),req.expectedVersion(),actor,
    admitted,req.fields());
   var next=change.item();
   if(jdbc.update("UPDATE synqoria_operations.items SET status=?,version=?,last_actor=?," +
    "fields=?::jsonb,updated_at=clock_timestamp() WHERE id=? AND module_id=? AND version=?",
    next.status(),next.version(),next.lastActor(),encode(next.fields()),id,module,
    before.version())!=1)throw new WorkflowViolation("stale_version");
   OperationsDomainEffects.onTransition(jdbc,before,next,change.step().action());
   recordChange(actor,req.commandId(),next,change.step().action(),before.status());
   completeCommand(actor,req.commandId(),next);
   return next;
  });
 }
 public OperationWorkflow.Item one(OperationWorkflow.Actor actor,String module,UUID itemId){
  actor.require(module.toLowerCase(Locale.ROOT)+":read");
  ModuleCatalog.require(module);
  return scoped(actor,()->getLocked(module,itemId,false));
 }
 public List<OperationWorkflow.Item> list(OperationWorkflow.Actor actor,String module,int limit){
  actor.require(module.toLowerCase(Locale.ROOT)+":read");
  ModuleCatalog.require(module);
  if(limit<1||limit>100)throw new WorkflowViolation("invalid_limit");
  return scoped(actor,()->queryItems(
   "SELECT * FROM synqoria_operations.items WHERE cell_id=? AND facility_id=? AND module_id=? " +
   "ORDER BY updated_at DESC,id LIMIT ?",actor.cell(),actor.facility(),module,limit));
 }

 /** The stock balances table is authoritative. Item.fields.quantity is only a request hint. */
 public record StockMovementRequest(String movement,String lot,String bin,long quantity,
                String referenceCode,long expectedVersion,UUID commandId){}
 public record StockBalanceView(String lot,String bin,long onHand,long reserved,long available,long version){}
 private static String code(String value){
  if(value==null||!value.matches("[A-Za-z0-9_.:/-]{1,160}"))
   throw new WorkflowViolation("invalid_reference");
  return value;
 }
 private StockBalanceView loadBalance(UUID item,String lot,String bin,boolean lock){
  List<StockBalanceView> values=jdbc.query(
   "SELECT lot_code,bin_code,on_hand,reserved,version FROM synqoria_operations.stock_balances " +
   "WHERE item_id=? AND lot_code=? AND bin_code=?"+(lock?" FOR UPDATE":""),
   (rs,i)->new StockBalanceView(rs.getString("lot_code"),rs.getString("bin_code"),
     rs.getLong("on_hand"),rs.getLong("reserved"),rs.getLong("on_hand")-rs.getLong("reserved"),
     rs.getLong("version")),item,lot,bin);
  if(values.size()!=1)throw new WorkflowViolation("stock_balance_not_found");
  return values.getFirst();
 }
 public List<StockBalanceView> balances(OperationWorkflow.Actor actor,UUID stockItem){
  actor.require("m22:read");
  return scoped(actor,()->{
   var item=getLocked("M22",stockItem,false);
   if(!"STOCK_ITEM".equals(item.recordType()))throw new WorkflowViolation("record_type_denied");
   return jdbc.query(
    "SELECT lot_code,bin_code,on_hand,reserved,version FROM synqoria_operations.stock_balances " +
    "WHERE cell_id=? AND facility_id=? AND item_id=? ORDER BY lot_code,bin_code LIMIT 100",
    (rs,i)->new StockBalanceView(rs.getString("lot_code"),rs.getString("bin_code"),
     rs.getLong("on_hand"),rs.getLong("reserved"),rs.getLong("on_hand")-rs.getLong("reserved"),
     rs.getLong("version")),actor.cell(),actor.facility(),stockItem);
  });
 }
 private boolean receiptProof(OperationWorkflow.Actor actor,OperationWorkflow.Item item,
                         StockLedger.Movement movement,String ref){
  String kind=switch(movement){
   case RECEIVE -> "stock_receipt";
   case RETURN -> "stock_return";
   default -> "";
  };
  if(kind.isEmpty())return true;
  Long count=jdbc.queryForObject("SELECT count(*) FROM synqoria_operations.external_evidence " +
   "WHERE cell_id=? AND facility_id=? AND item_id=? AND for_action=? AND expected_version=? " +
   "AND evidence_kind=? AND external_ref=? AND approver_id<>? AND revoked_at IS NULL " +
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,actor.cell(),actor.facility(),item.id(),movement.name()+"_STOCK",
   item.version(),kind,ref,item.createdBy());
  return count!=null&&count>0;
 }
 public StockBalanceView moveStock(OperationWorkflow.Actor actor,UUID stockItem,StockMovementRequest req){
  Objects.requireNonNull(req);
  if(req.commandId()==null||req.expectedVersion()<1||req.quantity()<1||req.quantity()>1_000_000)
   throw new WorkflowViolation("invalid_stock_movement");
  String lot=code(req.lot()),bin=code(req.bin()),reference=code(req.referenceCode());
  final StockLedger.Movement movement;
  try{movement=StockLedger.Movement.valueOf(Objects.requireNonNull(req.movement()));}
  catch(IllegalArgumentException | NullPointerException e){throw new WorkflowViolation("movement_not_allowed");}
  if(movement==StockLedger.Movement.ADJUST)
   throw new WorkflowViolation("signed_adjustment_review_required");
  String permission=switch(movement){
   case RECEIVE -> "m22:receive"; case RESERVE -> "m22:reserve"; case ISSUE -> "m22:issue";
   case RELEASE -> "m22:release"; case RETURN -> "m22:return";
   default -> throw new WorkflowViolation("movement_not_allowed");
  };
  actor.require(permission);
  String fp=fingerprint("STOCK","M22",stockItem,req.expectedVersion(),movement.name(),
    Map.of("lot",lot,"bin",bin,"quantity",req.quantity(),"referenceCode",reference));
  return scoped(actor,()->{
   UUID replay=beginCommand(actor,req.commandId(),fp);
   if(replay!=null){
    getVersionedReplay("M22",replay,req.expectedVersion()+1);
    return loadBalance(stockItem,lot,bin,false);
   }
   OperationWorkflow.Item item=getLocked("M22",stockItem,true);
   if(!"STOCK_ITEM".equals(item.recordType()))throw new WorkflowViolation("record_type_denied");
   if(item.version()!=req.expectedVersion())throw new WorkflowViolation("stale_version");
   if(movement==StockLedger.Movement.RESERVE||movement==StockLedger.Movement.ISSUE){
    if(!Set.of("AVAILABLE","RESERVED").contains(item.status()))
     throw new WorkflowViolation("stock_not_released");
   }
   boolean trustedReceipt=receiptProof(actor,item,StockLedger.Movement.RECEIVE,reference);
   boolean trustedReturn=receiptProof(actor,item,StockLedger.Movement.RETURN,reference);
   jdbc.update("INSERT INTO synqoria_operations.stock_balances " +
    "(cell_id,facility_id,item_id,lot_code,bin_code,on_hand,reserved,version) " +
    "VALUES(?,?,?,?,?,0,0,1) ON CONFLICT (cell_id,facility_id,item_id,lot_code,bin_code) DO NOTHING",
    actor.cell(),actor.facility(),stockItem,lot,bin);
   var current=loadBalance(stockItem,lot,bin,true);
   var b=StockLedger.apply(new StockLedger.Balance(current.onHand(),current.reserved(),
    current.version()),movement,req.quantity(),trustedReceipt,trustedReturn,false);
   int affected=jdbc.update(
    "UPDATE synqoria_operations.stock_balances SET on_hand=?,reserved=?,version=? " +
    "WHERE cell_id=? AND facility_id=? AND item_id=? AND lot_code=? AND bin_code=? AND version=?",
    b.onHand(),b.reserved(),b.version(),actor.cell(),actor.facility(),stockItem,lot,bin,current.version());
   if(affected!=1)throw new WorkflowViolation("stale_stock_balance");
   jdbc.update("INSERT INTO synqoria_operations.stock_movements" +
    "(movement_id,cell_id,facility_id,item_id,lot_code,bin_code,movement,quantity,actor_id," +
    "command_id,reference_code) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
    UUID.randomUUID(),actor.cell(),actor.facility(),stockItem,lot,bin,movement.name(),req.quantity(),
    actor.actor(),req.commandId(),reference);
   if(jdbc.update("UPDATE synqoria_operations.items SET version=?,last_actor=?,"+
     "updated_at=clock_timestamp() WHERE id=? AND module_id='M22' AND version=?",
     item.version()+1,actor.actor(),stockItem,item.version())!=1)
     throw new WorkflowViolation("stale_version");
   var next=new OperationWorkflow.Item(item.id(),item.cell(),item.facility(),item.module(),
    item.recordType(),item.status(),item.version()+1,item.createdBy(),actor.actor(),item.fields());
   recordChange(actor,req.commandId(),next,"STOCK_"+movement.name(),item.status());
   completeCommand(actor,req.commandId(),next);
   return new StockBalanceView(lot,bin,b.onHand(),b.reserved(),b.available(),b.version());
  });
 }

 
 /**
  * Read-only domain projections. Source data is in authoritative cell-scoped tables
  * and is never queried outside the current RLS-verified transaction.
  * The clinical patient encounter and signed document bytes are deliberately excluded.
  */
 public Map<String,Object> details(OperationWorkflow.Actor actor,String module,UUID id){
  ModuleCatalog.require(module);
  actor.require(module.toLowerCase(Locale.ROOT)+":read");
  return scoped(actor,()->{
   var item=getLocked(module,id,false);
   List<Map<String,Object>> rows=switch(module){
    case "M21" -> jdbc.queryForList("SELECT supplier_ref,purchase_ref,amount_minor,currency,"+
     "state,receipt_ref,invoice_ref,ordered_at FROM synqoria_operations.procurement_orders "+
     "WHERE cell_id=? AND facility_id=? AND item_id=?",actor.cell(),actor.facility(),id);
    case "M22" -> jdbc.queryForList("SELECT lot_code,bin_code,on_hand,reserved,version "+
     "FROM synqoria_operations.stock_balances WHERE cell_id=? AND facility_id=? AND item_id=? "+
     "ORDER BY lot_code,bin_code LIMIT 100",actor.cell(),actor.facility(),id);
    case "M23" -> jdbc.queryForList("SELECT asset_ref,technician_ref,inspection_ref,"+
     "state,due_at,verified_at FROM synqoria_operations.maintenance_orders "+
     "WHERE cell_id=? AND facility_id=? AND item_id=?",actor.cell(),actor.facility(),id);
    case "M25" -> jdbc.queryForList("SELECT vehicle_ref,driver_ref,origin_ref,destination_ref,"+
     "state,assigned_at,ended_at FROM synqoria_operations.transport_assignments "+
     "WHERE cell_id=? AND facility_id=? AND item_id=?",actor.cell(),actor.facility(),id);
    case "M29" -> jdbc.queryForList("SELECT config_key,schema_version,item_version,"+
     "content_digest,published_by,published_at,superseded_at "+
     "FROM synqoria_operations.facility_publications WHERE cell_id=? AND facility_id=? AND item_id=? "+
     "ORDER BY item_version DESC LIMIT 50",actor.cell(),actor.facility(),id);
    case "M31" -> jdbc.queryForList("SELECT document_version,disposition,content_digest,"+
     "signature_ref,previous_ref,actor_id,occurred_at FROM synqoria_operations.document_versions "+
     "WHERE cell_id=? AND facility_id=? AND item_id=? "+
     "ORDER BY document_version DESC LIMIT 50",actor.cell(),actor.facility(),id);
    case "M37" -> jdbc.queryForList("SELECT recipient_ref,template_ref,channel,state,"+
     "delivery_ref,delivery_attempts,next_attempt_at,acknowledged_by,updated_at "+
     "FROM synqoria_operations.notification_jobs WHERE cell_id=? AND facility_id=? AND item_id=?",
     actor.cell(),actor.facility(),id);
    default -> throw new WorkflowViolation("unknown_module");
   };
   // Not all types own a separate materialized detail row (e.g. supplier/vehicle).
   return Map.of("module",module,"itemId",item.id().toString(),
    "aggregateVersion",item.version(),"rows",rows);
  });
 }

}
