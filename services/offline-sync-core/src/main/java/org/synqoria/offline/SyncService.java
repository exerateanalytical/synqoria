package org.synqoria.offline;

import static org.synqoria.offline.Db.m;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.synqoria.offline.Db.Reply;
import org.synqoria.offline.domain.Domain;
import org.synqoria.offline.domain.Domain.*;

/** Contracts, devices, authoritative heads, the sync protocol, conflicts and history. Never owns clinical data. */
@Service
public class SyncService {
 final Db db;
 static final int MAX_BATCH=50;
 static final Pattern FIELD=Pattern.compile("[a-z][a-z0-9_]{0,39}");
 static final Pattern HASH=Pattern.compile("[0-9a-f]{64}");
 static final Set<String> REASONS=Set.of("CLINICAL_REVIEW","DUPLICATE_ENTRY","SUPERSEDED_BY_NEWER","DATA_ERROR","OTHER");
 public SyncService(Db db){this.db=db;}

 public record ContractRequest(String operationType,String recordType,String ownerDomain,boolean offlineAllowed,boolean requiresSafetyGate,boolean mergeable,int maxQueueAgeMinutes){}
 public record Decision(long expectedVersion,boolean approve){}
 public record DeviceRequest(String deviceKey,String label,UUID boundUser,int sessionMinutes,int queueLimit){}
 public record Revoke(long expectedVersion,String reason){}
 public record HeadRequest(String recordType,String recordKey,UUID ownerRef,String headHash){}
 public record RevisionRequest(long expectedVersion,String headHash,List<String> changedFields){}
 public record CommandIn(UUID commandId,long seq,String operationType,String recordType,String recordKey,long baseVersion,long baseEpoch,List<String> fields,
   Map<String,Object> payload,Instant createdLocalAt,Instant expiresAt){}
 public record BatchRequest(UUID deviceId,List<CommandIn> commands){}
 public record Resolution(long expectedVersion,String resolution,String reasonCode,String note,long expectedHeadVersion){}

 static <T> T req(T v){if(v==null)throw new Violation("malformed_request");return v;}
 static String lit(Collection<String> v){
  for(String f:v)if(!FIELD.matcher(f).matches())throw new Violation("invalid_field");
  return "{"+String.join(",",new TreeSet<>(v))+"}";
 }
 Timestamp ts(Instant i){return Timestamp.from(i);}

