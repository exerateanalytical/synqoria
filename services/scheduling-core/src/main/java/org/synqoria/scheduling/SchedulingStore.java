package org.synqoria.scheduling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
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
import static org.synqoria.scheduling.SchedulingApplication.*;

@Component
public class SchedulingStore {
 public record Reply(int status,Object body){}
 public record Resource(String resourceCode,String resourceType){}
 public record Slot(UUID resourceId,Instant startsAt,Instant endsAt){}
 public record Booking(UUID patientRef,UUID proofId,UUID slotId){}
 public record Wait(UUID patientRef,UUID proofId,UUID resourceId){}
 public record Offer(long expectedVersion,UUID slotId){}
 public record Move(long expectedVersion,UUID slotId){}
 public record Version(long expectedVersion){}
 private final JdbcTemplate jdbc;
 private final JsonMapper json;
 private final Clock clock;
 private final TransactionTemplate transaction;
 private final String role;
 public SchedulingStore(JdbcTemplate jdbc,JsonMapper json,PlatformTransactionManager manager,
       Clock clock,@Value("${synqoria.scheduling.db-role}")String role){
  if(!"synqoria_schedule_runtime".equals(role))throw new IllegalStateException("m02_restricted_role_required");
  this.jdbc=jdbc;this.json=json;this.clock=clock;this.role=role;
  transaction=new TransactionTemplate(manager);
 }
 public static String sha(String value){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
   .digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private String encode(Object o){return json.writeValueAsString(o);}
 private Object decode(String t){return json.readValue(t,Object.class);}
 public <T>T scoped(Actor actor,Supplier<T> work){
  return Objects.requireNonNull(transaction.execute(tx->{
   String current=jdbc.queryForObject("SELECT current_user",String.class);
   Boolean restricted=jdbc.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls "+
    "AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication "+
    "FROM pg_roles WHERE rolname=current_user",Boolean.class);
   if(!role.equals(current)||!Boolean.TRUE.equals(restricted))throw new Denied("db_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,actor.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,actor.facility().toString());
   return work.get();
  }));
 }
 private Reply command(Actor a,UUID key,String name,Object req,Supplier<Reply> op){
  if(key==null)throw new Denied("idempotency_key_required");
  String hash=sha(name+"|"+encode(req));
  return scoped(a,()->{
   jdbc.update("INSERT INTO synqoria_schedule.commands(cell_id,facility_id,actor_id,command_id,request_hash) "+
    "VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",a.cell(),a.facility(),a.id(),key,hash);
   var last=jdbc.queryForMap("SELECT request_hash,response::text AS response,http_status "+
    "FROM synqoria_schedule.commands WHERE cell_id=? AND facility_id=? AND actor_id=? AND command_id=? FOR UPDATE",
    a.cell(),a.facility(),a.id(),key);
   if(!hash.equals(last.get("request_hash")))throw new Denied("idempotency_collision");
   if(last.get("http_status")!=null)return new Reply(((Number)last.get("http_status")).intValue(),
    decode((String)last.get("response")));
   Reply result=op.get();
   jdbc.update("UPDATE synqoria_schedule.commands SET response=?::jsonb,http_status=? WHERE "+
    "cell_id=? AND facility_id=? AND actor_id=? AND command_id=?",
    encode(result.body()),result.status(),a.cell(),a.facility(),a.id(),key);
   return result;
  });
 }
 private static String code(String value){
  if(value==null||!value.matches("[A-Z][A-Z0-9_]{1,63}"))throw new Denied("invalid_code");
  return value;
 }
 private void audit(Actor a,UUID command,String type,UUID id,String action,long version){
  UUID event=UUID.randomUUID();
  jdbc.update("INSERT INTO synqoria_schedule.audit_events(id,cell_id,facility_id,aggregate_type,"+
   "aggregate_id,action,version,actor_id,command_id) VALUES(?,?,?,?,?,?,?,?,?)",
   event,a.cell(),a.facility(),type,id,action,version,a.id(),command);
  jdbc.update("INSERT INTO synqoria_schedule.outbox(id,cell_id,facility_id,aggregate_id,event_type,payload) "+
   "VALUES(?,?,?,?,?,?::jsonb)",event,a.cell(),a.facility(),id,"m02."+type+"."+action,
   encode(Map.of("id",id.toString(),"version",version,"action",action)));
 }
 private Map<String,Object> row(Actor a,String table,UUID id){
  if(!Set.of("resources","slots","appointments","waitlist").contains(table))throw new Denied("invalid_collection");
  try{
   return jdbc.queryForMap("SELECT * FROM synqoria_schedule."+table+" WHERE cell_id=? AND facility_id=? "+
    "AND id=? FOR UPDATE",a.cell(),a.facility(),id);
  }catch(EmptyResultDataAccessException e){throw new Denied("not_found");}
 }
 private void v(Map<String,Object> r,long expected){
  if(expected<1||((Number)r.get("version")).longValue()!=expected)throw new Denied("stale_version");
 }
 private static String str(Object o){return String.valueOf(o);}
 private Reply result(int status,UUID id,String state,long version){
  return new Reply(status,Map.of("id",id.toString(),"status",state,"version",version));
 }
 private void patient(Actor a,UUID proof,UUID ref){
  if(proof==null||ref==null)throw new Denied("evidence_unavailable");
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text,2))",proof.toString());
  var matches=jdbc.queryForList("SELECT proof_id FROM synqoria_schedule.patient_proofs "+
   "WHERE cell_id=? AND facility_id=? AND proof_id=? AND patient_ref=? "+
   "AND approved_by<>? AND valid_from<=clock_timestamp() AND valid_until>clock_timestamp() "+
   "AND revoked_at IS NULL",a.cell(),a.facility(),proof,ref,a.id());
  if(matches.size()!=1)throw new Denied("evidence_unavailable");
 }
 private Map<String,Object> checkSlot(Actor a,UUID id){
  var slot=row(a,"slots",id);
  if(!"OPEN".equals(slot.get("status")))throw new Denied("slot_not_open");
  if(!((Timestamp)slot.get("starts_at")).toInstant().isAfter(clock.instant()))
   throw new Denied("slot_not_open");
  var resource=row(a,"resources",(UUID)slot.get("resource_id"));
  if(!"ACTIVE".equals(resource.get("status")))throw new Denied("resource_not_active");
  return slot;
 }
 public List<Map<String,Object>> list(Actor a,String table,int limit){
  a.require("m02:read");
  if(!Set.of("resources","slots","appointments","waitlist").contains(table)||limit<1||limit>100)
   throw new Denied("invalid_collection");
  String order=table.equals("slots")?"starts_at":"created_at";
  return scoped(a,()->jdbc.queryForList("SELECT * FROM synqoria_schedule."+table+
   " WHERE cell_id=? AND facility_id=? ORDER BY "+order+" DESC,id LIMIT ?",
   a.cell(),a.facility(),limit));
 }
 public Reply resource(Actor a,UUID key,Resource req){
  a.require("m02:resource_write");
  if(req==null)throw new Denied("invalid_resource");
  return command(a,key,"RESOURCE",req,()->{
   String type=code(req.resourceType()),resource=code(req.resourceCode());
   if(!Set.of("CLINIC","ROOM","PROVIDER","EQUIPMENT").contains(type))throw new Denied("invalid_resource");
   UUID uuid=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_schedule.resources(id,cell_id,facility_id,resource_code,resource_type,created_by) "+
    "VALUES(?,?,?,?,?,?)",uuid,a.cell(),a.facility(),resource,type,a.id());
   audit(a,key,"resource",uuid,"CREATE",1);
   return result(201,uuid,"ACTIVE",1);
  });
 }
 public Reply slot(Actor a,UUID key,Slot req){
  a.require("m02:slot_publish");
  if(req==null||req.resourceId()==null||req.startsAt()==null||req.endsAt()==null)
   throw new Denied("invalid_slot");
  return command(a,key,"SLOT",req,()->{
   if(!req.endsAt().isAfter(req.startsAt())||
     !req.startsAt().isAfter(clock.instant())||
     req.endsAt().isAfter(req.startsAt().plusSeconds(86400)))
    throw new Denied("invalid_slot");
   if(!"ACTIVE".equals(row(a,"resources",req.resourceId()).get("status")))
    throw new Denied("resource_not_active");
   UUID uuid=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_schedule.slots(id,cell_id,facility_id,resource_id,"+
    "starts_at,ends_at,created_by) VALUES(?,?,?,?,?,?,?)",
    uuid,a.cell(),a.facility(),req.resourceId(),Timestamp.from(req.startsAt()),
    Timestamp.from(req.endsAt()),a.id());
   audit(a,key,"slot",uuid,"PUBLISH",1);
   return result(201,uuid,"OPEN",1);
  });
 }
 public Reply book(Actor a,UUID key,Booking req){
  a.require("m02:book");
  if(req==null)throw new Denied("invalid_booking");
  return command(a,key,"BOOK",req,()->{
   patient(a,req.proofId(),req.patientRef());
   checkSlot(a,req.slotId());
   UUID uuid=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_schedule.appointments(id,cell_id,facility_id,patient_ref,proof_id,"+
    "slot_id,creator_id,last_actor) VALUES(?,?,?,?,?,?,?,?)",
    uuid,a.cell(),a.facility(),req.patientRef(),req.proofId(),req.slotId(),a.id(),a.id());
   audit(a,key,"appointment",uuid,"REQUEST",1);
   return result(201,uuid,"REQUESTED",1);
  });
 }
 public Reply appointmentAction(Actor a,UUID key,UUID id,String action,Version request){
  String permission=switch(action){
   case "confirm"->"m02:confirm";case "arrive"->"m02:arrive";
   case "fulfil"->"m02:fulfil";case "cancel"->"m02:cancel";
   case "no-show"->"m02:no_show";default->throw new Denied("invalid_action");
  };
  a.require(permission);
  if(request==null)throw new Denied("invalid_version");
  return command(a,key,"APPOINTMENT_"+action,List.of(id,request),()->{
   var row=row(a,"appointments",id);v(row,request.expectedVersion());
   String previous=str(row.get("status")),next=switch(action){
    case "confirm"->"CONFIRMED";case "arrive"->"ARRIVED";case "fulfil"->"FULFILLED";
    case "cancel"->"CANCELLED";case "no-show"->"NO_SHOW";default->throw new Denied("invalid_action");
   };
   if(!((previous.equals("REQUESTED")&&Set.of("CONFIRMED","CANCELLED").contains(next))||
       (previous.equals("CONFIRMED")&&Set.of("ARRIVED","CANCELLED","NO_SHOW").contains(next))||
       (previous.equals("ARRIVED")&&Set.of("FULFILLED","CANCELLED").contains(next))))
    throw new Denied("invalid_transition");
   if(Set.of("CONFIRMED","ARRIVED","FULFILLED").contains(next))
    patient(a,(UUID)row.get("proof_id"),(UUID)row.get("patient_ref"));
   jdbc.update("UPDATE synqoria_schedule.appointments SET status=?,version=version+1,"+
    "last_actor=?,updated_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND id=?",
    next,a.id(),a.cell(),a.facility(),id);
   audit(a,key,"appointment",id,next,request.expectedVersion()+1);
   return result(200,id,next,request.expectedVersion()+1);
  });
 }
 public Reply reschedule(Actor a,UUID key,UUID id,Move move){
  a.require("m02:book");
  if(move==null||move.slotId()==null)throw new Denied("invalid_slot");
  return command(a,key,"RESCHEDULE",List.of(id,move),()->{
   var appt=row(a,"appointments",id);v(appt,move.expectedVersion());
   if(!Set.of("REQUESTED","CONFIRMED").contains(str(appt.get("status"))))
    throw new Denied("invalid_transition");
   if(move.slotId().equals(appt.get("slot_id")))throw new Denied("invalid_slot");
   patient(a,(UUID)appt.get("proof_id"),(UUID)appt.get("patient_ref"));
   checkSlot(a,move.slotId());
   jdbc.update("UPDATE synqoria_schedule.appointments SET slot_id=?,version=version+1,"+
    "last_actor=?,updated_at=clock_timestamp() WHERE cell_id=? AND facility_id=? AND id=?",
    move.slotId(),a.id(),a.cell(),a.facility(),id);
   audit(a,key,"appointment",id,"RESCHEDULE",move.expectedVersion()+1);
   return result(200,id,str(appt.get("status")),move.expectedVersion()+1);
  });
 }
 public Reply waitlist(Actor a,UUID key,Wait req){
  a.require("m02:waitlist");
  if(req==null||req.resourceId()==null)throw new Denied("invalid_waitlist");
  return command(a,key,"WAITLIST",req,()->{
   patient(a,req.proofId(),req.patientRef());
   if(!"ACTIVE".equals(row(a,"resources",req.resourceId()).get("status")))
    throw new Denied("resource_not_active");
   UUID uid=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_schedule.waitlist(id,cell_id,facility_id,patient_ref,"+
    "proof_id,resource_id,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
    uid,a.cell(),a.facility(),req.patientRef(),req.proofId(),req.resourceId(),a.id(),a.id());
   audit(a,key,"waitlist",uid,"CREATE",1);
   return result(201,uid,"WAITING",1);
  });
 }
 public Reply offer(Actor a,UUID key,UUID id,Offer req){
  a.require("m02:offer");
  if(req==null||req.slotId()==null)throw new Denied("invalid_offer");
  return command(a,key,"OFFER",List.of(id,req),()->{
   var w=row(a,"waitlist",id);v(w,req.expectedVersion());
   if(!"WAITING".equals(w.get("status")))throw new Denied("invalid_transition");
   var slot=checkSlot(a,req.slotId());
   if(!slot.get("resource_id").equals(w.get("resource_id")))throw new Denied("invalid_resource");
   jdbc.update("UPDATE synqoria_schedule.waitlist SET status='OFFERED',offered_slot_id=?,"+
    "updated_by=?,version=version+1,updated_at=clock_timestamp() "+
    "WHERE cell_id=? AND facility_id=? AND id=?",req.slotId(),a.id(),a.cell(),a.facility(),id);
   audit(a,key,"waitlist",id,"OFFER",req.expectedVersion()+1);
   return result(200,id,"OFFERED",req.expectedVersion()+1);
  });
 }
 public Reply accept(Actor a,UUID key,UUID id,Version request){
  a.require("m02:waitlist_accept");
  if(request==null)throw new Denied("invalid_version");
  return command(a,key,"ACCEPT_OFFER",List.of(id,request),()->{
   var w=row(a,"waitlist",id);v(w,request.expectedVersion());
   if(!"OFFERED".equals(w.get("status")))throw new Denied("invalid_transition");
   patient(a,(UUID)w.get("proof_id"),(UUID)w.get("patient_ref"));
   checkSlot(a,(UUID)w.get("offered_slot_id"));
   UUID appt=UUID.randomUUID();
   jdbc.update("INSERT INTO synqoria_schedule.appointments(id,cell_id,facility_id,patient_ref,proof_id,"+
    "slot_id,creator_id,last_actor) VALUES(?,?,?,?,?,?,?,?)",
    appt,a.cell(),a.facility(),w.get("patient_ref"),w.get("proof_id"),
    w.get("offered_slot_id"),a.id(),a.id());
   jdbc.update("UPDATE synqoria_schedule.waitlist SET status='BOOKED',appointment_id=?,"+
    "updated_by=?,version=version+1,updated_at=clock_timestamp() "+
    "WHERE cell_id=? AND facility_id=? AND id=?",appt,a.id(),a.cell(),a.facility(),id);
   audit(a,key,"appointment",appt,"REQUEST",1);
   audit(a,key,"waitlist",id,"BOOKED",request.expectedVersion()+1);
   return result(200,id,"BOOKED",request.expectedVersion()+1);
  });
 }
}
