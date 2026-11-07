package org.synqoria.nutrition;

import static org.synqoria.nutrition.Db.m;

import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.nutrition.Db.Reply;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/** M26-S04 tray tracking, bedside identity confirmation, acknowledgment and incident escalation. */
@Service
public class DeliveryService {
 private final Db db;
 private final Safety safety;
 public DeliveryService(Db db,Safety safety){this.db=db;this.safety=safety;}

 public record LoadRequest(UUID mealServiceId,String trayCode){}
 public record Transition(long expectedVersion){}
 public record DeliverRequest(long expectedVersion,UUID scannedPatientRef,String identityMethod){}
 public record ExceptionRequest(long expectedVersion,String incidentType,String severity,String description){}
 public record EscalateRequest(long expectedVersion,String toRole){}
 public record ResolveRequest(long expectedVersion,String note){}

 /**
  * Global lock order is ORDER -> MEAL -> TRAY, the same order used by revoke/supersede, so the delivery
  * gate and a concurrent revocation can never deadlock; whichever commits first is seen by the other.
  */
 private Map<String,Object> trayLock(UUID id,long expected){
  List<Map<String,Object>> pre=db.rows("SELECT ms.id AS meal_id,ms.order_id FROM synqoria_nutrition.trays t "+
   "JOIN synqoria_nutrition.meal_services ms ON ms.id=t.meal_service_id WHERE t.id=?",id);
  if(pre.isEmpty())throw new Violation("not_found");
  db.rows("SELECT id FROM synqoria_nutrition.dietary_orders WHERE id=? FOR SHARE",pre.getFirst().get("orderId"));
  mealLock(pre.getFirst().get("mealId"));
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.trays WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private Map<String,Object> mealLock(Object id){return db.one("SELECT * FROM synqoria_nutrition.meal_services WHERE id=? FOR UPDATE",id);}

 private static String incidentTypeFor(List<String> codes){
  if(codes.stream().anyMatch(c->c.startsWith("ALLERGEN")||c.startsWith("ALLERGY")))return "ALLERGEN_RISK";
  if(codes.contains("DIET_UNKNOWN"))return "DIET_UNKNOWN";
  return "DIET_MISMATCH";
 }

 private UUID incident(Actor a,UUID cmd,UUID meal,UUID tray,String type,String severity,String status,String desc,String role){
  UUID id=UUID.randomUUID();
  db.jdbc.update("INSERT INTO synqoria_nutrition.delivery_incidents(id,cell_id,facility_id,meal_service_id,tray_id,incident_type,severity,status,"+
   "description,reporter_id,escalated_to_role,escalated_by,escalated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,"+
   ("ESCALATED".equals(status)?"clock_timestamp()":"NULL")+",1)",id,a.cell(),a.facility(),meal,tray,type,severity,status,desc,a.id(),
   role,"ESCALATED".equals(status)?a.id():null);
  db.audit(a,cmd,"INCIDENT",id,"ESCALATED".equals(status)?"AUTO_ESCALATE":"REPORT",null,status,1,m("type",type,"severity",severity));
  return id;
 }
 private void toException(Actor a,UUID cmd,Map<String,Object> tray,String reason){
  if(tray==null)return;
  TrayStatus from=TrayStatus.valueOf((String)tray.get("status"));
  if(!from.canMoveTo(TrayStatus.EXCEPTION))return;
  db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='EXCEPTION',version=version+1,updated_at=clock_timestamp() WHERE id=?",tray.get("id"));
  db.audit(a,cmd,"TRAY",(UUID)tray.get("id"),"EXCEPTION",from.name(),"EXCEPTION",((Number)tray.get("version")).longValue()+1,m("reason",reason));
 }
 private void holdMeal(Actor a,UUID cmd,Map<String,Object> meal,String reason){
  String st=(String)meal.get("status");
  if(List.of("HELD","CANCELLED","DELIVERED","ACKNOWLEDGED").contains(st))return;
  db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='HELD',hold_reason=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",reason,meal.get("id"));
  db.audit(a,cmd,"MEAL",(UUID)meal.get("id"),"HOLD",st,"HELD",((Number)meal.get("version")).longValue()+1,m("reason",reason));
 }

 /** Gate used at load, transit and delivery. Blocked meals are held and an incident is committed. */
 private void gate(Actor a,UUID cmd,Map<String,Object> meal,Map<String,Object> tray){
  List<String> block=safety.forMeal(a,meal);
  if(block.isEmpty())return;
  holdMeal(a,cmd,meal,block.getFirst());
  toException(a,cmd,tray,block.getFirst());
  incident(a,cmd,(UUID)meal.get("id"),tray==null?null:(UUID)tray.get("id"),incidentTypeFor(block),
   block.contains("ALLERGEN_CONFLICT")?"CRITICAL":"HIGH","ESCALATED","Automatic safety block: "+String.join(",",block),"CLINICAL_DIETITIAN");
  throw new CommittedRejection("diet_safety_block",block);
 }

 public Reply load(Actor a,UUID cmd,LoadRequest r){
  a.require(Perm.DELIVERY_RECORD);
  return db.command(a,cmd,"TRAY_LOAD",r,()->{
   if(r==null||r.mealServiceId()==null||r.trayCode()==null||!r.trayCode().matches("[A-Z0-9_-]{3,40}"))throw new Violation("invalid_tray");
   db.rows("SELECT o.id FROM synqoria_nutrition.dietary_orders o JOIN synqoria_nutrition.meal_services ms ON ms.order_id=o.id "+
    "WHERE ms.id=? FOR SHARE OF o",r.mealServiceId());
   Map<String,Object> meal=mealLock(r.mealServiceId());
   if(!db.rows("SELECT 1 FROM synqoria_nutrition.trays WHERE meal_service_id=? OR tray_code=?",r.mealServiceId(),r.trayCode()).isEmpty())
    throw new Violation("tray_already_issued");
   if(!"DISPATCHED".equals(meal.get("status")))throw new Violation("meal_not_dispatch_approved");
   gate(a,cmd,meal,null);
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_nutrition.trays(id,cell_id,facility_id,meal_service_id,tray_code,status,loaded_by,version) VALUES (?,?,?,?,?,'LOADED',?,1)",
    id,a.cell(),a.facility(),r.mealServiceId(),r.trayCode(),a.id());
   db.audit(a,cmd,"TRAY",id,"LOAD",null,"LOADED",1,m("mealServiceId",r.mealServiceId().toString()));
   return new Reply(201,tray(id));
  });
 }

