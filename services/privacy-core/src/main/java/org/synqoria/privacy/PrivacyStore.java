package org.synqoria.privacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.synqoria.privacy.Rules.*;

/** Atomic, role-verified, hospital-cell-scoped consent and disclosure transactions.
 * Writes only M32-owned records; it cannot issue legal proofs or release clinical data.
 */
@Component
public class PrivacyStore {
 public record Reply(int status,Object body){}
 private final JdbcTemplate jdbc;
 private final JsonMapper json;
 private final Clock clock;
 private final TransactionTemplate transaction;
 private final String expectedRole;
 public PrivacyStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,Clock clock,
       @Value("${synqoria.privacy.db-runtime-role}")String role){
  if(!"synqoria_privacy_runtime".equals(role))throw new IllegalStateException("privacy_requires_independent_nonowner_role");
  this.jdbc=jdbc;this.json=json;this.clock=clock;this.transaction=new TransactionTemplate(manager);this.expectedRole=role;
 }
 public <T> T scoped(Actor actor,Supplier<T> op){
  return Objects.requireNonNull(transaction.execute(t->{
   String current=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean restricted=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls "+
     "AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication "+
     "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!expectedRole.equals(current)||!Boolean.TRUE.equals(restricted))throw new Problem("db_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   return op.get();
  }));
 }
 static String sha(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
   .digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 String encode(Object body){return json.writeValueAsString(body);}
 Object decode(String text){return json.readValue(text,Object.class);}
 public Reply command(Actor actor,UUID key,String operation,Object input,Supplier<Reply> op){
  if(key==null)throw new Problem("idempotency_key_required");
  String digest=sha(operation+"|"+encode(input));
  return scoped(actor,()->{
   jdbc.update("INSERT INTO synqoria_privacy.commands(cell_id,facility_id,actor_id,command_id,request_hash) "+
    "VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
    actor.cell(),actor.facility(),actor.id(),key,digest);
   var prior=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status "+
    "FROM synqoria_privacy.commands WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    actor.cell(),actor.facility(),actor.id(),key);
   if(!digest.equals(prior.get("request_hash")))throw new Problem("idempotency_collision");
   if(prior.get("http_status")!=null)
    return new Reply(((Number)prior.get("http_status")).intValue(),decode((String)prior.get("response")));
   Reply result=op.get();
   if(jdbc.update("UPDATE synqoria_privacy.commands SET response=?::jsonb,http_status=? "+
      "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? AND http_status IS NULL",
      encode(result.body()),result.status(),actor.cell(),actor.facility(),actor.id(),key)!=1)
    throw new Problem("idempotency_collision");
   return result;
  });
 }
 private Map<String,Object> row(String table,Actor actor,UUID id){
  if(!Set.of("directives","disclosures","privacy_cases").contains(table))throw new IllegalArgumentException("table");
  try{return jdbc.queryForMap("SELECT * FROM synqoria_privacy."+table+
    " WHERE cell_id=? AND facility_id=? AND id=? FOR UPDATE",actor.cell(),actor.facility(),id);
  }catch(EmptyResultDataAccessException e){throw new Problem("not_found");}
 }
 private void version(Map<String,Object> item,long expected){
  if(((Number)item.get("version")).longValue()!=expected)throw new Problem("stale_version");
 }
 private Map<String,Object> result(String table,Actor actor,UUID id){
  var row=row(table,actor,id);
  return Map.of("id",id.toString(),"status",String.valueOf(row.get("status")),
   "version",((Number)row.get("version")).longValue());
 }
 private void event(Actor actor,UUID key,String type,UUID id,String action,long version){
  UUID eventId=UUID.randomUUID();
  String payload=encode(Map.of("aggregateId",id.toString(),"action",action,"version",version));
  String digest=sha(type+"|"+id+"|"+action+"|"+version+"|"+actor.id()+"|"+key);
  jdbc.update("INSERT INTO synqoria_privacy.audit_events(event_id,cell_id,facility_id,"+
   "aggregate_type,aggregate_id,action,aggregate_version,actor_id,command_id,digest_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?)",
   eventId,actor.cell(),actor.facility(),type,id,action,version,actor.id(),key,digest);
  jdbc.update("INSERT INTO synqoria_privacy.outbox(event_id,cell_id,facility_id,aggregate_id,event_type,payload) "+
   "VALUES(?,?,?,?,?,?::jsonb)",eventId,actor.cell(),actor.facility(),id,"m32."+type+"."+action,payload);
 }
 /** Only separately issued, unrevoked attestations can establish a consent.
  * FOR SHARE prevents a concurrent independent issuer revocation from racing this check.
  */
 private void attested(Actor actor,Map<String,Object> directive,UUID proof){
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,32))",
   proof.toString());
  var matches=jdbc.queryForList(
   "SELECT proof_id FROM synqoria_privacy.authority_evidence "+
   "WHERE cell_id=? AND facility_id=? AND proof_id=? AND subject_ref=? "+
   "AND purpose=? AND recipient=? AND operation=? AND approved_by<>? "+
   "AND revoked_at IS NULL AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp() "+
   "",actor.cell(),actor.facility(),proof,directive.get("subject_ref"),
   directive.get("purpose"),directive.get("recipient"),directive.get("operation"),actor.id());
  if(matches.size()!=1)throw new Problem("authority_evidence_required");
 }
 private boolean active(Actor actor,Map<String,Object> d){
  if(!"ACTIVE".equals(d.get("status")))return false;
  java.time.Instant now=clock.instant();
  java.time.Instant start=((java.sql.Timestamp)d.get("effective_at")).toInstant();
  java.time.Instant until=((java.sql.Timestamp)d.get("expires_at")).toInstant();
  if(!allowed("ACTIVE",start,until,now,true))return false;
  UUID proof=(UUID)d.get("proof_id");
  if(proof==null)return false;
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,32))",
    proof.toString());
  // Validated for the actual subject/scope, but do not require a different checker here:
  // the original checker and the reader may be the same person at a later time.
  Long n=jdbc.queryForObject(
   "SELECT count(*) FROM synqoria_privacy.authority_evidence WHERE "+
   "cell_id=? AND facility_id=? AND proof_id=? AND subject_ref=? AND purpose=? "+
   "AND recipient=? AND operation=? AND revoked_at IS NULL "+
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   Long.class,actor.cell(),actor.facility(),proof,d.get("subject_ref"),
   d.get("purpose"),d.get("recipient"),d.get("operation"));
  return n!=null&&n==1;
 }
 public Reply create(Actor actor,UUID key,Directive request){
  actor.require("m32:directive_create");
  if(request==null)throw new Problem("invalid_directive");
  return command(actor,key,"CREATE_DIRECTIVE",request,()->{
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_privacy.directives(id,cell_id,facility_id,subject_ref,purpose,recipient,"+
     "operation,effective_at,expires_at,creator_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
     id,actor.cell(),actor.facility(),request.subjectRef(),request.purpose(),request.recipient(),
     request.operation(),java.sql.Timestamp.from(request.effectiveAt()),
     java.sql.Timestamp.from(request.expiresAt()),actor.id());
   event(actor,key,"directive",id,"CREATE",1);
   return new Reply(201,result("directives",actor,id));
  });
 }
 public Reply verify(Actor actor,UUID key,UUID id,Verification v){
  actor.require("m32:directive_verify");
  if(v==null)throw new Problem("invalid_evidence");
  return command(actor,key,"VERIFY_DIRECTIVE",List.of(id,v),()->{
   var d=row("directives",actor,id);
   version(d,v.expectedVersion());
   transition((String)d.get("status"),"VERIFIED");
   if(actor.id().equals(d.get("creator_id")))throw new Problem("independent_checker_required");
   attested(actor,d,v.evidenceId());
   jdbc.update("UPDATE synqoria_privacy.directives SET status='VERIFIED',version=version+1,"+
    "verified_by=?,proof_id=?,updated_at=clock_timestamp() WHERE id=? AND cell_id=? AND facility_id=?",
    actor.id(),v.evidenceId(),id,actor.cell(),actor.facility());
   event(actor,key,"directive",id,"VERIFY",v.expectedVersion()+1);
   return new Reply(200,result("directives",actor,id));
  });
 }
 public Reply activate(Actor actor,UUID key,UUID id,Version v){
  actor.require("m32:directive_activate");
  if(v==null)throw new Problem("invalid_version");
  return command(actor,key,"ACTIVATE_DIRECTIVE",List.of(id,v),()->{
   var d=row("directives",actor,id);
   version(d,v.expectedVersion());
   transition((String)d.get("status"),"ACTIVE");
   if(actor.id().equals(d.get("creator_id"))||actor.id().equals(d.get("verified_by")))
    throw new Problem("independent_checker_required");
   attested(actor,d,(UUID)d.get("proof_id"));
   if(!((java.sql.Timestamp)d.get("expires_at")).toInstant().isAfter(clock.instant()))
    throw new Problem("invalid_effective_window");
   jdbc.update("UPDATE synqoria_privacy.directives SET status='ACTIVE',version=version+1,"+
    "activated_by=?,updated_at=clock_timestamp() WHERE id=? AND cell_id=? AND facility_id=?",
    actor.id(),id,actor.cell(),actor.facility());
   event(actor,key,"directive",id,"ACTIVATE",v.expectedVersion()+1);
   return new Reply(200,result("directives",actor,id));
  });
 }
 public Reply revoke(Actor actor,UUID key,UUID id,Version v){
  actor.require("m32:directive_revoke");
  if(v==null)throw new Problem("invalid_version");
  return command(actor,key,"REVOKE_DIRECTIVE",List.of(id,v),()->{
   var d=row("directives",actor,id);
   version(d,v.expectedVersion());
   transition((String)d.get("status"),"REVOKED");
   jdbc.update("UPDATE synqoria_privacy.directives SET status='REVOKED',version=version+1,"+
    "revoked_by=?,revoked_at=clock_timestamp(),updated_at=clock_timestamp() "+
    "WHERE id=? AND cell_id=? AND facility_id=?",actor.id(),id,actor.cell(),actor.facility());
   event(actor,key,"directive",id,"REVOKE",v.expectedVersion()+1);
   return new Reply(200,result("directives",actor,id));
  });
 }
 /** Exact, time-of-use consent predicate. Only Boolean eligibility is returned; no PHI. */
 public Map<String,Object> evaluate(Actor actor,UUID subject,String purpose,String recipient,String operation){
  actor.require("m32:policy_evaluate");
  code(purpose);code(recipient);code(operation);
  return scoped(actor,()->{
   var items=jdbc.queryForList("SELECT * FROM synqoria_privacy.directives "+
    "WHERE cell_id=? AND facility_id=? AND subject_ref=? AND purpose=? AND recipient=? AND operation=? "+
    "AND status='ACTIVE' ORDER BY created_at DESC,id LIMIT 100",
    actor.cell(),actor.facility(),subject,purpose,recipient,operation);
   boolean permitted=items.stream().anyMatch(d->active(actor,d));
   return Map.of("allowed",permitted,"evaluatedAt",clock.instant().toString());
  });
 }
 private Map<String,Object> scopedActive(Actor actor,UUID id,Disclosure request){
  var d=row("directives",actor,id);
  if(!d.get("subject_ref").equals(request.subjectRef())||
   !d.get("purpose").equals(request.purpose())||
   !d.get("recipient").equals(request.recipient())||
   !d.get("operation").equals(request.operation())||!active(actor,d))
    throw new Problem("consent_not_active");
  return d;
 }
 public Reply requestDisclosure(Actor actor,UUID key,Disclosure req){
  actor.require("m32:disclosure_request");
  if(req==null)throw new Problem("invalid_disclosure");
  return command(actor,key,"REQUEST_DISCLOSURE",req,()->{
   scopedActive(actor,req.directiveId(),req);
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_privacy.disclosures(id,cell_id,facility_id,subject_ref,directive_id,"+
    "purpose,recipient,operation,record_class,requester_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
    id,actor.cell(),actor.facility(),req.subjectRef(),req.directiveId(),req.purpose(),
    req.recipient(),req.operation(),req.recordClass(),actor.id());
   event(actor,key,"disclosure",id,"REQUEST",1);
   return new Reply(201,result("disclosures",actor,id));
  });
 }
 public Reply reviewDisclosure(Actor actor,UUID key,UUID id,Decision decision){
  actor.require("m32:disclosure_review");
  if(decision==null)throw new Problem("invalid_decision");
  return command(actor,key,"REVIEW_DISCLOSURE",List.of(id,decision),()->{
   var d=row("disclosures",actor,id);
   version(d,decision.expectedVersion());
   if(!"PENDING".equals(d.get("status")))throw new Problem("invalid_transition");
   if(actor.id().equals(d.get("requester_id")))throw new Problem("independent_checker_required");
   if(decision.approve()){
    scopedActive(actor,(UUID)d.get("directive_id"),
     new Disclosure((UUID)d.get("subject_ref"),(UUID)d.get("directive_id"),
      (String)d.get("purpose"),(String)d.get("recipient"),
      (String)d.get("operation"),(String)d.get("record_class")));
   }
   String status=decision.approve()?"AUTHORIZED":"DENIED";
   jdbc.update("UPDATE synqoria_privacy.disclosures SET status=?,version=version+1,"+
    "reviewer_id=?,reason=?,reviewed_at=clock_timestamp() "+
    "WHERE id=? AND cell_id=? AND facility_id=?",status,actor.id(),
    decision.approve()?"AUTHORIZED":decision.reason(),id,actor.cell(),actor.facility());
   event(actor,key,"disclosure",id,status,decision.expectedVersion()+1);
   return new Reply(200,result("disclosures",actor,id));
  });
 }
 public Reply openCase(Actor actor,UUID key,PrivacyCase request){
  actor.require("m32:case_create");
  if(request==null)throw new Problem("invalid_case");
  return command(actor,key,"OPEN_CASE",request,()->{
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_privacy.privacy_cases(id,cell_id,facility_id,subject_ref,"+
    "category,reason,opened_by) VALUES(?,?,?,?,?,?,?)",
    id,actor.cell(),actor.facility(),request.subjectRef(),request.category(),request.reason(),actor.id());
   event(actor,key,"privacy_case",id,"OPEN",1);
   return new Reply(201,result("privacy_cases",actor,id));
  });
 }
 public Reply closeCase(Actor actor,UUID key,UUID id,Decision decision){
  actor.require("m32:case_review");
  if(decision==null||!decision.approve())throw new Problem("invalid_decision");
  return command(actor,key,"CLOSE_CASE",List.of(id,decision),()->{
   var c=row("privacy_cases",actor,id);
   version(c,decision.expectedVersion());
   if(!"OPEN".equals(c.get("status")))throw new Problem("invalid_transition");
   if(actor.id().equals(c.get("opened_by")))throw new Problem("independent_checker_required");
   jdbc.update("UPDATE synqoria_privacy.privacy_cases SET status='CLOSED',version=version+1,"+
    "closed_by=?,close_reason=?,closed_at=clock_timestamp() WHERE id=? AND cell_id=? AND facility_id=?",
    actor.id(),decision.reason(),id,actor.cell(),actor.facility());
   event(actor,key,"privacy_case",id,"CLOSE",decision.expectedVersion()+1);
   return new Reply(200,result("privacy_cases",actor,id));
  });
 }
 public List<Map<String,Object>> listing(Actor actor,String table,int limit){
  actor.require("m32:read");
  if(!Set.of("directives","disclosures","privacy_cases").contains(table))throw new Problem("invalid_collection");
  if(limit<1||limit>100)throw new Problem("invalid_limit");
  return scoped(actor,()->jdbc.queryForList(
   "SELECT * FROM synqoria_privacy."+table+" WHERE cell_id=? AND facility_id=? "+
   "ORDER BY created_at DESC,id LIMIT ?",actor.cell(),actor.facility(),limit));
 }
}
