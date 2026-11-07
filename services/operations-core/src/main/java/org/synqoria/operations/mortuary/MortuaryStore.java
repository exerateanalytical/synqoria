package org.synqoria.operations.mortuary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;
import tools.jackson.databind.json.JsonMapper;
import static org.synqoria.operations.mortuary.MortuaryModels.*;

/**
 * M27 transactional hospital-cell mortuary domain.
 * Identity, custody and release evidence is written by a distinct institutional
 * authority role, never by this API runtime. A single transaction owns aggregate,
 * immutable custody, two-person approval, idempotency, audit and outbox.
 */
@Service
public final class MortuaryStore {
 private static final String ROLE="synqoria_operations_runtime";
 private final JdbcTemplate jdbc;
 private final TransactionTemplate tx;
 private final JsonMapper json;
 public MortuaryStore(JdbcTemplate jdbc,PlatformTransactionManager manager,
  JsonMapper json,@Value("${synqoria.ops.db-runtime-role}")String role){
  if(!ROLE.equals(role))throw new IllegalStateException("mortuary_non_owner_runtime_required");
  this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;
 }
 private <T> T scoped(OperationWorkflow.Actor actor,Supplier<T> operation){
  return Objects.requireNonNull(tx.execute(status->{
   String user=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean restricted=jdbc.queryForObject(
    "SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit " +
    "AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication " +
    "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!ROLE.equals(user)||!Boolean.TRUE.equals(restricted))
    throw new WorkflowViolation("database_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,
    actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,
    actor.facility().toString());
   return operation.get();
  }));
 }
 private static String code(String value){
  if(value==null||!value.matches("[A-Za-z0-9_.:/-]{1,160}"))
   throw new WorkflowViolation("invalid_mortuary_reference");
  return value;
 }
 private static UUID nonNull(UUID value){
  if(value==null)throw new WorkflowViolation("missing_required_identifier");
  return value;
 }
 private static String digest(String value){
  try{
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
     value.getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable");}
 }
 private static String hashParts(String...values){
  StringBuilder b=new StringBuilder();
  for(String s:values){String v=s==null?"":s;b.append(v.length()).append(':').append(v);}
  return digest(b.toString());
 }
 private MortuaryWorkflow.Snapshot snapshot(ResultSet rs)throws SQLException{
  return new MortuaryWorkflow.Snapshot(rs.getObject("case_id",UUID.class),
   rs.getObject("cell_id",UUID.class),rs.getObject("facility_id",UUID.class),
   rs.getObject("decedent_ref",UUID.class),MortuaryWorkflow.State.valueOf(rs.getString("state")),
   rs.getLong("version"),rs.getString("current_storage"),
   rs.getObject("intake_actor",UUID.class),rs.getObject("last_actor",UUID.class));
 }
 private CaseView view(ResultSet rs)throws SQLException{
  var end=rs.getTimestamp("released_at");
  return new CaseView(rs.getObject("case_id",UUID.class),
   rs.getObject("decedent_ref",UUID.class),
   rs.getString("originating_unit_ref"),rs.getString("belongings_seal_ref"),
   rs.getObject("intake_receiving_staff",UUID.class),
   rs.getString("state"),rs.getLong("version"),
   rs.getString("current_storage"),rs.getTimestamp("received_at").toInstant(),
   end==null?null:end.toInstant());
 }
 private List<MortuaryWorkflow.Snapshot> snapshots(OperationWorkflow.Actor actor,UUID id,boolean lock){
  return jdbc.query("SELECT * FROM synqoria_operations.mortuary_cases " +
   "WHERE cell_id=? AND facility_id=? AND case_id=?"+(lock?" FOR UPDATE":""),
   (rs,i)->snapshot(rs),actor.cell(),actor.facility(),id);
 }
 private MortuaryWorkflow.Snapshot load(OperationWorkflow.Actor actor,UUID id,boolean lock){
  var found=snapshots(actor,id,lock);
  if(found.size()!=1)throw new WorkflowViolation("not_found");
  return found.getFirst();
 }
 private CaseView caseView(OperationWorkflow.Actor actor,UUID id){
  var found=jdbc.query("SELECT * FROM synqoria_operations.mortuary_cases "+
   "WHERE cell_id=? AND facility_id=? AND case_id=?",(rs,i)->view(rs),
   actor.cell(),actor.facility(),id);
  if(found.size()!=1)throw new WorkflowViolation("not_found");
  return found.getFirst();
 }
 private UUID start(OperationWorkflow.Actor actor,UUID command,String hash){
  nonNull(command);
  jdbc.update("INSERT INTO synqoria_operations.mortuary_commands " +
   "(cell_id,facility_id,actor_id,command_id,request_sha256) VALUES(?,?,?,?,?) " +
   "ON CONFLICT(cell_id,facility_id,actor_id,command_id) DO NOTHING",
   actor.cell(),actor.facility(),actor.actor(),command,hash);
  var row=jdbc.queryForMap("SELECT request_sha256,case_id,result_version " +
   "FROM synqoria_operations.mortuary_commands " +
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
   actor.cell(),actor.facility(),actor.actor(),command);
  if(!hash.equals(row.get("request_sha256")))throw new WorkflowViolation("idempotency_collision");
  if(row.get("case_id")!=null)return (UUID)row.get("case_id");
  return null;
 }
 private void finish(OperationWorkflow.Actor actor,UUID cmd,UUID id,long version){
  if(jdbc.update("UPDATE synqoria_operations.mortuary_commands SET case_id=?,result_version=? " +
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? AND result_version IS NULL",
   id,version,actor.cell(),actor.facility(),actor.actor(),cmd)!=1)
   throw new WorkflowViolation("idempotency_collision");
 }
 private CaseView replay(OperationWorkflow.Actor actor,UUID id,long expected){
  var row=caseView(actor,id);
  if(row.version()!=expected)throw new WorkflowViolation("idempotency_result_superseded");
  return row;
 }
 private void proof(OperationWorkflow.Actor actor,UUID decedent,UUID id,String action,
                    long version,String kind,String external){
  String ref=code(external);
  // Lock the verified proof until the custody/release transaction commits.
  // A simultaneous independent revocation cannot invalidate an already-read proof mid-command.
  List<UUID> found=jdbc.query(
   "SELECT proof_id FROM synqoria_operations.mortuary_authority_evidence " +
   "WHERE cell_id=? AND facility_id=? AND decedent_ref=? " +
   "AND case_id IS NOT DISTINCT FROM ?::uuid AND for_action=? AND expected_version=? " +
   "AND proof_kind=? AND external_ref=? AND approved_by<>? AND revoked_at IS NULL " +
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   (rs,i)->rs.getObject("proof_id",UUID.class),
   actor.cell(),actor.facility(),decedent,id,action,version,kind,ref,actor.actor());
  if(found.isEmpty())throw new WorkflowViolation("independent_mortuary_evidence_required");
 }
 /**
  * Revocation-safe legal death/identity revalidation at EVERY release transition.
  * A physician/civil registrar may withdraw identity/death proof after intake;
  * a valid first-time intake must not silently authorize a later release.
  * The advisory fence serializes independent intake-proof revocations, while the
  * locked case row serializes case-specific proof revisions.
  */
 private void freshIdentityForRelease(OperationWorkflow.Actor actor,
                                      MortuaryWorkflow.Snapshot item){
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,27))",
   item.decedentRef().toString());
  Map<String,Object> refs=jdbc.queryForMap(
   "SELECT death_ref,intake_ref FROM synqoria_operations.mortuary_cases " +
   "WHERE cell_id=? AND facility_id=? AND case_id=?",
   actor.cell(),actor.facility(),item.caseId());
  proof(actor,item.decedentRef(),null,"RECEIVE",0,"DEATH_CONFIRMED",
   (String)refs.get("death_ref"));
  proof(actor,item.decedentRef(),null,"RECEIVE",0,"IDENTITY_ATTESTED",
   (String)refs.get("intake_ref"));
  Long current=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.mortuary_authority_evidence " +
   "WHERE cell_id=? AND facility_id=? AND decedent_ref=? AND case_id=? " +
   "AND for_action='IDENTIFY' AND expected_version=1 " +
   "AND proof_kind='IDENTITY_VERIFIED' AND approved_by<>? " +
   "AND revoked_at IS NULL AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,actor.cell(),actor.facility(),item.decedentRef(),item.caseId(),actor.actor());
  if(current==null||current<1)
   throw new WorkflowViolation("mortuary_identity_proof_revoked");
 }
 private void noLegalHolds(OperationWorkflow.Actor actor,UUID id){
  Long active=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_operations.mortuary_legal_holds "+
   "WHERE cell_id=? AND facility_id=? AND case_id=? AND active",
   Long.class,actor.cell(),actor.facility(),id);
  if(active==null||active>0)throw new WorkflowViolation("mortuary_legal_hold_active");
 }
 private void receiverOnShift(OperationWorkflow.Actor actor,UUID id){
  nonNull(id);
  List<UUID> accepted=jdbc.query(
   "SELECT actor_id FROM synqoria_operations.workforce_grants "+
   "WHERE cell_id=? AND facility_id=? AND actor_id=? AND active "+
   "AND shift_from<=clock_timestamp() AND shift_until>clock_timestamp() "+
   "AND valid_until>clock_timestamp() AND 'm27:custody_receive'=ANY(permissions)",
   (rs,i)->rs.getObject("actor_id",UUID.class),actor.cell(),actor.facility(),id);
  if(accepted.size()!=1)throw new WorkflowViolation("custody_receiver_not_on_shift");
 }
 private void event(OperationWorkflow.Actor actor,UUID command,UUID id,long version,
                   String action,String previous,String next,String detailsHash){
  UUID evt=UUID.randomUUID();
  jdbc.update("INSERT INTO synqoria_operations.mortuary_audit_events "+
   "(event_id,cell_id,facility_id,case_id,case_version,action,from_state,to_state,"+
   "actor_id,command_id,evidence_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
   evt,actor.cell(),actor.facility(),id,version,action,previous,next,
   actor.actor(),command,digest(detailsHash));
  String payload=json.writeValueAsString(Map.of("caseId",id.toString(),"module","M27",
   "version",version,"state",next));
  jdbc.update("INSERT INTO synqoria_operations.mortuary_outbox "+
   "(event_id,cell_id,facility_id,case_id,case_version,event_type,payload) "+
   "VALUES(?,?,?,?,?,?,?::jsonb)",
   evt,actor.cell(),actor.facility(),id,version,action,payload);
 }
 private void custody(OperationWorkflow.Actor actor,UUID id,long version,String type,
                      String from,String to,UUID receiver,String ref,String seal,String condition,
                      UUID witness,String belongings){
  jdbc.update("INSERT INTO synqoria_operations.mortuary_custody_events "+
   "(event_id,cell_id,facility_id,case_id,case_version,event_type,from_storage,to_storage,"+
   "actor_id,receiving_staff_id,evidence_ref,seal_ref,condition_code,witness_staff_id,belongings_ref) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),actor.cell(),actor.facility(),id,version,type,from,to,
   actor.actor(),receiver,code(ref),seal,condition,witness,belongings);
 }
 public CaseView receive(OperationWorkflow.Actor actor,Intake req){
  actor.require("m27:intake");
  if(req==null)throw new WorkflowViolation("invalid_mortuary_intake");
  UUID deceased=nonNull(req.decedentRef()),cmd=nonNull(req.commandId());
  String intake=code(req.intakeRef()),death=code(req.deathRef());
  String origin=code(req.originatingUnitRef()),authorization=code(req.authorizationRef());
  String seal=code(req.belongingsSealRef());
  UUID receiver=nonNull(req.receivingStaffId());
  if(receiver.equals(actor.actor()))throw new WorkflowViolation("independent_intake_receiver_required");
  String hash=hashParts("RECEIVE",deceased.toString(),intake,death,origin,authorization,seal,
   receiver.toString());
  return scoped(actor,()->{
   UUID prior=start(actor,cmd,hash);
   if(prior!=null)return replay(actor,prior,1);
   // Synchronize intake against independent proof issuance/revocation.
   jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,27))",
    deceased.toString());
   // Never create a death record from an unverified/administrative claim.
   proof(actor,deceased,null,"RECEIVE",0,"DEATH_CONFIRMED",death);
   proof(actor,deceased,null,"RECEIVE",0,"IDENTITY_ATTESTED",intake);
   proof(actor,deceased,null,"RECEIVE",0,"INTAKE_AUTHORIZED",authorization);
   proof(actor,deceased,null,"RECEIVE",0,"BELONGINGS_SEALED",seal);
   receiverOnShift(actor,receiver);
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_operations.mortuary_cases "+
    "(case_id,cell_id,facility_id,decedent_ref,intake_ref,death_ref,originating_unit_ref,"+
    "intake_authorization_ref,belongings_seal_ref,intake_receiving_staff,state,version,intake_actor,last_actor) "+
    "VALUES(?,?,?,?,?,?,?,?,?,?,'RECEIVED',1,?,?)",
    id,actor.cell(),actor.facility(),deceased,intake,death,origin,authorization,seal,receiver,
    actor.actor(),actor.actor());
   custody(actor,id,1,"INTAKE",null,null,receiver,intake,seal,"SEALED",null,seal);
   event(actor,cmd,id,1,"RECEIVE",null,"RECEIVED",hash);
   finish(actor,cmd,id,1);
   return caseView(actor,id);
  });
 }
 public CaseView change(OperationWorkflow.Actor actor,UUID caseId,Change req){
  nonNull(caseId);
  if(req==null||req.expectedVersion()<1)throw new WorkflowViolation("invalid_mortuary_action");
  nonNull(req.commandId());
  final MortuaryWorkflow.Action action;
  try{action=MortuaryWorkflow.Action.valueOf(req.action());}
  catch(RuntimeException e){throw new WorkflowViolation("invalid_mortuary_action");}
  String hash=hashParts("CHANGE",caseId.toString(),action.name(),String.valueOf(req.expectedVersion()),
   req.evidenceRef(),req.storageCode(),
   req.receivingStaffId()==null?null:req.receivingStaffId().toString(),
   req.recipientRef(),req.authorityRef(),req.sealRef(),req.conditionCode(),
   req.witnessStaffId()==null?null:req.witnessStaffId().toString(),
   req.belongingsReleaseRef());
  return scoped(actor,()->{
   UUID prior=start(actor,req.commandId(),hash);
   if(prior!=null){
    if(!prior.equals(caseId))throw new WorkflowViolation("idempotency_collision");
    return replay(actor,prior,req.expectedVersion()+1);
   }
   var before=load(actor,caseId,true);
   var decision=MortuaryWorkflow.decide(before,action,req.expectedVersion(),actor);
   if(decision.releaseSafetyCritical()){
    noLegalHolds(actor,caseId);
    freshIdentityForRelease(actor,before);
   }
   String from=before.state().name();
   String destination=before.currentStorage();
   UUID receiver=req.receivingStaffId();
   switch(action){
    case IDENTIFY -> proof(actor,before.decedentRef(),caseId,"IDENTIFY",before.version(),
      "IDENTITY_VERIFIED",req.evidenceRef());
    case STORE -> {
     destination=code(req.storageCode());
     code(req.sealRef());code(req.conditionCode());
     if(receiver!=null&&receiver.equals(actor.actor()))
      throw new WorkflowViolation("custody_receiver_must_be_independent");
     receiverOnShift(actor,receiver);
     proof(actor,before.decedentRef(),caseId,"STORE",before.version(),
      "STORAGE_SAFE",req.evidenceRef());
    }
    case TRANSFER -> {
     destination=code(req.storageCode());
     code(req.sealRef());code(req.conditionCode());
     MortuaryWorkflow.validateTransfer(before.currentStorage(),destination,actor.actor(),receiver);
     receiverOnShift(actor,receiver);
     proof(actor,before.decedentRef(),caseId,"TRANSFER",before.version(),
      "CUSTODY_ACCEPTED",req.evidenceRef());
    }
    case REVIEW_RELEASE -> {
     if(actor.actor().equals(before.intakeActor()))
      throw new WorkflowViolation("maker_checker_denied");
     proof(actor,before.decedentRef(),caseId,"REVIEW_RELEASE",before.version(),
      "LEGAL_RELEASE",req.authorityRef());
     proof(actor,before.decedentRef(),caseId,"REVIEW_RELEASE",before.version(),
      "RECIPIENT_VERIFIED",req.recipientRef());
     jdbc.update("INSERT INTO synqoria_operations.mortuary_release_reviews "+
      "(approval_id,cell_id,facility_id,case_id,stage,actor_id,authority_ref,recipient_ref,case_version) "+
      "VALUES(?,?,?,?, 'REVIEW',?,?,?,?)",
      UUID.randomUUID(),actor.cell(),actor.facility(),caseId,actor.actor(),
      code(req.authorityRef()),code(req.recipientRef()),before.version()+1);
    }
    case AUTHORIZE_RELEASE -> {
     var reviews=jdbc.queryForList("SELECT actor_id,authority_ref,recipient_ref,case_version "+
      "FROM synqoria_operations.mortuary_release_reviews "+
      "WHERE cell_id=? AND facility_id=? AND case_id=? AND stage='REVIEW'",
      actor.cell(),actor.facility(),caseId);
     if(reviews.size()!=1)throw new WorkflowViolation("release_review_missing");
     var review=reviews.getFirst();
     MortuaryWorkflow.requireIndependentApprovers((UUID)review.get("actor_id"),
      actor.actor(),before.intakeActor());
     String authority=code(req.authorityRef()),recipient=code(req.recipientRef());
     if(!authority.equals(review.get("authority_ref"))||
        !recipient.equals(review.get("recipient_ref")))
      throw new WorkflowViolation("release_scope_mismatch");
     proof(actor,before.decedentRef(),caseId,"REVIEW_RELEASE",
      ((Number)review.get("case_version")).longValue()-1,"LEGAL_RELEASE",authority);
     proof(actor,before.decedentRef(),caseId,"AUTHORIZE_RELEASE",before.version(),
      "RELEASE_AUTHORIZATION",authority);
     proof(actor,before.decedentRef(),caseId,"AUTHORIZE_RELEASE",before.version(),
      "RECIPIENT_VERIFIED",recipient);
     jdbc.update("INSERT INTO synqoria_operations.mortuary_release_reviews "+
      "(approval_id,cell_id,facility_id,case_id,stage,actor_id,authority_ref,recipient_ref,case_version) "+
      "VALUES(?,?,?,?, 'AUTHORIZE',?,?,?,?)",
      UUID.randomUUID(),actor.cell(),actor.facility(),caseId,actor.actor(),authority,recipient,
      before.version()+1);
    }
    case HANDOVER -> {
     var approvals=jdbc.queryForList(
      "SELECT actor_id,stage,authority_ref,recipient_ref,case_version "+
      "FROM synqoria_operations.mortuary_release_reviews "+
      "WHERE cell_id=? AND facility_id=? AND case_id=? ORDER BY stage",
      actor.cell(),actor.facility(),caseId);
     if(approvals.size()!=2)throw new WorkflowViolation("dual_release_approval_required");
     var auth=approvals.stream().filter(x->"AUTHORIZE".equals(x.get("stage"))).findFirst()
      .orElseThrow(()->new WorkflowViolation("dual_release_approval_required"));
     var review=approvals.stream().filter(x->"REVIEW".equals(x.get("stage"))).findFirst()
      .orElseThrow(()->new WorkflowViolation("dual_release_approval_required"));
     MortuaryWorkflow.requireIndependentApprovers((UUID)review.get("actor_id"),
      (UUID)auth.get("actor_id"),before.intakeActor());
     if(actor.actor().equals(review.get("actor_id"))||
        actor.actor().equals(auth.get("actor_id")))
      throw new WorkflowViolation("release_handover_separation_required");
     if(!code(req.authorityRef()).equals(auth.get("authority_ref"))||
        !code(req.recipientRef()).equals(auth.get("recipient_ref")))
      throw new WorkflowViolation("release_scope_mismatch");
     proof(actor,before.decedentRef(),caseId,"REVIEW_RELEASE",
      ((Number)review.get("case_version")).longValue()-1,"LEGAL_RELEASE",
      req.authorityRef());
     proof(actor,before.decedentRef(),caseId,"AUTHORIZE_RELEASE",
      ((Number)auth.get("case_version")).longValue()-1,"RELEASE_AUTHORIZATION",
      req.authorityRef());
     proof(actor,before.decedentRef(),caseId,"HANDOVER",before.version(),
      "RECIPIENT_VERIFIED",req.recipientRef());
     proof(actor,before.decedentRef(),caseId,"HANDOVER",before.version(),
      "HANDOVER_RECEIPT",req.evidenceRef());
     UUID witness=nonNull(req.witnessStaffId());
     if(witness.equals(actor.actor())||witness.equals(before.intakeActor())||
        witness.equals(review.get("actor_id"))||witness.equals(auth.get("actor_id")))
      throw new WorkflowViolation("independent_handover_witness_required");
     receiverOnShift(actor,witness);
     proof(actor,before.decedentRef(),caseId,"HANDOVER",before.version(),
      "BELONGINGS_RELEASED",req.belongingsReleaseRef());
     destination=null;
    }
   }
   long version=before.version()+1;
   int changed=jdbc.update(
    "UPDATE synqoria_operations.mortuary_cases SET state=?,version=?,current_storage=?,"+
    "last_actor=?,updated_at=clock_timestamp(),released_at="+
    (action==MortuaryWorkflow.Action.HANDOVER?"clock_timestamp()":"released_at")+
    " WHERE case_id=? AND cell_id=? AND facility_id=? AND version=?",
    decision.next().name(),version,destination,actor.actor(),caseId,
    actor.cell(),actor.facility(),before.version());
   if(changed!=1)throw new WorkflowViolation("stale_version");
   if(action==MortuaryWorkflow.Action.STORE||action==MortuaryWorkflow.Action.TRANSFER)
    custody(actor,caseId,version,action.name(),before.currentStorage(),destination,receiver,
      req.evidenceRef(),code(req.sealRef()),code(req.conditionCode()),null,null);
   if(action==MortuaryWorkflow.Action.HANDOVER)
    custody(actor,caseId,version,"HANDOVER",before.currentStorage(),null,null,req.evidenceRef(),
      null,null,nonNull(req.witnessStaffId()),code(req.belongingsReleaseRef()));
   event(actor,req.commandId(),caseId,version,action.name(),from,decision.next().name(),hash);
   finish(actor,req.commandId(),caseId,version);
   return caseView(actor,caseId);
  });
 }
 public List<CaseView> list(OperationWorkflow.Actor actor,int limit){
  actor.require("m27:read");
  if(limit<1||limit>100)throw new WorkflowViolation("invalid_limit");
  return scoped(actor,()->jdbc.query("SELECT * FROM synqoria_operations.mortuary_cases "+
   "WHERE cell_id=? AND facility_id=? ORDER BY updated_at DESC,case_id LIMIT ?",
   (rs,i)->view(rs),actor.cell(),actor.facility(),limit));
 }
 public CaseView one(OperationWorkflow.Actor actor,UUID caseId){
  actor.require("m27:read");
  return scoped(actor,()->caseView(actor,nonNull(caseId)));
 }
 public List<CustodyView> custodyHistory(OperationWorkflow.Actor actor,UUID id){
  actor.require("m27:read");
  return scoped(actor,()->{
   load(actor,nonNull(id),false);
   return jdbc.query("SELECT * FROM synqoria_operations.mortuary_custody_events "+
    "WHERE cell_id=? AND facility_id=? AND case_id=? ORDER BY case_version",
    (rs,i)->new CustodyView(rs.getLong("case_version"),rs.getString("event_type"),
      rs.getString("from_storage"),rs.getString("to_storage"),
      rs.getObject("actor_id",UUID.class),rs.getObject("receiving_staff_id",UUID.class),
      rs.getString("evidence_ref"),rs.getTimestamp("occurred_at").toInstant(),
      rs.getString("seal_ref"),rs.getString("condition_code"),
      rs.getObject("witness_staff_id",UUID.class),rs.getString("belongings_ref")),
    actor.cell(),actor.facility(),id);
  });
 }

 /** Only independently approved release reviewers can inspect administrative/court holds. */
 public List<HoldView> legalHolds(OperationWorkflow.Actor actor,UUID id){
  actor.require("m27:review");
  return scoped(actor,()->{
   load(actor,nonNull(id),false);
   return jdbc.query("SELECT hold_id,authority_ref,reason_code,active,ordered_at,rescinded_at "+
    "FROM synqoria_operations.mortuary_legal_holds "+
    "WHERE cell_id=? AND facility_id=? AND case_id=? ORDER BY ordered_at DESC LIMIT 100",
    (rs,i)->{
     var rescinded=rs.getTimestamp("rescinded_at");
     return new HoldView(rs.getObject("hold_id",UUID.class),
      rs.getString("authority_ref"),rs.getString("reason_code"),rs.getBoolean("active"),
      rs.getTimestamp("ordered_at").toInstant(),
      rescinded==null?null:rescinded.toInstant());
    },actor.cell(),actor.facility(),id);
  });
 }
 public List<ApprovalView> approvals(OperationWorkflow.Actor actor,UUID id){
  actor.require("m27:review");
  return scoped(actor,()->{
   load(actor,nonNull(id),false);
   return jdbc.query("SELECT * FROM synqoria_operations.mortuary_release_reviews "+
    "WHERE cell_id=? AND facility_id=? AND case_id=? ORDER BY created_at",
    (rs,i)->new ApprovalView(rs.getString("stage"),rs.getObject("actor_id",UUID.class),
     rs.getLong("case_version"),rs.getTimestamp("created_at").toInstant()),
    actor.cell(),actor.facility(),id);
  });
 }
 public List<AuditView> audit(OperationWorkflow.Actor actor,UUID id){
  actor.require("m27:audit");
  return scoped(actor,()->{
   load(actor,nonNull(id),false);
   return jdbc.query("SELECT * FROM synqoria_operations.mortuary_audit_events "+
    "WHERE cell_id=? AND facility_id=? AND case_id=? ORDER BY case_version",
    (rs,i)->new AuditView(rs.getLong("case_version"),rs.getString("action"),
     rs.getString("from_state"),rs.getString("to_state"),
     rs.getObject("actor_id",UUID.class),rs.getTimestamp("occurred_at").toInstant()),
    actor.cell(),actor.facility(),id);
  });
 }
}