 public Reply dispatch(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.DELIVERY_RECORD);
  return db.command(a,cmd,"TRAY_DISPATCH|"+id,t,()->{
   Map<String,Object> tray=trayLock(id,t.expectedVersion());
   if(!TrayStatus.valueOf((String)tray.get("status")).canMoveTo(TrayStatus.IN_TRANSIT)||!"LOADED".equals(tray.get("status")))throw new Violation("invalid_transition");
   gate(a,cmd,mealLock(tray.get("mealServiceId")),tray);
   db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='IN_TRANSIT',dispatched_by=?,dispatched_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.audit(a,cmd,"TRAY",id,"DISPATCH","LOADED","IN_TRANSIT",t.expectedVersion()+1,m());
   return new Reply(200,tray(id));
  });
 }

 public Reply deliver(Actor a,UUID cmd,UUID id,DeliverRequest r){
  a.require(Perm.DELIVERY_RECORD);
  return db.command(a,cmd,"TRAY_DELIVER|"+id,r,()->{
   if(r.scannedPatientRef()==null)throw new Violation("identity_confirmation_required");
   Domain.parse(IdentityMethod.class,r.identityMethod(),"identity_method");
   Map<String,Object> tray=trayLock(id,r.expectedVersion());
   if(!"IN_TRANSIT".equals(tray.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> meal=mealLock(tray.get("mealServiceId"));
   if(!"DISPATCHED".equals(meal.get("status")))throw new Violation("invalid_transition");
   if(!meal.get("patientRef").equals(r.scannedPatientRef())){
    holdMeal(a,cmd,meal,"WRONG_PATIENT_SCAN");
    toException(a,cmd,tray,"WRONG_PATIENT_SCAN");
    incident(a,cmd,(UUID)meal.get("id"),id,"WRONG_PATIENT","CRITICAL","ESCALATED","Scanned identity does not match the tray's patient","NURSE_IN_CHARGE");
    throw new CommittedRejection("identity_mismatch",List.of("WRONG_PATIENT"));
   }
   List<Map<String,Object>> pc=db.rows("SELECT admitted,ward_code FROM synqoria_nutrition.patient_context WHERE patient_ref=?",meal.get("patientRef"));
   if(pc.isEmpty()||!Boolean.TRUE.equals(pc.getFirst().get("admitted"))||!pc.getFirst().get("wardCode").equals(meal.get("wardCode"))){
    holdMeal(a,cmd,meal,"PATIENT_MOVED");
    toException(a,cmd,tray,"PATIENT_MOVED");
    incident(a,cmd,(UUID)meal.get("id"),id,"OTHER","HIGH","ESCALATED","Patient discharged or moved since planning","NURSE_IN_CHARGE");
    throw new CommittedRejection("diet_safety_block",List.of("PATIENT_MOVED"));
   }
   gate(a,cmd,meal,tray);
   db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='DELIVERED',delivered_by=?,delivered_at=clock_timestamp(),identity_method=?,identity_confirmed_ref=?,"+
    "version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),r.identityMethod(),r.scannedPatientRef(),id);
   db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='DELIVERED',version=version+1,updated_at=clock_timestamp() WHERE id=?",meal.get("id"));
   db.audit(a,cmd,"MEAL",(UUID)meal.get("id"),"DELIVER",(String)meal.get("status"),"DELIVERED",((Number)meal.get("version")).longValue()+1,m());
   db.audit(a,cmd,"TRAY",id,"DELIVER","IN_TRANSIT","DELIVERED",r.expectedVersion()+1,m("identityMethod",r.identityMethod()));
   return new Reply(200,tray(id));
  });
 }
 public enum IdentityMethod{WRISTBAND_SCAN,DUAL_VERBAL}

 public Reply acknowledge(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.ACK_RECORD);
  return db.command(a,cmd,"TRAY_ACK|"+id,t,()->{
   Map<String,Object> tray=trayLock(id,t.expectedVersion());
   if(!"DELIVERED".equals(tray.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(tray.get("deliveredBy")))throw new Violation("ack_must_be_independent");
   Map<String,Object> meal=mealLock(tray.get("mealServiceId"));
   db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='ACKNOWLEDGED',version=version+1,updated_at=clock_timestamp() WHERE id=?",meal.get("id"));
   db.audit(a,cmd,"MEAL",(UUID)meal.get("id"),"ACKNOWLEDGE","DELIVERED","ACKNOWLEDGED",((Number)meal.get("version")).longValue()+1,m());
   db.audit(a,cmd,"TRAY",id,"ACKNOWLEDGE","DELIVERED","ACKNOWLEDGED",t.expectedVersion()+1,m());
   return new Reply(200,tray(id));
  });
 }

 public Reply returnTray(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.DELIVERY_RECORD);
  return db.command(a,cmd,"TRAY_RETURN|"+id,t,()->{
   Map<String,Object> tray=trayLock(id,t.expectedVersion());
   TrayStatus from=TrayStatus.valueOf((String)tray.get("status"));
   if(!from.canMoveTo(TrayStatus.RETURNED))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='RETURNED',version=version+1,updated_at=clock_timestamp() WHERE id=?",id);
   db.audit(a,cmd,"TRAY",id,"RETURN",from.name(),"RETURNED",t.expectedVersion()+1,m());
   return new Reply(200,tray(id));
  });
 }

 public Reply reportException(Actor a,UUID cmd,UUID id,ExceptionRequest r){
  a.require(Perm.DELIVERY_RECORD);
  return db.command(a,cmd,"TRAY_EXCEPTION|"+id,r,()->{
   if(!List.of("REFUSED","LATE","MISSING_TRAY","OTHER","DIET_MISMATCH","ALLERGEN_RISK").contains(r.incidentType())||
      !List.of("LOW","MEDIUM","HIGH","CRITICAL").contains(r.severity())||
      r.description()==null||r.description().length()>500)throw new Violation("invalid_incident");
   Map<String,Object> tray=trayLock(id,r.expectedVersion());
   Map<String,Object> meal=mealLock(tray.get("mealServiceId"));
   if(!TrayStatus.valueOf((String)tray.get("status")).canMoveTo(TrayStatus.EXCEPTION))throw new Violation("invalid_transition");
   toException(a,cmd,tray,r.incidentType());
   holdMeal(a,cmd,meal,r.incidentType());
   boolean serious=r.severity().equals("HIGH")||r.severity().equals("CRITICAL");
   incident(a,cmd,(UUID)meal.get("id"),id,r.incidentType(),r.severity(),serious?"ESCALATED":"OPEN",r.description(),serious?"NURSE_IN_CHARGE":null);
   return new Reply(201,tray(id));
  });
 }

 public Reply escalate(Actor a,UUID cmd,UUID id,EscalateRequest r){
  a.require(Perm.INCIDENT_ESCALATE);
  return db.command(a,cmd,"INCIDENT_ESCALATE|"+id,r,()->{
   Map<String,Object> i=incidentLock(id,r.expectedVersion());
   if(!"OPEN".equals(i.get("status")))throw new Violation("invalid_transition");
   if(r.toRole()==null||!r.toRole().matches("[A-Z_]{3,40}"))throw new Violation("invalid_role");
   db.jdbc.update("UPDATE synqoria_nutrition.delivery_incidents SET status='ESCALATED',escalated_to_role=?,escalated_by=?,escalated_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",r.toRole(),a.id(),id);
   db.audit(a,cmd,"INCIDENT",id,"ESCALATE","OPEN","ESCALATED",r.expectedVersion()+1,m("toRole",r.toRole()));
   return new Reply(200,db.one("SELECT * FROM synqoria_nutrition.delivery_incidents WHERE id=?",id));
  });
 }
 public Reply resolve(Actor a,UUID cmd,UUID id,ResolveRequest r){
  a.require(Perm.INCIDENT_RESOLVE);
  return db.command(a,cmd,"INCIDENT_RESOLVE|"+id,r,()->{
   Map<String,Object> i=incidentLock(id,r.expectedVersion());
   if("RESOLVED".equals(i.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(i.get("reporterId")))throw new Violation("resolver_must_be_independent");
   if(r.note()==null||r.note().isBlank()||r.note().length()>500)throw new Violation("resolution_note_required");
   db.jdbc.update("UPDATE synqoria_nutrition.delivery_incidents SET status='RESOLVED',resolved_by=?,resolved_at=clock_timestamp(),resolution_note=?,"+
    "escalated_at=COALESCE(escalated_at,clock_timestamp()),version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),r.note(),id);
   db.audit(a,cmd,"INCIDENT",id,"RESOLVE",(String)i.get("status"),"RESOLVED",r.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_nutrition.delivery_incidents WHERE id=?",id));
  });
 }
 private Map<String,Object> incidentLock(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.delivery_incidents WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }

 private Map<String,Object> tray(UUID id){
  return db.one("SELECT t.*,ms.patient_ref,pc.patient_label,ms.ward_code,ms.bed_code,ms.meal_type,ms.meal_date,ms.diet_category,ms.texture,"+
   "ms.status AS meal_status,ms.hold_reason FROM synqoria_nutrition.trays t JOIN synqoria_nutrition.meal_services ms ON ms.id=t.meal_service_id "+
   "JOIN synqoria_nutrition.patient_context pc ON pc.patient_ref=ms.patient_ref WHERE t.id=?",id);
 }
 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.DELIVERY_READ);
  return db.scoped(a,()->{
   Map<String,Object> t=new LinkedHashMap<>(tray(id));
   t.put("incidents",db.rows("SELECT * FROM synqoria_nutrition.delivery_incidents WHERE tray_id=? ORDER BY created_at,id",id));
   t.put("events",db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,occurred_at FROM synqoria_nutrition.audit_events "+
    "WHERE aggregate_type='TRAY' AND aggregate_id=? ORDER BY occurred_at,event_id",id));
   return t;
  });
 }
 public Map<String,Object> listTrays(Actor a,String ward,String status,int page,int size){
  a.require(Perm.DELIVERY_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(TrayStatus.class,status,"status");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(ward!=null){w.append(" AND ms.ward_code=?");args.add(ward);}
   if(status!=null){w.append(" AND t.status=?");args.add(status);}
   String from=" FROM synqoria_nutrition.trays t JOIN synqoria_nutrition.meal_services ms ON ms.id=t.meal_service_id "+
    "JOIN synqoria_nutrition.patient_context pc ON pc.patient_ref=ms.patient_ref";
   long total=db.jdbc.queryForObject("SELECT count(*)"+from+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT t.id,t.tray_code,t.status,t.version,t.updated_at,ms.patient_ref,pc.patient_label,ms.ward_code,ms.bed_code,"+
    "ms.meal_type,ms.diet_category,ms.texture,ms.status AS meal_status"+from+w+" ORDER BY ms.ward_code,ms.bed_code,t.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public Map<String,Object> listIncidents(Actor a,String status,int page,int size){
  a.require(Perm.DELIVERY_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();String w="";
   if(status!=null){w=" WHERE status=?";args.add(status);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_nutrition.delivery_incidents"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT * FROM synqoria_nutrition.delivery_incidents"+w+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 /** Meals ready for loading (dispatch-approved, no tray yet): drives the S04 "load tray" picker. */
 public List<Map<String,Object>> awaitingTray(Actor a,String ward){
  a.require(Perm.DELIVERY_READ);
  return db.scoped(a,()->db.rows("SELECT ms.id,ms.patient_ref,pc.patient_label,ms.ward_code,ms.bed_code,ms.meal_type,ms.diet_category,ms.texture "+
   "FROM synqoria_nutrition.meal_services ms JOIN synqoria_nutrition.patient_context pc ON pc.patient_ref=ms.patient_ref "+
   "WHERE ms.status='DISPATCHED' AND NOT EXISTS(SELECT 1 FROM synqoria_nutrition.trays t WHERE t.meal_service_id=ms.id)"+
   (ward==null?"":" AND ms.ward_code=?")+" ORDER BY ms.ward_code,ms.bed_code LIMIT 200",ward==null?new Object[0]:new Object[]{ward}));
 }
}
