package org.synqoria.referrals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.synqoria.referrals.ReferralApplication.*;

@Component
public class ReferralStore{
 public record Reply(int status,Object body){}
 public record Request(UUID patientRef,UUID recipientFacility,String reasonCode,
                       String summarySha256,UUID consentProofId){}
 public record Action(long expectedVersion,UUID proofId){}
 private final JdbcTemplate jdbc;private final JsonMapper json;private final Clock clock;
 private final TransactionTemplate transaction;private final String expectedRole;
 public ReferralStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,
   Clock clock,@Value("${synqoria.referrals.role}")String role){
  if(!"synqoria_referral_runtime".equals(role))throw new IllegalStateException("m16_restricted_role_required");
  this.jdbc=jdbc;this.json=json;this.clock=clock;
  this.expectedRole=role;this.transaction=new TransactionTemplate(manager);
 }
 public static String sha(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
    .digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public <T>T scoped(Actor actor,Supplier<T> supplier){
  return Objects.requireNonNull(transaction.execute(t->{
   String current=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean restricted=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls "+
    "AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication "+
    "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!expectedRole.equals(current)||!Boolean.TRUE.equals(restricted))
    throw new Problem("db_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   return supplier.get();
  }));
 }
 private String encode(Object x){return json.writeValueAsString(x);}
 private Object decode(String x){return json.readValue(x,Object.class);}
 private Reply command(Actor actor,UUID key,String name,Object payload,Supplier<Reply> work){
  if(key==null)throw new Problem("idempotency_key_required");
  String digest=sha(name+"|"+encode(payload));
  return scoped(actor,()->{
   jdbc.update("INSERT INTO synqoria_referral.commands(cell_id,facility_id,actor_id,command_id,request_hash) "+
    "VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
    actor.cell(),actor.facility(),actor.id(),key,digest);
   var prior=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status "+
    "FROM synqoria_referral.commands WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    actor.cell(),actor.facility(),actor.id(),key);
   if(!digest.equals(prior.get("request_hash")))throw new Problem("idempotency_collision");
   if(prior.get("http_status")!=null)return new Reply(((Number)prior.get("http_status")).intValue(),
    decode((String)prior.get("response")));
   Reply response=work.get();
   jdbc.update("UPDATE synqoria_referral.commands SET response=?::jsonb,http_status=? "+
    "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=?",
    encode(response.body()),response.status(),actor.cell(),actor.facility(),actor.id(),key);
   return response;
  });
 }
 private static String code(String value){
  if(value==null||!value.matches("[A-Z][A-Z0-9_]{1,63}"))
   throw new Problem("invalid_reason_code");
  return value;
 }
 private static String hash(String value){
  if(value==null||!value.matches("[0-9a-f]{64}"))throw new Problem("invalid_sha256");
  return value;
 }
 private void proof(Actor actor,UUID proof,UUID patient,UUID recipient,UUID referral,String kind){
  if(proof==null||patient==null||recipient==null)throw new Problem("evidence_unavailable");
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,16))",proof.toString());
  var found=jdbc.queryForList("SELECT proof_id FROM synqoria_referral.authority_proofs WHERE "+
   "cell_id=? AND facility_id=? AND proof_id=? AND patient_ref=? "+
   "AND recipient_facility=? AND referral_id IS NOT DISTINCT FROM ?::uuid "+
   "AND kind=? AND external_actor<>? AND revoked_at IS NULL "+
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   actor.cell(),actor.facility(),proof,patient,recipient,referral,kind,actor.id());
  if(found.size()!=1)throw new Problem("evidence_unavailable");
 }
 private Map<String,Object> row(Actor a,UUID id){
  try{return jdbc.queryForMap("SELECT * FROM synqoria_referral.referrals WHERE "+
    "cell_id=? AND facility_id=? AND id=? FOR UPDATE",a.cell(),a.facility(),id);
  }catch(EmptyResultDataAccessException e){throw new Problem("not_found");}
 }
 private void history(Actor actor,UUID key,UUID id,String before,String after,long version,UUID proof){
  UUID event=UUID.randomUUID(),transition=UUID.randomUUID();
  jdbc.update("INSERT INTO synqoria_referral.transition_events(id,cell_id,facility_id,"+
   "referral_id,from_status,to_status,actor_id,version,proof_id) "+
   "VALUES(?,?,?,?,?,?,?,?,?)",
   transition,actor.cell(),actor.facility(),id,before,after,actor.id(),version,proof);
  jdbc.update("INSERT INTO synqoria_referral.audit_events(id,cell_id,facility_id,aggregate_id,"+
   "action,version,actor_id,command_id) VALUES(?,?,?,?,?,?,?,?)",
   event,actor.cell(),actor.facility(),id,after,version,actor.id(),key);
  jdbc.update("INSERT INTO synqoria_referral.outbox(id,cell_id,facility_id,aggregate_id,"+
   "event_type,payload) VALUES(?,?,?,?,?,?::jsonb)",
   event,actor.cell(),actor.facility(),id,"m16.referral."+after,
   encode(Map.of("id",id.toString(),"status",after,"version",version)));
 }
 private Reply result(int status,UUID id,String state,long version){
  return new Reply(status,Map.of("id",id.toString(),"status",state,"version",version));
 }
 public List<Map<String,Object>> list(Actor a,int limit){
  a.require("m16:read");
  if(limit<1||limit>100)throw new Problem("invalid_limit");
  return scoped(a,()->jdbc.queryForList("SELECT id,patient_ref,recipient_facility,reason_code,"+
   "summary_sha256,status,version,created_at,updated_at FROM synqoria_referral.referrals "+
   "WHERE cell_id=? AND facility_id=? ORDER BY created_at DESC,id LIMIT ?",
   a.cell(),a.facility(),limit));
 }
 public List<Map<String,Object>> transitions(Actor a,UUID referral,int limit){
  a.require("m16:read");
  if(limit<1||limit>100)throw new Problem("invalid_limit");
  return scoped(a,()->{
   row(a,referral);
   return jdbc.queryForList("SELECT from_status,to_status,actor_id,version,occurred_at "+
    "FROM synqoria_referral.transition_events WHERE cell_id=? AND facility_id=? AND referral_id=? "+
    "ORDER BY version DESC LIMIT ?",a.cell(),a.facility(),referral,limit);
  });
 }
 public Reply create(Actor a,UUID key,Request req){
  a.require("m16:create");
  if(req==null||req.patientRef()==null||req.recipientFacility()==null||
     req.consentProofId()==null||req.recipientFacility().equals(a.facility()))
   throw new Problem("invalid_referral");
  return command(a,key,"CREATE",req,()->{
   code(req.reasonCode());hash(req.summarySha256());
   proof(a,req.consentProofId(),req.patientRef(),req.recipientFacility(),null,"PATIENT_CONSENT");
   UUID uuid=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_referral.referrals(id,cell_id,facility_id,patient_ref,"+
    "recipient_facility,reason_code,summary_sha256,consent_proof_id,creator_id) "+
    "VALUES(?,?,?,?,?,?,?,?,?)",
    uuid,a.cell(),a.facility(),req.patientRef(),req.recipientFacility(),req.reasonCode(),
    req.summarySha256(),req.consentProofId(),a.id());
   history(a,key,uuid,"NONE","DRAFT",1,req.consentProofId());
   return result(201,uuid,"DRAFT",1);
  });
 }
 public Reply act(Actor a,UUID key,UUID id,String operation,Action action){
  String permission=switch(operation){
   case "authorize"->"m16:authorize";case "send"->"m16:send";
   case "accept","reject"->"m16:receiving_review";
   case "transfer"->"m16:transfer";case "attend"->"m16:attend";
   case "close"->"m16:close";case "cancel"->"m16:cancel";
   default->throw new Problem("invalid_action");
  };
  a.require(permission);
  if(action==null||action.expectedVersion()<1)throw new Problem("invalid_version");
  return command(a,key,"ACTION_"+operation,List.of(id,action),()->{
   var r=row(a,id);
   if(((Number)r.get("version")).longValue()!=action.expectedVersion())
    throw new Problem("stale_version");
   String previous=(String)r.get("status");
   String target=switch(operation){
    case "authorize"->"AUTHORIZED";case "send"->"SENT";
    case "accept"->"ACCEPTED";case "reject"->"REJECTED";
    case "transfer"->"TRANSFERRED";case "attend"->"ATTENDED";
    case "close"->"CLOSED";case "cancel"->"CANCELLED";
    default->throw new Problem("invalid_action");
   };
   boolean allowed=switch(previous){
    case "DRAFT"->Set.of("AUTHORIZED","CANCELLED").contains(target);
    case "AUTHORIZED"->Set.of("SENT","CANCELLED").contains(target);
    case "SENT"->Set.of("ACCEPTED","REJECTED").contains(target);
    case "ACCEPTED"->"TRANSFERRED".equals(target);
    case "TRANSFERRED"->"ATTENDED".equals(target);
    case "ATTENDED"->"CLOSED".equals(target);
    default->false;
   };
   if(!allowed)throw new Problem("invalid_transition");
   UUID patient=(UUID)r.get("patient_ref"),recipient=(UUID)r.get("recipient_facility");
   UUID consent=(UUID)r.get("consent_proof_id"),evidence=action.proofId();
   if(Set.of("AUTHORIZED","SENT").contains(target))
    proof(a,consent,patient,recipient,null,"PATIENT_CONSENT");
   if("AUTHORIZED".equals(target)&&a.id().equals(r.get("creator_id"))||
      "SENT".equals(target)&&a.id().equals(r.get("authorized_by"))||
      "TRANSFERRED".equals(target)&&a.id().equals(r.get("acceptance_actor"))||
      "CLOSED".equals(target)&&a.id().equals(r.get("attended_by")))
    throw new Problem("independent_checker_required");
   if(Set.of("ACCEPTED","REJECTED").contains(target))
    proof(a,evidence,patient,recipient,id,"RECEIVING_DECISION");
   if("TRANSFERRED".equals(target))
    proof(a,evidence,patient,recipient,id,"HANDOVER_RECEIPT");
   if("ATTENDED".equals(target)||"CLOSED".equals(target))
    proof(a,"CLOSED".equals(target)?(UUID)r.get("followup_proof_id"):evidence,
      patient,recipient,id,"FOLLOWUP_VERIFICATION");
   if("CLOSED".equals(target)&&r.get("followup_proof_id")==null)
    throw new Problem("evidence_unavailable");
   String changes=switch(target){
    case "AUTHORIZED"->"authorized_by=?";
    case "SENT"->"sent_by=?";
    case "ACCEPTED","REJECTED"->"acceptance_actor=?";
    case "TRANSFERRED"->"transfer_actor=?";
    case "ATTENDED"->"attended_by=?";
    case "CLOSED"->"closed_by=?";
    default->"updated_at=clock_timestamp()";
   };
   if(target.equals("CANCELLED")){
    jdbc.update("UPDATE synqoria_referral.referrals SET status=?,version=version+1,"+
     "updated_at=clock_timestamp() WHERE id=? AND cell_id=? AND facility_id=?",
     target,id,a.cell(),a.facility());
   }else{
    jdbc.update("UPDATE synqoria_referral.referrals SET status=?,version=version+1,"+changes+","+
     ("ACCEPTED".equals(target)||"REJECTED".equals(target)?"receiving_proof_id=?,decision=?,decision_at=clock_timestamp(),":
      "TRANSFERRED".equals(target)?"handover_proof_id=?,transferred_at=clock_timestamp(),":
      "ATTENDED".equals(target)?"followup_proof_id=?,attended_at=clock_timestamp(),":
      "CLOSED".equals(target)?"closed_at=clock_timestamp(),":"")+
     "updated_at=clock_timestamp() WHERE id=? AND cell_id=? AND facility_id=?",
     params(target,a,id,evidence));
   }
   history(a,key,id,previous,target,action.expectedVersion()+1,evidence);
   return result(200,id,target,action.expectedVersion()+1);
  });
 }
 private static Object[] params(String target,Actor actor,UUID id,UUID proof){
  var args=new ArrayList<Object>();
  args.add(target);args.add(actor.id());
  if(Set.of("ACCEPTED","REJECTED").contains(target)){
   args.add(proof);args.add(target);
  }else if("TRANSFERRED".equals(target)||"ATTENDED".equals(target))args.add(proof);
  args.add(id);args.add(actor.cell());args.add(actor.facility());
  return args.toArray();
 }
}
