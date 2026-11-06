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
                            OperationWorkflow.Item current,ModuleCatalog.Step step){
  if(step.evidence().isEmpty())return OperationWorkflow.TrustedEvidence.none();
  Long count=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.external_evidence " +
   "WHERE cell_id=? AND facility_id=? AND item_id=? AND for_action=? AND expected_version=? " +
   "AND evidence_kind=? AND approver_id <> ? AND revoked_at IS NULL " +
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,actor.cell(),actor.facility(),current.id(),step.action(),current.version(),
   step.evidence(),current.createdBy());
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
   OperationWorkflow.TrustedEvidence admitted=authoritativeEvidence(actor,before,required);
   var change=OperationWorkflow.advance(before,req.action(),req.expectedVersion(),actor,
    admitted,req.fields());
   var next=change.item();
   if(jdbc.update("UPDATE synqoria_operations.items SET status=?,version=?,last_actor=?," +
    "fields=?::jsonb,updated_at=clock_timestamp() WHERE id=? AND module_id=? AND version=?",
    next.status(),next.version(),next.lastActor(),encode(next.fields()),id,module,
    before.version())!=1)throw new WorkflowViolation("stale_version");
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
}
