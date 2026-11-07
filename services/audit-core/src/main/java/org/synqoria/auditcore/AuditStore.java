package org.synqoria.auditcore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.synqoria.auditcore.Rules.*;

/** Hospital-cell independent append-only evidence store; metadata/digests only. */
@Component
public class AuditStore{
 public record Reply(int status,Object body){}
 private final JdbcTemplate jdbc;private final JsonMapper json;private final Clock clock;
 private final TransactionTemplate tx;private final String role;
 public AuditStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,
                   Clock clock,@Value("${synqoria.audit.db-runtime-role}")String role){
  if(!"synqoria_audit_runtime".equals(role))throw new IllegalStateException("audit_runtime_must_be_nonowner");
  this.jdbc=jdbc;this.json=json;this.clock=clock;this.role=role;this.tx=new TransactionTemplate(manager);
 }
 public static String sha(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
   .digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String envelope(Ingest in){
  return sha("M34|"+in.streamId()+"|"+in.sourceId()+"|"+in.sourceEventId()+"|"+
   in.sourceActor()+"|"+(in.subjectRef()==null?"-":in.subjectRef().toString())+"|"+
   in.sequence()+"|"+in.action()+"|"+in.outcome()+"|"+in.payloadSha256()+"|"+
   in.occurredAt().truncatedTo(ChronoUnit.MICROS));
 }
 public static String eventHash(UUID cell,UUID facility,Ingest in,String previous){
  return sha("M34-EVENT|"+cell+"|"+facility+"|"+in.streamId()+"|"+in.sequence()+"|"+
   previous+"|"+envelope(in));
 }
 public <T>T scoped(Actor actor,Supplier<T> body){
  return Objects.requireNonNull(tx.execute(t->{
   String user=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean restricted=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls "+
    "AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication "+
    "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!role.equals(user)||!Boolean.TRUE.equals(restricted))throw new Problem("db_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   return body.get();
  }));
 }
 private String encode(Object o){return json.writeValueAsString(o);}
 private Object decode(String s){return json.readValue(s,Object.class);}
 private Reply command(Actor actor,UUID key,String operation,Object request,Supplier<Reply> op){
  if(key==null)throw new Problem("idempotency_key_required");
  String digest=sha(operation+"|"+encode(request));
  return scoped(actor,()->{
   jdbc.update("INSERT INTO synqoria_audit.commands(cell_id,facility_id,actor_id,command_id,request_hash) "+
    "VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
    actor.cell(),actor.facility(),actor.id(),key,digest);
   var row=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status "+
    "FROM synqoria_audit.commands WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    actor.cell(),actor.facility(),actor.id(),key);
   if(!digest.equals(row.get("request_hash")))throw new Problem("idempotency_collision");
   if(row.get("http_status")!=null)
    return new Reply(((Number)row.get("http_status")).intValue(),decode((String)row.get("response")));
   Reply response=op.get();
   jdbc.update("UPDATE synqoria_audit.commands SET response=?::jsonb,http_status=? WHERE "+
    "cell_id=? AND facility_id=? AND actor_id=? AND command_id=? AND http_status IS NULL",
    encode(response.body()),response.status(),actor.cell(),actor.facility(),actor.id(),key);
   return response;
  });
 }
 private Map<String,Object> row(String table,Actor actor,UUID id){
  if(!Set.of("evidence_events","export_requests","retention_reviews").contains(table))
   throw new Problem("invalid_collection");
  try{return jdbc.queryForMap("SELECT * FROM synqoria_audit."+table+
   " WHERE cell_id=? AND facility_id=? AND id=?"+(table.equals("evidence_events")?"":" FOR UPDATE"),
   actor.cell(),actor.facility(),id);}
  catch(EmptyResultDataAccessException e){throw new Problem("not_found");}
 }
 private void log(Actor actor,UUID key,String type,UUID id,String action,long version){
  UUID event=UUID.randomUUID();
  String digest=sha("M34-ACTION|"+type+"|"+id+"|"+action+"|"+version+"|"+actor.id()+"|"+key);
  jdbc.update("INSERT INTO synqoria_audit.audit_actions(event_id,cell_id,facility_id,"+
   "aggregate_type,aggregate_id,action,version,actor_id,command_id,digest_sha256) "+
   "VALUES(?,?,?,?,?,?,?,?,?,?)",
   event,actor.cell(),actor.facility(),type,id,action,version,actor.id(),key,digest);
  String payload=encode(Map.of("id",id.toString(),"version",version,"action",action));
  jdbc.update("INSERT INTO synqoria_audit.outbox(event_id,cell_id,facility_id,"+
   "event_type,aggregate_id,payload) VALUES(?,?,?,?,?,?::jsonb)",
   event,actor.cell(),actor.facility(),"m34."+type+"."+action,id,payload);
 }
 private void attested(Actor actor,Ingest event){
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,34))",
   event.proofId().toString());
  var n=jdbc.queryForList("SELECT proof_id FROM synqoria_audit.ingest_attestations "+
   "WHERE cell_id=? AND facility_id=? AND proof_id=? AND source_id=? "+
   "AND source_event_id=? AND stream_id=? AND source_actor=? "+
   "AND subject_ref IS NOT DISTINCT FROM ?::uuid AND sequence=? "+
   "AND payload_sha256=? AND event_action=? AND event_outcome=? AND occurred_at=? "+
   "AND envelope_sha256=? AND approved_by<>? "+
   "AND revoked_at IS NULL AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   actor.cell(),actor.facility(),event.proofId(),event.sourceId(),event.sourceEventId(),
   event.streamId(),event.sourceActor(),event.subjectRef(),event.sequence(),event.payloadSha256(),
   event.action(),event.outcome(),Timestamp.from(event.occurredAt().truncatedTo(ChronoUnit.MICROS)),
   envelope(event),actor.id());
  if(n.size()!=1)throw new Problem("audit_attestation_required");
 }
 private static String asString(Object x){return String.valueOf(x);}
 private static long asLong(Object x){return ((Number)x).longValue();}
 private void version(Map<String,Object> r,long v){
  if(asLong(r.get("version"))!=v)throw new Problem("stale_version");
 }
 public Reply ingest(Actor actor,UUID key,Ingest in){
  actor.require("m34:ingest");
  if(in==null)throw new Problem("invalid_ingest");
  return command(actor,key,"INGEST",in,()->{
   attested(actor,in);
   jdbc.update("INSERT INTO synqoria_audit.stream_heads(cell_id,facility_id,stream_id) "+
    "VALUES(?,?,?) ON CONFLICT DO NOTHING",actor.cell(),actor.facility(),in.streamId());
   var head=jdbc.queryForMap("SELECT last_sequence,last_hash FROM synqoria_audit.stream_heads "+
    "WHERE cell_id=? AND facility_id=? AND stream_id=? FOR UPDATE",
    actor.cell(),actor.facility(),in.streamId());
   long next=asLong(head.get("last_sequence"))+1;
   if(next!=in.sequence())throw new Problem("stale_source_sequence");
   String previous=asString(head.get("last_hash"));
   String hash=eventHash(actor.cell(),actor.facility(),in,previous);
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_audit.evidence_events(id,cell_id,facility_id,stream_id,"+
    "source_id,source_event_id,source_actor,subject_ref,sequence,action,outcome,payload_sha256,"+
    "previous_sha256,event_sha256,proof_id,occurred_at) "+
    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
    id,actor.cell(),actor.facility(),in.streamId(),in.sourceId(),in.sourceEventId(),
    in.sourceActor(),in.subjectRef(),in.sequence(),in.action(),in.outcome(),
    in.payloadSha256(),previous,hash,in.proofId(),
    Timestamp.from(in.occurredAt().truncatedTo(ChronoUnit.MICROS)));
   jdbc.update("UPDATE synqoria_audit.stream_heads SET last_sequence=?,last_hash=?,"+
    "updated_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND stream_id=?",
    next,hash,actor.cell(),actor.facility(),in.streamId());
   log(actor,key,"evidence",id,"INGEST",next);
   return new Reply(201,Map.of("id",id.toString(),"streamId",in.streamId().toString(),
    "sequence",next,"hash",hash,"previousHash",previous));
  });
 }
 /** Full scan is bounded. On >10k events, explicitly return incomplete rather than a false pass. */
 private Map<String,Object> verifyChain(Actor actor,UUID streamId){
  Long count=jdbc.queryForObject("SELECT count(*) FROM synqoria_audit.evidence_events "+
   "WHERE cell_id=? AND facility_id=? AND stream_id=?",
   Long.class,actor.cell(),actor.facility(),streamId);
  if(count==null||count>10000)return Map.of("verified",false,"reason","CHAIN_TOO_LARGE","count",count==null?0:count);
  var rows=jdbc.queryForList("SELECT * FROM synqoria_audit.evidence_events "+
   "WHERE cell_id=? AND facility_id=? AND stream_id=? ORDER BY sequence",
   actor.cell(),actor.facility(),streamId);
  String prior="0".repeat(64);
  long seq=0;
  for(var e:rows){
   seq++;
   var in=new Ingest((UUID)e.get("stream_id"),(UUID)e.get("source_id"),
    (UUID)e.get("source_event_id"),(UUID)e.get("source_actor"),(UUID)e.get("subject_ref"),
    asLong(e.get("sequence")),(String)e.get("action"),(String)e.get("outcome"),
    (String)e.get("payload_sha256"),(UUID)e.get("proof_id"),
    ((Timestamp)e.get("occurred_at")).toInstant());
   String actual=eventHash(actor.cell(),actor.facility(),in,prior);
   if(in.sequence()!=seq||!actual.equals(e.get("event_sha256"))||
     !prior.equals(e.get("previous_sha256")))
    return Map.of("verified",false,"reason","CHAIN_MISMATCH","count",seq);
   prior=actual;
  }
  var h=jdbc.queryForList("SELECT last_sequence,last_hash FROM synqoria_audit.stream_heads "+
   "WHERE cell_id=? AND facility_id=? AND stream_id=?",
   actor.cell(),actor.facility(),streamId);
  if(h.size()!=1||seq!=asLong(h.getFirst().get("last_sequence"))||
    !prior.equals(h.getFirst().get("last_hash")))
   return Map.of("verified",false,"reason","HEAD_MISMATCH","count",seq);
  return Map.of("verified",true,"count",seq,"headHash",prior);
 }
 public Map<String,Object> verify(Actor actor,UUID streamId){
  actor.require("m34:timeline");
  return scoped(actor,()->verifyChain(actor,streamId));
 }
 public List<Map<String,Object>> list(Actor actor,String table,int limit){
  if(limit<1||limit>100)throw new Problem("invalid_limit");
  if(!Set.of("evidence_events","export_requests","retention_reviews","retention_holds").contains(table))
   throw new Problem("invalid_collection");
  actor.require(switch(table){
   case "evidence_events"->"m34:search";
   case "export_requests"->"m34:manifest_read";
   case "retention_reviews","retention_holds"->"m34:hold_read";
   default->"m34:read";
  });
  return scoped(actor,()->jdbc.queryForList("SELECT * FROM synqoria_audit."+table+
   " WHERE cell_id=? AND facility_id=? ORDER BY "+
   (table.equals("evidence_events")?"recorded_at":
     table.equals("retention_holds")?"issued_at":"created_at")+" DESC LIMIT ?",
   actor.cell(),actor.facility(),limit));
 }
 public Map<String,Object> detail(Actor actor,UUID id){
  actor.require("m34:read");
  return scoped(actor,()->row("evidence_events",actor,id));
 }
 public List<Map<String,Object>> timeline(Actor actor,UUID streamId,int limit){
  actor.require("m34:timeline");
  if(limit<1||limit>100)throw new Problem("invalid_limit");
  return scoped(actor,()->jdbc.queryForList("SELECT * FROM synqoria_audit.evidence_events "+
   "WHERE cell_id=? AND facility_id=? AND stream_id=? ORDER BY sequence DESC LIMIT ?",
   actor.cell(),actor.facility(),streamId,limit));
 }
 public Reply requestExport(Actor actor,UUID key,ExportRequest request){
  actor.require("m34:export_request");
  if(request==null)throw new Problem("invalid_export");
  return command(actor,key,"EXPORT_REQUEST",request,()->{
   if(!request.expiresAt().isAfter(clock.instant())||
      request.expiresAt().isAfter(clock.instant().plusSeconds(86400)))
    throw new Problem("invalid_export_expiry");
   Long count=jdbc.queryForObject("SELECT count(*) FROM synqoria_audit.evidence_events "+
    "WHERE cell_id=? AND facility_id=? AND stream_id=? AND sequence BETWEEN ? AND ?",
    Long.class,actor.cell(),actor.facility(),request.streamId(),
    request.fromSequence(),request.toSequence());
   if(count==null||count!=request.toSequence()-request.fromSequence()+1)
    throw new Problem("export_range_incomplete");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_audit.export_requests(id,cell_id,facility_id,stream_id,"+
    "purpose,recipient,from_sequence,to_sequence,expires_at,requested_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?,?)",
    id,actor.cell(),actor.facility(),request.streamId(),request.purpose(),
    request.recipient(),request.fromSequence(),request.toSequence(),
    Timestamp.from(request.expiresAt()),actor.id());
   log(actor,key,"export",id,"REQUEST",1);
   return new Reply(201,Map.of("id",id.toString(),"status","PROPOSED","version",1));
  });
 }
 public Reply reviewExport(Actor actor,UUID key,UUID id,Decision decision){
  actor.require("m34:export_review");
  if(decision==null)throw new Problem("invalid_decision");
  return command(actor,key,"EXPORT_REVIEW",List.of(id,decision),()->{
   var row=row("export_requests",actor,id);
   version(row,decision.expectedVersion());
   step((String)row.get("status"),decision.approve()?"APPROVED":"DENIED");
   if(actor.id().equals(row.get("requested_by")))throw new Problem("independent_checker_required");
   if(!((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant()))
    throw new Problem("export_expired");
   String manifest=null;
   if(decision.approve()){
    var chain=verifyChain(actor,(UUID)row.get("stream_id"));
    if(!Boolean.TRUE.equals(chain.get("verified")))throw new Problem("audit_chain_invalid");
    manifest=sha("M34-MANIFEST|"+row.get("stream_id")+"|"+row.get("from_sequence")+"|"+
     row.get("to_sequence")+"|"+row.get("purpose")+"|"+row.get("recipient")+"|"+chain.get("headHash"));
   }
   String result=decision.approve()?"APPROVED":"DENIED";
   jdbc.update("UPDATE synqoria_audit.export_requests SET status=?,version=version+1,"+
    "reviewed_by=?,reviewed_at=clock_timestamp(),decision_reason=?,manifest_sha256=? "+
    "WHERE cell_id=? AND facility_id=? AND id=?",result,actor.id(),decision.reason(),manifest,
    actor.cell(),actor.facility(),id);
   log(actor,key,"export",id,result,decision.expectedVersion()+1);
   return new Reply(200,Map.of("id",id.toString(),"status",result,"version",decision.expectedVersion()+1,
    "clinicalDataExported",false));
  });
 }
 public Map<String,Object> manifest(Actor actor,UUID id){
  actor.require("m34:manifest_read");
  return scoped(actor,()->{
   var x=row("export_requests",actor,id);
   if(!"APPROVED".equals(x.get("status")))throw new Problem("invalid_transition");
   if(!((Timestamp)x.get("expires_at")).toInstant().isAfter(clock.instant()))
    throw new Problem("export_expired");
   Map<String,Object> integrity=verifyChain(actor,(UUID)x.get("stream_id"));
   if(!Boolean.TRUE.equals(integrity.get("verified")))throw new Problem("audit_chain_invalid");
   String refreshed=sha("M34-MANIFEST|"+x.get("stream_id")+"|"+x.get("from_sequence")+"|"+
    x.get("to_sequence")+"|"+x.get("purpose")+"|"+x.get("recipient")+"|"+integrity.get("headHash"));
   // If the stream has moved, the original manifest no longer matches: fail closed.
   if(!refreshed.equals(x.get("manifest_sha256")))throw new Problem("audit_chain_invalid");
   return Map.of("manifestSha256",refreshed,"streamId",x.get("stream_id").toString(),
    "clinicalDataExported",false,"signedObjectStatus","NOT_CONNECTED",
    "validUntil",((Timestamp)x.get("expires_at")).toInstant().toString());
  });
 }
 public Reply requestRetention(Actor actor,UUID key,RetentionReview request){
  actor.require("m34:retention_request");
  if(request==null)throw new Problem("invalid_retention");
  return command(actor,key,"RETENTION_REQUEST",request,()->{
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_audit.retention_reviews(id,cell_id,facility_id,"+
    "stream_id,policy,reason,requested_by) VALUES(?,?,?,?,?,?,?)",
    id,actor.cell(),actor.facility(),request.streamId(),request.policy(),request.reason(),actor.id());
   log(actor,key,"retention",id,"REQUEST",1);
   return new Reply(201,Map.of("id",id.toString(),"status","PROPOSED","version",1));
  });
 }
 public Reply reviewRetention(Actor actor,UUID key,UUID id,Decision decision){
  actor.require("m34:retention_review");
  if(decision==null)throw new Problem("invalid_decision");
  return command(actor,key,"RETENTION_REVIEW",List.of(id,decision),()->{
   var item=row("retention_reviews",actor,id);
   version(item,decision.expectedVersion());
   step((String)item.get("status"),decision.approve()?"APPROVED":"DENIED");
   if(actor.id().equals(item.get("requested_by")))throw new Problem("independent_checker_required");
   if(decision.approve()){
    Long holds=jdbc.queryForObject("SELECT count(*) FROM synqoria_audit.retention_holds "+
     "WHERE cell_id=? AND facility_id=? AND stream_id=? AND rescinded_at IS NULL",
     Long.class,actor.cell(),actor.facility(),item.get("stream_id"));
    if(holds==null||holds>0)throw new Problem("active_legal_hold");
   }
   String result=decision.approve()?"APPROVED":"DENIED";
   jdbc.update("UPDATE synqoria_audit.retention_reviews SET status=?,version=version+1,"+
    "reviewed_by=?,reviewed_at=clock_timestamp() "+
    "WHERE cell_id=? AND facility_id=? AND id=?",
    result,actor.id(),actor.cell(),actor.facility(),id);
   log(actor,key,"retention",id,result,decision.expectedVersion()+1);
   // A review never invokes data deletion or storage purging.
   return new Reply(200,Map.of("id",id.toString(),"status",result,"version",decision.expectedVersion()+1,
    "recordsDisposed",false));
  });
 }
}
