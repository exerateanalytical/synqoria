package org.synqoria.integrationmanagement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.synqoria.integrationmanagement.IntegrationApplication.*;

@Component
public class IntegrationStore{
 public record Reply(int status,Object body){}
 public record Partner(String partnerCode,String partnerType){}
 public record ApiProduct(String apiCode,String apiVersion,String contractSha256){}
 public record DeveloperApp(UUID partnerId,UUID apiProductId,String appCode){}
 public record Mapping(String mapCode,String mapVersion,String contractSha256,
                       String sourceSystem,String targetSystem){}
 public record Ingest(UUID partnerId,UUID externalEventId,String eventType,
                      String payloadSha256,UUID sourceProofId){}
 public record Replay(UUID messageId,String reasonCode){}
 public record Version(long expectedVersion,UUID proofId){}
 public record Decision(long expectedVersion,boolean approve){}
 private final JdbcTemplate jdbc;private final JsonMapper json;private final TransactionTemplate tx;
 private final String role;
 public IntegrationStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,
   @Value("${synqoria.integrations.role}")String role){
  if(!"synqoria_integration_runtime".equals(role))throw new IllegalStateException("m35_restricted_role_required");
  this.jdbc=jdbc;this.json=json;this.role=role;this.tx=new TransactionTemplate(manager);
 }
 public static String sha(String x){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
   .digest(x.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String code(String value){
  if(value==null||!value.matches("[A-Z][A-Z0-9_]{1,63}"))throw new Problem("invalid_code");
  return value;
 }
 public static String hex(String value){
  if(value==null||!value.matches("[0-9a-f]{64}"))throw new Problem("invalid_sha256");
  return value;
 }
 private String encode(Object o){return json.writeValueAsString(o);}
 private Object decode(String t){return json.readValue(t,Object.class);}
 public <T>T scoped(Actor a,Supplier<T> work){
  return Objects.requireNonNull(tx.execute(t->{
   String current=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean safe=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls "+
    "AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication "+
    "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!role.equals(current)||!Boolean.TRUE.equals(safe))throw new Problem("db_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,a.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,a.facility().toString());
   return work.get();
  }));
 }
 private Reply command(Actor a,UUID key,String op,Object request,Supplier<Reply> work){
  if(key==null)throw new Problem("idempotency_key_required");
  String digest=sha(op+"|"+encode(request));
  return scoped(a,()->{
   jdbc.update("INSERT INTO synqoria_integration.commands(cell_id,facility_id,actor_id,command_id,"+
    "request_hash) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
    a.cell(),a.facility(),a.id(),key,digest);
   var row=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status "+
    "FROM synqoria_integration.commands WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    a.cell(),a.facility(),a.id(),key);
   if(!digest.equals(row.get("request_hash")))throw new Problem("idempotency_collision");
   if(row.get("http_status")!=null)return new Reply(((Number)row.get("http_status")).intValue(),
    decode((String)row.get("response")));
   Reply out=work.get();
   jdbc.update("UPDATE synqoria_integration.commands SET response=?::jsonb,http_status=? "+
    "WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=?",
    encode(out.body()),out.status(),a.cell(),a.facility(),a.id(),key);
   return out;
  });
 }
 private Map<String,Object> row(Actor a,String table,UUID id){
  if(!Set.of("partners","api_products","developer_apps","mapping_versions","messages","replay_requests")
   .contains(table))throw new Problem("invalid_collection");
  try{return jdbc.queryForMap("SELECT * FROM synqoria_integration."+table+
   " WHERE cell_id=? AND facility_id=? AND id=? FOR UPDATE",a.cell(),a.facility(),id);
  }catch(EmptyResultDataAccessException e){throw new Problem("not_found");}
 }
 private void version(Map<String,Object> row,long expected){
  if(expected<1||((Number)row.get("version")).longValue()!=expected)
   throw new Problem("stale_version");
 }
 private Reply reply(int status,UUID id,String state,long version){
  return new Reply(status,Map.of("id",id.toString(),"status",state,"version",version));
 }
 private void audit(Actor a,UUID key,String type,UUID id,String op,long version){
  UUID event=UUID.randomUUID();
  jdbc.update("INSERT INTO synqoria_integration.audit_events(id,cell_id,facility_id,type,"+
   "aggregate_id,action,version,actor_id,command_id) VALUES(?,?,?,?,?,?,?,?,?)",
   event,a.cell(),a.facility(),type,id,op,version,a.id(),key);
  jdbc.update("INSERT INTO synqoria_integration.outbox(id,cell_id,facility_id,aggregate_id,event_type,"+
   "payload) VALUES(?,?,?,?,?,?::jsonb)",event,a.cell(),a.facility(),id,"m35."+type+"."+op,
   encode(Map.of("id",id.toString(),"version",version,"action",op)));
 }
 private void proof(Actor a,UUID id,String kind,String code,UUID event,String payload){
  if(id==null)throw new Problem("proof_unavailable");
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,35))",id.toString());
  var matches=jdbc.queryForList("SELECT proof_id FROM synqoria_integration.partner_proofs "+
   "WHERE cell_id=? AND facility_id=? AND proof_id=? AND proof_type=? AND partner_code=? "+
   "AND source_event_id IS NOT DISTINCT FROM ?::uuid "+
   "AND source_payload_sha256 IS NOT DISTINCT FROM ?::char(64) "+
   "AND external_actor<>? AND revoked_at IS NULL "+
   "AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp()",
   a.cell(),a.facility(),id,kind,code,event,payload,a.id());
  if(matches.size()!=1)throw new Problem("proof_unavailable");
 }
 public List<Map<String,Object>> list(Actor a,String table,int limit){
  a.require("m35:read");
  if(!Set.of("partners","api_products","developer_apps","mapping_versions","messages","replay_requests")
    .contains(table)||limit<1||limit>100)throw new Problem("invalid_collection");
  return scoped(a,()->jdbc.queryForList("SELECT * FROM synqoria_integration."+table+
   " WHERE cell_id=? AND facility_id=? ORDER BY created_at DESC,id LIMIT ?",
    a.cell(),a.facility(),limit));
 }
 public Reply partner(Actor a,UUID key,Partner req){
  a.require("m35:partner_create");
  if(req==null)throw new Problem("invalid_partner");
  return command(a,key,"CREATE_PARTNER",req,()->{
   String code=code(req.partnerCode()),type=code(req.partnerType());
   if(!Set.of("HOSPITAL","LABORATORY","PAYER","GOVERNMENT","VENDOR").contains(type))
    throw new Problem("invalid_partner_type");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.partners(id,cell_id,facility_id,partner_code,"+
    "partner_type,created_by) VALUES(?,?,?,?,?,?)",id,a.cell(),a.facility(),code,type,a.id());
   audit(a,key,"partner",id,"CREATE",1);
   return reply(201,id,"PENDING",1);
  });
 }
 public Reply partnerAction(Actor a,UUID key,UUID id,String op,Version req){
  String grant=switch(op){
   case "verify"->"m35:partner_verify";case "enable"->"m35:partner_enable";
   case "suspend","retire"->"m35:partner_suspend";
   default->throw new Problem("invalid_action");
  };
  a.require(grant);
  if(req==null)throw new Problem("invalid_version");
  return command(a,key,"PARTNER_"+op,List.of(id,req),()->{
   var p=row(a,"partners",id);version(p,req.expectedVersion());
   String before=(String)p.get("status"),next=switch(op){
    case "verify"->"VERIFIED";case "enable"->"ENABLED";
    case "suspend"->"SUSPENDED";case "retire"->"RETIRED";
    default->throw new Problem("invalid_action");
   };
   if(!((before.equals("PENDING")&&next.equals("VERIFIED"))||
    (before.equals("VERIFIED")&&next.equals("ENABLED"))||
    (before.equals("ENABLED")&&next.equals("SUSPENDED"))||
    (before.equals("SUSPENDED")&&next.equals("RETIRED"))))
    throw new Problem("invalid_transition");
   if("VERIFIED".equals(next)){
    if(a.id().equals(p.get("created_by")))throw new Problem("independent_checker_required");
    proof(a,req.proofId(),"PARTNER_KYB",(String)p.get("partner_code"),null,null);
   }
   if("ENABLED".equals(next)){
    if(a.id().equals(p.get("created_by"))||a.id().equals(p.get("verified_by")))
     throw new Problem("independent_checker_required");
    proof(a,(UUID)p.get("proof_id"),"PARTNER_KYB",(String)p.get("partner_code"),null,null);
   }
   String actorField=switch(next){
    case "VERIFIED"->"verified_by";case "ENABLED"->"enabled_by";
    case "SUSPENDED"->"suspended_by";default->"retired_by";
   };
   jdbc.update("UPDATE synqoria_integration.partners SET status=?,version=version+1,"+
    actorField+"=?,"+("VERIFIED".equals(next)?"proof_id=?,":"")+"updated_at=clock_timestamp() "+
    "WHERE cell_id=? AND facility_id=? AND id=?",
    "VERIFIED".equals(next)?
     new Object[]{next,a.id(),req.proofId(),a.cell(),a.facility(),id}:
     new Object[]{next,a.id(),a.cell(),a.facility(),id});
   audit(a,key,"partner",id,next,req.expectedVersion()+1);
   return reply(200,id,next,req.expectedVersion()+1);
  });
 }
 public Reply product(Actor a,UUID key,ApiProduct req){
  a.require("m35:api_create");
  if(req==null)throw new Problem("invalid_api");
  return command(a,key,"CREATE_PRODUCT",req,()->{
   code(req.apiCode());hex(req.contractSha256());
   if(req.apiVersion()==null||!req.apiVersion().matches("[A-Z0-9][A-Z0-9_.-]{0,63}"))
    throw new Problem("invalid_api_version");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.api_products(id,cell_id,facility_id,api_code,"+
    "api_version,contract_sha256,created_by) VALUES(?,?,?,?,?,?,?)",
    id,a.cell(),a.facility(),req.apiCode(),req.apiVersion(),req.contractSha256(),a.id());
   audit(a,key,"api_product",id,"CREATE",1);
   return reply(201,id,"DRAFT",1);
  });
 }
 public Reply publish(Actor a,UUID key,UUID id,Version req){
  a.require("m35:api_publish");
  if(req==null)throw new Problem("invalid_version");
  return command(a,key,"PUBLISH_API",List.of(id,req),()->{
   var item=row(a,"api_products",id);version(item,req.expectedVersion());
   if(!"DRAFT".equals(item.get("status")))throw new Problem("invalid_transition");
   if(a.id().equals(item.get("created_by")))throw new Problem("independent_checker_required");
   jdbc.update("UPDATE synqoria_integration.api_products SET status='PUBLISHED',version=version+1,"+
    "published_by=?,updated_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND id=?",
    a.id(),a.cell(),a.facility(),id);
   audit(a,key,"api_product",id,"PUBLISH",req.expectedVersion()+1);
   return reply(200,id,"PUBLISHED",req.expectedVersion()+1);
  });
 }
 public Reply application(Actor a,UUID key,DeveloperApp req){
  a.require("m35:app_request");
  if(req==null||req.partnerId()==null||req.apiProductId()==null)throw new Problem("invalid_app");
  return command(a,key,"REQUEST_APP",req,()->{
   code(req.appCode());
   var partner=row(a,"partners",req.partnerId());
   if(!"ENABLED".equals(partner.get("status")))throw new Problem("partner_not_enabled");
   var product=row(a,"api_products",req.apiProductId());
   if(!"PUBLISHED".equals(product.get("status")))throw new Problem("api_not_published");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.developer_apps(id,cell_id,facility_id,"+
    "partner_id,api_product_id,app_code,requested_by) VALUES(?,?,?,?,?,?,?)",
    id,a.cell(),a.facility(),req.partnerId(),req.apiProductId(),req.appCode(),a.id());
   audit(a,key,"developer_app",id,"REQUEST",1);
   return reply(201,id,"REQUESTED",1);
  });
 }
 public Reply appAction(Actor a,UUID key,UUID id,String op,Version req){
  a.require("m35:app_review");
  if(req==null)throw new Problem("invalid_version");
  return command(a,key,"APP_"+op,List.of(id,req),()->{
   var app=row(a,"developer_apps",id);version(app,req.expectedVersion());
   String before=(String)app.get("status"),next=switch(op){
    case "approve"->"APPROVED";case "suspend"->"SUSPENDED";
    default->throw new Problem("invalid_action");
   };
   if(!((before.equals("REQUESTED")&&next.equals("APPROVED"))||
    (before.equals("APPROVED")&&next.equals("SUSPENDED"))))throw new Problem("invalid_transition");
   if(a.id().equals(app.get("requested_by")))throw new Problem("independent_checker_required");
   if(next.equals("APPROVED")){
    if(!"ENABLED".equals(row(a,"partners",(UUID)app.get("partner_id")).get("status")))
     throw new Problem("partner_not_enabled");
    if(!"PUBLISHED".equals(row(a,"api_products",(UUID)app.get("api_product_id")).get("status")))
     throw new Problem("api_not_published");
   }
   jdbc.update("UPDATE synqoria_integration.developer_apps SET status=?,version=version+1,"+
    "reviewed_by=?,reviewed_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND id=?",
    next,a.id(),a.cell(),a.facility(),id);
   audit(a,key,"developer_app",id,next,req.expectedVersion()+1);
   return reply(200,id,next,req.expectedVersion()+1);
  });
 }
 public Reply mapping(Actor a,UUID key,Mapping req){
  a.require("m35:mapping_create");
  if(req==null)throw new Problem("invalid_mapping");
  return command(a,key,"CREATE_MAPPING",req,()->{
   code(req.mapCode());code(req.sourceSystem());code(req.targetSystem());hex(req.contractSha256());
   if(req.mapVersion()==null||!req.mapVersion().matches("[A-Z0-9][A-Z0-9_.-]{0,63}"))
    throw new Problem("invalid_mapping_version");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.mapping_versions(id,cell_id,facility_id,"+
    "map_code,map_version,contract_sha256,source_system,target_system,created_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?)",id,a.cell(),a.facility(),req.mapCode(),req.mapVersion(),
    req.contractSha256(),req.sourceSystem(),req.targetSystem(),a.id());
   audit(a,key,"mapping",id,"CREATE",1);
   return reply(201,id,"DRAFT",1);
  });
 }
 public Reply mappingAction(Actor a,UUID key,UUID id,String op,Version req){
  a.require("validate".equals(op)?"m35:mapping_validate":"m35:mapping_approve");
  if(req==null)throw new Problem("invalid_version");
  return command(a,key,"MAPPING_"+op,List.of(id,req),()->{
   var m=row(a,"mapping_versions",id);version(m,req.expectedVersion());
   String before=(String)m.get("status"),next=switch(op){
    case "validate"->"VALIDATED";case "approve"->"APPROVED";
    default->throw new Problem("invalid_action");
   };
   if(!((before.equals("DRAFT")&&next.equals("VALIDATED"))||
    (before.equals("VALIDATED")&&next.equals("APPROVED"))))throw new Problem("invalid_transition");
   if(a.id().equals(m.get("created_by"))||(next.equals("APPROVED")&&a.id().equals(m.get("validated_by"))))
    throw new Problem("independent_checker_required");
   String col=next.equals("VALIDATED")?"validated_by":"approved_by";
   jdbc.update("UPDATE synqoria_integration.mapping_versions SET status=?,version=version+1,"+
    col+"=? WHERE cell_id=? AND facility_id=? AND id=?",next,a.id(),a.cell(),a.facility(),id);
   audit(a,key,"mapping",id,next,req.expectedVersion()+1);
   return reply(200,id,next,req.expectedVersion()+1);
  });
 }
 public Reply ingest(Actor a,UUID key,Ingest req){
  a.require("m35:message_ingest");
  if(req==null||req.partnerId()==null||req.externalEventId()==null||req.sourceProofId()==null)
   throw new Problem("invalid_message");
  return command(a,key,"INGEST_MESSAGE",req,()->{
   code(req.eventType());hex(req.payloadSha256());
   var partner=row(a,"partners",req.partnerId());
   if(!"ENABLED".equals(partner.get("status")))throw new Problem("partner_not_enabled");
   proof(a,req.sourceProofId(),"SOURCE_MESSAGE",(String)partner.get("partner_code"),
    req.externalEventId(),req.payloadSha256());
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.messages(id,cell_id,facility_id,partner_id,"+
    "external_event_id,event_type,payload_sha256,source_proof_id,received_by) "+
    "VALUES(?,?,?,?,?,?,?,?,?)",id,a.cell(),a.facility(),req.partnerId(),
    req.externalEventId(),req.eventType(),req.payloadSha256(),req.sourceProofId(),a.id());
   audit(a,key,"message",id,"QUARANTINE",1);
   return reply(201,id,"QUARANTINED",1);
  });
 }
 public Reply reviewMessage(Actor a,UUID key,UUID id,Version req){
  a.require("m35:message_review");
  if(req==null)throw new Problem("invalid_version");
  return command(a,key,"REVIEW_MESSAGE",List.of(id,req),()->{
   var m=row(a,"messages",id);version(m,req.expectedVersion());
   if(!"QUARANTINED".equals(m.get("status")))throw new Problem("message_not_quarantined");
   if(a.id().equals(m.get("received_by")))throw new Problem("independent_checker_required");
   var partner=row(a,"partners",(UUID)m.get("partner_id"));
   if(!"ENABLED".equals(partner.get("status")))throw new Problem("partner_not_enabled");
   proof(a,(UUID)m.get("source_proof_id"),"SOURCE_MESSAGE",(String)partner.get("partner_code"),
    (UUID)m.get("external_event_id"),(String)m.get("payload_sha256"));
   jdbc.update("UPDATE synqoria_integration.messages SET status='ACCEPTED',"+
    "version=version+1,reviewed_by=? WHERE cell_id=? AND facility_id=? AND id=?",
    a.id(),a.cell(),a.facility(),id);
   audit(a,key,"message",id,"ACCEPT",req.expectedVersion()+1);
   return reply(200,id,"ACCEPTED",req.expectedVersion()+1);
  });
 }
 public Reply replay(Actor a,UUID key,Replay req){
  a.require("m35:replay_request");
  if(req==null||req.messageId()==null)throw new Problem("invalid_replay");
  return command(a,key,"REQUEST_REPLAY",req,()->{
   code(req.reasonCode());
   var m=row(a,"messages",req.messageId());
   if(!"QUARANTINED".equals(m.get("status")))throw new Problem("message_not_quarantined");
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_integration.replay_requests(id,cell_id,facility_id,"+
    "message_id,reason_code,requested_by) VALUES(?,?,?,?,?,?)",
    id,a.cell(),a.facility(),req.messageId(),req.reasonCode(),a.id());
   audit(a,key,"replay",id,"REQUEST",1);
   return reply(201,id,"REQUESTED",1);
  });
 }
 public Reply reviewReplay(Actor a,UUID key,UUID id,Decision req){
  a.require("m35:replay_review");
  if(req==null)throw new Problem("invalid_decision");
  return command(a,key,"REVIEW_REPLAY",List.of(id,req),()->{
   var r=row(a,"replay_requests",id);version(r,req.expectedVersion());
   if(!"REQUESTED".equals(r.get("status")))throw new Problem("invalid_transition");
   if(a.id().equals(r.get("requested_by")))throw new Problem("independent_checker_required");
   var m=row(a,"messages",(UUID)r.get("message_id"));
   if(!"QUARANTINED".equals(m.get("status")))throw new Problem("message_not_quarantined");
   String next=req.approve()?"APPROVED_PENDING_DELIVERY":"DENIED";
   jdbc.update("UPDATE synqoria_integration.replay_requests SET status=?,version=version+1,"+
    "reviewed_by=?,reviewed_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND id=?",
    next,a.id(),a.cell(),a.facility(),id);
   audit(a,key,"replay",id,next,req.expectedVersion()+1);
   // An approval does not transmit, retry, or acknowledge a network message.
   return new Reply(200,Map.of("id",id.toString(),"status",next,
    "version",req.expectedVersion()+1,"dispatched",false,"credentialsIssued",false));
  });
 }
}