 // ------------------------------------------------------------------ contracts (B01)
 public Reply createContract(Actor a,UUID cmd,ContractRequest r){
  a.require(Perm.CONTRACT_MANAGE);req(r);
  if(!Domain.validOp(r.operationType())||!Domain.validKey(r.recordType())||r.ownerDomain()==null||r.ownerDomain().isBlank()||r.maxQueueAgeMinutes()<1||r.maxQueueAgeMinutes()>20160)
   throw new Violation("invalid_contract");
  if(r.requiresSafetyGate()&&r.offlineAllowed())throw new Violation("gate_means_online_only");
  return db.command(a,cmd,"CONTRACT_CREATE",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_offline.contracts(id,cell_id,facility_id,operation_type,record_type,owner_domain,offline_allowed,requires_safety_gate,mergeable,max_queue_age_minutes,status,created_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT',?,1)",id,a.cell(),a.facility(),r.operationType(),r.recordType(),r.ownerDomain().trim(),r.offlineAllowed(),r.requiresSafetyGate(),r.mergeable(),r.maxQueueAgeMinutes(),a.id());
   db.audit(a,cmd,"CONTRACT",id,"CREATE",null,"DRAFT",1,m("operation",r.operationType()));
   return new Reply(201,contract(id));
  });
 }
 Map<String,Object> contract(UUID id){return db.one("SELECT id,operation_type,record_type,owner_domain,offline_allowed,requires_safety_gate,mergeable,max_queue_age_minutes,status,created_by,approved_by,version FROM synqoria_offline.contracts WHERE id=?",id);}
 public Reply decideContract(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.CONTRACT_APPROVE);req(d);
  return db.command(a,cmd,"CONTRACT_DECIDE|"+id,d,()->{
   Map<String,Object> c=db.rows("SELECT * FROM synqoria_offline.contracts WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(((Number)c.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(c.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(c.get("createdBy")))throw new Violation("approver_must_be_independent");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_offline.contracts SET status=?,approved_by=?,version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"CONTRACT",id,d.approve()?"APPROVE":"REJECT","DRAFT",to,d.expectedVersion()+1,m());
   return new Reply(200,contract(id));
  });
 }
 public List<Map<String,Object>> contracts(Actor a){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->db.rows("SELECT id,operation_type,record_type,owner_domain,offline_allowed,requires_safety_gate,mergeable,max_queue_age_minutes,status,created_by,approved_by,version FROM synqoria_offline.contracts ORDER BY operation_type"));
 }

 // ------------------------------------------------------------------ devices (B02, B08)
 public Reply registerDevice(Actor a,UUID cmd,DeviceRequest r){
  a.require(Perm.DEVICE_MANAGE);req(r);
  if(r.deviceKey()==null||!r.deviceKey().matches("[A-Za-z0-9][A-Za-z0-9_.-]{5,63}")||r.label()==null||r.label().isBlank()||r.boundUser()==null||
     r.sessionMinutes()<5||r.sessionMinutes()>1440||r.queueLimit()<1||r.queueLimit()>5000)throw new Violation("invalid_device");
  return db.command(a,cmd,"DEVICE_REGISTER",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_offline.devices(id,cell_id,facility_id,device_key,label,bound_user,status,session_expires_at,queue_limit,registered_by,version) VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,1)",
    id,a.cell(),a.facility(),r.deviceKey(),r.label().trim(),r.boundUser(),ts(db.clock.instant().plus(Duration.ofMinutes(r.sessionMinutes()))),r.queueLimit(),a.id());
   db.audit(a,cmd,"DEVICE",id,"REGISTER",null,"ACTIVE",1,m());
   return new Reply(201,device(id));
  });
 }
 Map<String,Object> device(UUID id){return db.one(DEV+" WHERE d.id=?",id);}
 static final String DEV="SELECT d.id,d.device_key,d.label,d.bound_user,d.status,d.session_expires_at,d.queue_limit,d.last_seq,d.last_seen_at,d.revoke_reason,d.version,"+
  "(SELECT count(*) FROM synqoria_offline.commands c WHERE c.device_id=d.id AND c.status='CONFLICT') AS open_conflicts,"+
  "(SELECT count(*) FROM synqoria_offline.commands c WHERE c.device_id=d.id) AS received FROM synqoria_offline.devices d";
 public Reply revokeDevice(Actor a,UUID cmd,UUID id,Revoke r){
  a.require(Perm.DEVICE_MANAGE);req(r);
  if(r.reason()==null||r.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"DEVICE_REVOKE|"+id,r,()->{
   Map<String,Object> d=db.rows("SELECT status,version FROM synqoria_offline.devices WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(((Number)d.get("version")).longValue()!=r.expectedVersion())throw new Violation("stale_version");
   if("REVOKED".equals(d.get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_offline.devices SET status='REVOKED',revoked_by=?,revoke_reason=?,version=version+1 WHERE id=?",a.id(),r.reason().trim(),id);
   db.audit(a,cmd,"DEVICE",id,"REVOKE","ACTIVE","REVOKED",r.expectedVersion()+1,m());
   return new Reply(200,device(id));
  });
 }
 public List<Map<String,Object>> devices(Actor a){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->db.rows(DEV+" ORDER BY d.created_at DESC, d.id"));
 }

 // ------------------------------------------------------------------ authoritative heads (B04) - published by the OWNING domain
 public Reply registerHead(Actor a,UUID cmd,HeadRequest r){
  a.require(Perm.HEAD_PUBLISH);req(r);
  if(!Domain.validKey(r.recordType())||r.recordKey()==null||r.recordKey().isBlank()||r.recordKey().length()>120||r.ownerRef()==null||r.headHash()==null||!HASH.matcher(r.headHash()).matches())
   throw new Violation("invalid_head");
  return db.command(a,cmd,"HEAD_REGISTER",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_offline.record_heads(id,cell_id,facility_id,record_type,record_key,owner_ref,ownership_epoch,head_version,head_hash,version) VALUES (?,?,?,?,?,?,1,1,?,1)",
    id,a.cell(),a.facility(),r.recordType(),r.recordKey(),r.ownerRef(),r.headHash());
   db.jdbc.update("INSERT INTO synqoria_offline.record_revisions(id,cell_id,facility_id,head_id,head_version,head_hash,changed_fields,source) VALUES (?,?,?,?,1,?,'{}','DOMAIN')",UUID.randomUUID(),a.cell(),a.facility(),id,r.headHash());
   db.audit(a,cmd,"HEAD",id,"REGISTER",null,"ACTIVE",1,m("type",r.recordType()));
   return new Reply(201,head(id));
  });
 }
 public Reply publishRevision(Actor a,UUID cmd,UUID id,RevisionRequest r){
  a.require(Perm.HEAD_PUBLISH);req(r);
  if(r.headHash()==null||!HASH.matcher(r.headHash()).matches()||r.changedFields()==null)throw new Violation("invalid_head");
  String fields=lit(r.changedFields());
  return db.command(a,cmd,"HEAD_REVISION|"+id,r,()->{
   Map<String,Object> h=lockHead(id);
   if(((Number)h.get("version")).longValue()!=r.expectedVersion())throw new Violation("stale_version");
   if(Boolean.TRUE.equals(h.get("frozen")))throw new Violation("record_frozen");
   long next=((Number)h.get("headVersion")).longValue()+1;
   db.jdbc.update("UPDATE synqoria_offline.record_heads SET head_version=?,head_hash=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",next,r.headHash(),id);
   db.jdbc.update("INSERT INTO synqoria_offline.record_revisions(id,cell_id,facility_id,head_id,head_version,head_hash,changed_fields,source) VALUES (?,?,?,?,?,?,?::text[],'DOMAIN')",UUID.randomUUID(),a.cell(),a.facility(),id,next,r.headHash(),fields);
   db.audit(a,cmd,"HEAD",id,"REVISION","ACTIVE","ACTIVE",r.expectedVersion()+1,m("headVersion",next));
   return new Reply(200,head(id));
  });
 }
 Map<String,Object> lockHead(UUID id){return db.rows("SELECT * FROM synqoria_offline.record_heads WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));}
 Map<String,Object> head(UUID id){return db.one("SELECT id,record_type,record_key,owner_ref,ownership_epoch,head_version,head_hash,frozen,version,updated_at FROM synqoria_offline.record_heads WHERE id=?",id);}
 public Map<String,Object> heads(Actor a,int page,int size){
  a.require(Perm.SYNC_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.record_heads",Long.class);
   return db.pageOf(db.rows("SELECT id,record_type,record_key,owner_ref,ownership_epoch,head_version,head_hash,frozen,version,updated_at FROM synqoria_offline.record_heads ORDER BY record_type,record_key LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }

 // ------------------------------------------------------------------ the sync protocol (B03, B07)
 /** Bounded, authenticated, idempotent batch. Per-record outcomes; a retryable outcome stops the batch so order is preserved. */
 public Reply submitBatch(Actor a,UUID batchKey,BatchRequest b){
  a.require(Perm.SYNC_SUBMIT);req(b);
  if(b.deviceId()==null||b.commands()==null||b.commands().isEmpty()||b.commands().size()>MAX_BATCH)throw new Violation("invalid_batch");
  return db.command(a,batchKey,"SYNC_BATCH|"+b.deviceId(),b,()->{
   Instant now=db.clock.instant();
   Map<String,Object> dev=db.rows("SELECT * FROM synqoria_offline.devices WHERE id=? FOR UPDATE",b.deviceId()).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!a.id().equals(dev.get("boundUser")))throw new Violation("device_user_mismatch");
   if("REVOKED".equals(dev.get("status")))throw new Violation("device_revoked");
   if(!Instant.parse((String)dev.get("sessionExpiresAt")).isAfter(now))throw new Violation("device_session_expired");
   long limit=((Number)dev.get("queueLimit")).longValue();
   long cursor=((Number)dev.get("lastSeq")).longValue();
   List<CommandIn> sorted=new ArrayList<>(b.commands());
   sorted.sort(Comparator.comparingLong(CommandIn::seq));
   List<Map<String,Object>> results=new ArrayList<>();
   boolean stopped=false;
   for(CommandIn c:sorted){
    if(stopped){results.add(res(c,"NOT_PROCESSED","batch_stopped",null,null));continue;}
    Map<String,Object> r=one(a,batchKey,b.deviceId(),c,now,limit);
    results.add(r);
    if("RETRY".equals(r.get("status")))stopped=true;
    else if(!"INVALID".equals(r.get("status"))&&!"REJECTED_UNSTORED".equals(r.get("status")))cursor=Math.max(cursor,c.seq());
   }
   db.jdbc.update("UPDATE synqoria_offline.devices SET last_seq=?,last_seen_at=clock_timestamp(),version=version+1 WHERE id=?",cursor,b.deviceId());
   return new Reply(200,m("deviceId",b.deviceId().toString(),"results",results,"cursor",cursor,"serverTime",now.toString()));
  });
 }
 Map<String,Object> res(CommandIn c,String status,String code,Long applied,UUID conflict){
  return m("commandId",c==null||c.commandId()==null?null:c.commandId().toString(),"seq",c==null?null:c.seq(),"status",status,"code",code,"appliedVersion",applied,"conflictId",conflict==null?null:conflict.toString());
 }
 private Map<String,Object> one(Actor a,UUID batchKey,UUID deviceId,CommandIn c,Instant now,long queueLimit){
  if(c==null||c.commandId()==null||c.seq()<1||!Domain.validOp(c.operationType())||!Domain.validKey(c.recordType())||c.recordKey()==null||c.recordKey().isBlank()||c.recordKey().length()>120||
     c.baseVersion()<1||c.baseEpoch()<1||c.fields()==null||c.fields().size()>40||c.payload()==null||c.createdLocalAt()==null||c.expiresAt()==null||!c.expiresAt().isAfter(c.createdLocalAt()))
   return res(c,"INVALID","invalid_command",null,null);
  for(String f:c.fields())if(!FIELD.matcher(f).matches())return res(c,"INVALID","invalid_field",null,null);
  String payloadJson=db.encode(new TreeMap<>(c.payload()));
  if(payloadJson.length()>16000)return res(c,"INVALID","payload_too_large",null,null);
  String hash=Domain.sha256(c.operationType()+"|"+c.recordType()+"|"+c.recordKey()+"|"+c.baseVersion()+"|"+c.baseEpoch()+"|"+payloadJson);
  // duplicate / replay protection
  List<Map<String,Object>> known=db.rows("SELECT id,payload_hash,status,outcome_code,applied_version FROM synqoria_offline.commands WHERE device_id=? AND command_id=?",deviceId,c.commandId());
  if(!known.isEmpty()){
   Map<String,Object> k=known.getFirst();
   if(!hash.equals(String.valueOf(k.get("payloadHash")).trim()))return res(c,"REJECTED_UNSTORED","replay_mismatch",null,null);
   Map<String,Object> out=res(c,"DUPLICATE",(String)k.get("outcomeCode"),k.get("appliedVersion")==null?null:((Number)k.get("appliedVersion")).longValue(),null);
   out.put("originalStatus",k.get("status"));
   return out;
  }
  if(!db.rows("SELECT 1 AS x FROM synqoria_offline.commands WHERE device_id=? AND device_seq=?",deviceId,c.seq()).isEmpty())return res(c,"REJECTED_UNSTORED","sequence_reuse",null,null);
  long open=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.commands WHERE device_id=? AND status='CONFLICT'",Long.class,deviceId);
  if(open>=queueLimit)return res(c,"RETRY","queue_full",null,null);

  List<Map<String,Object>> ct=db.rows("SELECT status,offline_allowed,requires_safety_gate,mergeable,max_queue_age_minutes FROM synqoria_offline.contracts WHERE operation_type=?",c.operationType());
  Contract contract=ct.isEmpty()?null:new Contract("APPROVED".equals(ct.getFirst().get("status")),(Boolean)ct.getFirst().get("offlineAllowed"),(Boolean)ct.getFirst().get("requiresSafetyGate"),
   (Boolean)ct.getFirst().get("mergeable"),((Number)ct.getFirst().get("maxQueueAgeMinutes")).intValue());
  List<Map<String,Object>> hd=db.rows("SELECT * FROM synqoria_offline.record_heads WHERE record_type=? AND record_key=? FOR UPDATE",c.recordType(),c.recordKey());
  Head head=hd.isEmpty()?null:new Head(((Number)hd.getFirst().get("ownershipEpoch")).longValue(),((Number)hd.getFirst().get("headVersion")).longValue(),(Boolean)hd.getFirst().get("frozen"));
  Set<String> changed=new TreeSet<>();
  if(head!=null)for(Map<String,Object> rv:db.rows("SELECT changed_fields FROM synqoria_offline.record_revisions WHERE head_id=? AND head_version>?",hd.getFirst().get("id"),c.baseVersion()))
   changed.addAll(castList(rv.get("changedFields")));
  Verdict v=Domain.evaluate(new Cmd(c.operationType(),c.baseVersion(),c.baseEpoch(),new TreeSet<>(c.fields()),c.createdLocalAt(),c.expiresAt()),contract,head,changed,now);
  if(v.kind()==Kind.RETRY)return res(c,"RETRY",v.code(),null,null);

  UUID rowId=UUID.randomUUID();
  String status=switch(v.kind()){case APPLY,MERGE->"APPLIED";case CONFLICT->"CONFLICT";case EXPIRED->"EXPIRED";default->"REJECTED";};
  Long applied=null;UUID headId=hd.isEmpty()?null:(UUID)hd.getFirst().get("id");
  if(v.kind()==Kind.APPLY||v.kind()==Kind.MERGE)applied=head.version()+1;
  db.jdbc.update("INSERT INTO synqoria_offline.commands(id,cell_id,facility_id,device_id,command_id,device_seq,operation_type,record_type,record_key,base_version,base_epoch,fields,payload,payload_hash,created_local_at,expires_at,author_id,status,outcome_code,applied_version,version) "+
   "VALUES (?,?,?,?,?,?,?,?,?,?,?,?::text[],?::jsonb,?,?,?,?,?,?,?,1)",rowId,a.cell(),a.facility(),deviceId,c.commandId(),c.seq(),c.operationType(),c.recordType(),c.recordKey(),c.baseVersion(),c.baseEpoch(),
   lit(c.fields()),payloadJson,hash,ts(c.createdLocalAt()),ts(c.expiresAt()),a.id(),status,v.code(),applied);
  UUID conflictId=null;
  if(applied!=null){
   String nh=Domain.nextHash(String.valueOf(hd.getFirst().get("headHash")).trim(),hash);
   db.jdbc.update("UPDATE synqoria_offline.record_heads SET head_version=?,head_hash=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",applied,nh,headId);
   db.jdbc.update("INSERT INTO synqoria_offline.record_revisions(id,cell_id,facility_id,head_id,head_version,head_hash,changed_fields,source) VALUES (?,?,?,?,?,?,?::text[],'OFFLINE')",UUID.randomUUID(),a.cell(),a.facility(),headId,applied,nh,lit(c.fields()));
   recon(a,rowId,null,headId,v.kind()==Kind.MERGE?"APPLIED_MERGED":"APPLIED",null,m("code",v.code(),"commandId",c.commandId().toString()),applied,head.epoch());
  }else if(v.kind()==Kind.CONFLICT){
   conflictId=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_offline.conflicts(id,cell_id,facility_id,command_row,head_id,kind,local_base_version,local_payload_hash,authoritative_version,authoritative_hash,authoritative_epoch,differing_fields,status,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?::text[],'OPEN',1)",conflictId,a.cell(),a.facility(),rowId,headId,v.conflictKind(),c.baseVersion(),hash,head.version(),String.valueOf(hd.getFirst().get("headHash")).trim(),head.epoch(),lit(v.differing()));
   db.audit(a,batchKey,"CONFLICT",conflictId,"DETECT",null,"OPEN",1,m("kind",v.conflictKind()));
  }else{
   recon(a,rowId,null,headId==null?noHead(a,c):headId,v.kind()==Kind.EXPIRED?"EXPIRED":"REJECTED",null,m("code",v.code(),"commandId",c.commandId().toString()),head==null?0:head.version(),head==null?0:head.epoch());
  }
  db.audit(a,batchKey,"COMMAND",rowId,v.kind()==Kind.CONFLICT?"CONFLICT":status,null,status,1,m("code",v.code()));
  return res(c,status,v.code(),applied,conflictId);
 }
 /** A command for an unknown record still needs a history subject: a sentinel head is NOT invented; history stores it against a deterministic placeholder id. */
 private UUID noHead(Actor a,CommandIn c){return UUID.nameUUIDFromBytes(("unknown|"+c.recordType()+"|"+c.recordKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @SuppressWarnings("unchecked") static List<String> castList(Object o){return o==null?List.of():(List<String>)o;}

 void recon(Actor a,UUID cmdRow,UUID transfer,UUID headId,String outcome,UUID reviewer,Map<String,Object> evidence,long finalVersion,long finalEpoch){
  db.jdbc.update("INSERT INTO synqoria_offline.reconciliations(id,cell_id,facility_id,command_row,transfer_id,head_id,outcome,reviewer,evidence,final_version,final_epoch) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?)",
   UUID.randomUUID(),a.cell(),a.facility(),cmdRow,transfer,headId,outcome,reviewer,db.encode(new TreeMap<>(evidence)),finalVersion,finalEpoch);
 }

 public Map<String,Object> cursor(Actor a,UUID deviceId){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->{Map<String,Object> d=db.one(DEV+" WHERE d.id=?",deviceId);return m("deviceId",deviceId.toString(),"cursor",d.get("lastSeq"),"status",d.get("status"));});
 }

 // ------------------------------------------------------------------ queue views (S02)
 static final String CMD="SELECT c.id,c.device_id,d.label AS device_label,c.command_id AS client_command_id,c.device_seq,c.operation_type,c.record_type,c.record_key,c.base_version,c.base_epoch,c.fields,c.payload_hash,"+
  "c.created_local_at,c.expires_at,c.received_at,c.author_id,c.status,c.outcome_code,c.applied_version,c.version FROM synqoria_offline.commands c JOIN synqoria_offline.devices d ON d.id=c.device_id";
 public Map<String,Object> commands(Actor a,String status,UUID deviceId,int page,int size){
  a.require(Perm.SYNC_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null&&!Set.of("APPLIED","CONFLICT","REJECTED","EXPIRED","RESOLVED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(status!=null){w.append(" AND c.status=?");args.add(status);}
   if(deviceId!=null){w.append(" AND c.device_id=?");args.add(deviceId);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.commands c"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows(CMD+w+" ORDER BY c.received_at DESC,c.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public Map<String,Object> command(Actor a,UUID id){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->{
   Map<String,Object> r=new LinkedHashMap<>(db.one(CMD+" WHERE c.id=?",id));
   r.put("payload",db.one("SELECT payload FROM synqoria_offline.commands WHERE id=?",id).get("payload"));
   return r;
  });
 }
 public Map<String,Object> status(Actor a){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->{
   Instant now=db.clock.instant();
   List<Map<String,Object>> devs=new ArrayList<>();
   for(Map<String,Object> d:db.rows(DEV+" ORDER BY d.created_at DESC")){
    Map<String,Object> x=new LinkedHashMap<>(d);
    Object seen=d.get("lastSeenAt");
    x.put("stale",seen==null||Instant.parse((String)seen).plus(Duration.ofMinutes(60)).isBefore(now));
    x.put("sessionExpired",!Instant.parse((String)d.get("sessionExpiresAt")).isAfter(now));
    devs.add(x);
   }
   return m("devices",devs,"openConflicts",db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.conflicts WHERE status='OPEN'",Long.class),
    "frozenRecords",db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.record_heads WHERE frozen",Long.class),
    "openTransfers",db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.ownership_transfers WHERE status IN ('PROPOSED','FROZEN','FENCED','APPROVED')",Long.class),
    "serverTime",now.toString());
  });
 }

 // ------------------------------------------------------------------ conflicts (B05, B06)
 static final String CONF="SELECT x.id,x.kind,x.status,x.local_base_version,x.local_payload_hash,x.authoritative_version,x.authoritative_hash,x.authoritative_epoch,x.differing_fields,x.resolution,x.reason_code,x.reason_note,"+
  "x.resolved_by,x.resolved_at,x.resulting_version,x.version,x.created_at,c.operation_type,c.record_type,c.record_key,c.author_id,c.command_id AS client_command_id,h.id AS head_id,h.head_version AS current_head_version,h.ownership_epoch AS current_epoch,h.owner_ref "+
  "FROM synqoria_offline.conflicts x JOIN synqoria_offline.commands c ON c.id=x.command_row JOIN synqoria_offline.record_heads h ON h.id=x.head_id";
 public Map<String,Object> conflicts(Actor a,String status,int page,int size){
  a.require(Perm.SYNC_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null&&!Set.of("OPEN","RESOLVED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE x.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.conflicts x"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows(CONF+w+" ORDER BY x.created_at DESC,x.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
 public Map<String,Object> conflict(Actor a,UUID id){
  a.require(Perm.SYNC_READ);
  return db.scoped(a,()->{
   Map<String,Object> r=new LinkedHashMap<>(db.one(CONF+" WHERE x.id=?",id));
   r.put("localPayload",db.one("SELECT c.payload FROM synqoria_offline.commands c JOIN synqoria_offline.conflicts x ON x.command_row=c.id WHERE x.id=?",id).get("payload"));
   return r;
  });
 }
 /** Reviewed, authorized, reasoned and immutable. The local source version is retained either way. */
 public Reply resolve(Actor a,UUID cmd,UUID id,Resolution r){
  a.require(Perm.CONFLICT_RESOLVE);req(r);
  if(!Set.of("ACCEPT_LOCAL","KEEP_AUTHORITATIVE").contains(r.resolution()))throw new Violation("invalid_resolution");
  if(r.reasonCode()==null||!REASONS.contains(r.reasonCode()))throw new Violation("reason_required");
  if("OTHER".equals(r.reasonCode())&&(r.note()==null||r.note().trim().length()<5))throw new Violation("reason_required");
  if(r.note()!=null&&r.note().length()>500)throw new Violation("invalid_resolution");
  return db.command(a,cmd,"CONFLICT_RESOLVE|"+id,r,()->{
   Map<String,Object> x=db.rows("SELECT x.*,c.author_id,c.payload_hash,c.fields FROM synqoria_offline.conflicts x JOIN synqoria_offline.commands c ON c.id=x.command_row WHERE x.id=? FOR UPDATE OF x",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(((Number)x.get("version")).longValue()!=r.expectedVersion())throw new Violation("stale_version");
   if(!"OPEN".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("authorId")))throw new Violation("resolver_must_be_independent");
   UUID headId=(UUID)x.get("headId");
   Map<String,Object> h=lockHead(headId);
   long headV=((Number)h.get("headVersion")).longValue(),epoch=((Number)h.get("ownershipEpoch")).longValue();
   UUID cmdRow=(UUID)x.get("commandRow");
   Long result=null;String outcome;
   if("ACCEPT_LOCAL".equals(r.resolution())){
    if(Boolean.TRUE.equals(h.get("frozen")))throw new Violation("record_frozen");
    if(epoch!=((Number)x.get("authoritativeEpoch")).longValue())throw new Violation("epoch_changed");   // ownership moved: re-detect, never apply across epochs
    if(headV!=r.expectedHeadVersion())throw new Violation("head_moved");                                   // the reviewer must have seen the current authoritative version
    result=headV+1;
    String nh=Domain.nextHash(String.valueOf(h.get("headHash")).trim(),String.valueOf(x.get("payloadHash")).trim());
    db.jdbc.update("UPDATE synqoria_offline.record_heads SET head_version=?,head_hash=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",result,nh,headId);
    db.jdbc.update("INSERT INTO synqoria_offline.record_revisions(id,cell_id,facility_id,head_id,head_version,head_hash,changed_fields,source) VALUES (?,?,?,?,?,?,?::text[],'RESOLUTION')",
     UUID.randomUUID(),a.cell(),a.facility(),headId,result,nh,lit(castList(x.get("fields"))));
    outcome="RESOLVED_LOCAL";
   }else outcome="RESOLVED_AUTHORITATIVE";
   db.jdbc.update("UPDATE synqoria_offline.conflicts SET status='RESOLVED',resolution=?,reason_code=?,reason_note=?,resolved_by=?,resolved_at=clock_timestamp(),resulting_version=?,version=version+1 WHERE id=?",
    r.resolution(),r.reasonCode(),r.note()==null?null:r.note().trim(),a.id(),result,id);
   db.jdbc.update("UPDATE synqoria_offline.commands SET status='RESOLVED',outcome_code=?,applied_version=?,version=version+1 WHERE id=?",outcome.toLowerCase(),result,cmdRow);
   recon(a,cmdRow,null,headId,outcome,a.id(),m("reason",r.reasonCode(),"conflictId",id.toString()),result==null?headV:result,epoch);
   db.audit(a,cmd,"CONFLICT",id,"RESOLVE","OPEN","RESOLVED",r.expectedVersion()+1,m("resolution",r.resolution(),"reason",r.reasonCode()));
   return new Reply(200,conflict(a,id,true));
  });
 }
 Map<String,Object> conflict(Actor a,UUID id,boolean inTx){return db.one(CONF+" WHERE x.id=?",id);}

 // ------------------------------------------------------------------ history (S05)
 public Map<String,Object> history(Actor a,String outcome,int page,int size){
  a.require(Perm.HISTORY_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=outcome==null?"":" WHERE r.outcome=?";Object[] base=outcome==null?new Object[0]:new Object[]{outcome};
   String f=" FROM synqoria_offline.reconciliations r LEFT JOIN synqoria_offline.record_heads h ON h.id=r.head_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT r.id,r.command_row,r.transfer_id,r.outcome,r.reviewer,r.evidence,r.final_version,r.final_epoch,r.occurred_at,h.record_type,h.record_key"+f+w+" ORDER BY r.occurred_at DESC,r.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
}
