package org.synqoria.nutrition;

import static org.synqoria.nutrition.Db.m;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.nutrition.Db.Reply;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/** Verified-contract consumption (feed) and M26-S02 dietary orders. */
@Service
public class OrderService {
 private final Db db;
 public OrderService(Db db){this.db=db;}

 // ------------------------------------------------------------------ clinical feed (consumed contracts)
 public record PatientContext(UUID sourceEventId,long identityVersion,String patientLabel,String wardCode,String bedCode,
   boolean admitted,Instant verifiedAt){}
 public record Allergies(UUID sourceEventId,long sourceVersion,List<String> allergenCodes,Instant verifiedAt){}
 public record FastingFeed(UUID sourceEventId,UUID patientRef,long sourceVersion,String status,String reasonCode,
   Instant startsAt,Instant endsAt,Instant verifiedAt){}

 private boolean consume(Actor a,UUID eventId,String kind){
  return db.jdbc.update("INSERT INTO synqoria_nutrition.consumed_events(cell_id,facility_id,source_event_id,kind) "+
   "VALUES (?,?,?,?) ON CONFLICT DO NOTHING",a.cell(),a.facility(),eventId,kind)==1;
 }
 private void requireFresh(Instant verifiedAt,Instant now){
  if(verifiedAt==null||verifiedAt.isAfter(now.plusSeconds(60)))throw new Violation("invalid_verified_at");
 }

 public Reply feedPatient(Actor a,UUID cmd,UUID patient,PatientContext r){
  a.require(Perm.CLINICAL_FEED);
  return db.command(a,cmd,"FEED_PATIENT|"+patient,r,()->{
   requireFresh(r.verifiedAt(),db.clock.instant());
   if(r.patientLabel()==null||r.patientLabel().isBlank()||r.patientLabel().length()>120||r.identityVersion()<1)
    throw new Violation("invalid_patient_context");
   if(!consume(a,r.sourceEventId(),"PATIENT_CONTEXT"))return new Reply(200,m("status","already_applied"));
   List<Map<String,Object>> cur=db.rows("SELECT identity_version FROM synqoria_nutrition.patient_context WHERE patient_ref=? FOR UPDATE",patient);
   if(cur.isEmpty())
    db.jdbc.update("INSERT INTO synqoria_nutrition.patient_context(cell_id,facility_id,patient_ref,patient_label,ward_code,bed_code,"+
     "admitted,identity_version,verified_at,source_event_id) VALUES (?,?,?,?,?,?,?,?,?,?)",a.cell(),a.facility(),patient,
     r.patientLabel(),r.wardCode(),r.bedCode(),r.admitted(),r.identityVersion(),Timestamp(r.verifiedAt()),r.sourceEventId());
   else{
    if(((Number)cur.getFirst().get("identityVersion")).longValue()>=r.identityVersion())throw new Violation("stale_source_version");
    db.jdbc.update("UPDATE synqoria_nutrition.patient_context SET patient_label=?,ward_code=?,bed_code=?,admitted=?,identity_version=?,"+
     "verified_at=?,source_event_id=?,updated_at=clock_timestamp() WHERE patient_ref=?",r.patientLabel(),r.wardCode(),r.bedCode(),
     r.admitted(),r.identityVersion(),Timestamp(r.verifiedAt()),r.sourceEventId(),patient);
   }
   return new Reply(200,m("status","applied","identityVersion",r.identityVersion()));
  });
 }

 public Reply feedAllergies(Actor a,UUID cmd,UUID patient,Allergies r){
  a.require(Perm.CLINICAL_FEED);
  return db.command(a,cmd,"FEED_ALLERGY|"+patient,r,()->{
   requireFresh(r.verifiedAt(),db.clock.instant());
   List<String> codes=Domain.codes(r.allergenCodes(),64,"allergen_codes");
   if(r.sourceVersion()<1)throw new Violation("invalid_source_version");
   if(!consume(a,r.sourceEventId(),"ALLERGY"))return new Reply(200,m("status","already_applied"));
   if(db.rows("SELECT 1 FROM synqoria_nutrition.patient_context WHERE patient_ref=?",patient).isEmpty())throw new Violation("patient_unknown");
   List<Map<String,Object>> cur=db.rows("SELECT source_version FROM synqoria_nutrition.allergy_snapshots WHERE patient_ref=? FOR UPDATE",patient);
   String[] arr=codes.toArray(new String[0]);
   if(cur.isEmpty())
    db.jdbc.update("INSERT INTO synqoria_nutrition.allergy_snapshots(cell_id,facility_id,patient_ref,allergen_codes,no_known_allergies,"+
     "source_version,verified_at,source_event_id) VALUES (?,?,?,?,?,?,?,?)",a.cell(),a.facility(),patient,arr,codes.isEmpty(),
     r.sourceVersion(),Timestamp(r.verifiedAt()),r.sourceEventId());
   else{
    if(((Number)cur.getFirst().get("sourceVersion")).longValue()>=r.sourceVersion())throw new Violation("stale_source_version");
    db.jdbc.update("UPDATE synqoria_nutrition.allergy_snapshots SET allergen_codes=?,no_known_allergies=?,source_version=?,verified_at=?,"+
     "source_event_id=?,updated_at=clock_timestamp() WHERE patient_ref=?",arr,codes.isEmpty(),r.sourceVersion(),Timestamp(r.verifiedAt()),
     r.sourceEventId(),patient);
   }
   return new Reply(200,m("status","applied","sourceVersion",r.sourceVersion()));
  });
 }

 public Reply feedFasting(Actor a,UUID cmd,UUID instruction,FastingFeed r){
  a.require(Perm.CLINICAL_FEED);
  return db.command(a,cmd,"FEED_FASTING|"+instruction,r,()->{
   requireFresh(r.verifiedAt(),db.clock.instant());
   if(r.sourceVersion()<1||r.startsAt()==null||r.patientRef()==null||
      !List.of("ACTIVE","REVOKED","COMPLETED").contains(r.status())||
      r.reasonCode()==null||!r.reasonCode().matches("[A-Z0-9_]{2,40}")||
      (r.endsAt()!=null&&!r.endsAt().isAfter(r.startsAt())))throw new Violation("invalid_fasting_instruction");
   if(!consume(a,r.sourceEventId(),"FASTING"))return new Reply(200,m("status","already_applied"));
   if(db.rows("SELECT 1 FROM synqoria_nutrition.patient_context WHERE patient_ref=?",r.patientRef()).isEmpty())throw new Violation("patient_unknown");
   List<Map<String,Object>> cur=db.rows("SELECT source_version,patient_ref FROM synqoria_nutrition.fasting_instructions WHERE instruction_ref=? FOR UPDATE",instruction);
   if(cur.isEmpty())
    db.jdbc.update("INSERT INTO synqoria_nutrition.fasting_instructions(cell_id,facility_id,instruction_ref,patient_ref,status,reason_code,"+
     "starts_at,ends_at,source_version,verified_at,source_event_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",a.cell(),a.facility(),instruction,
     r.patientRef(),r.status(),r.reasonCode(),Timestamp(r.startsAt()),r.endsAt()==null?null:Timestamp(r.endsAt()),r.sourceVersion(),
     Timestamp(r.verifiedAt()),r.sourceEventId());
   else{
    if(!cur.getFirst().get("patientRef").equals(r.patientRef()))throw new Violation("invalid_fasting_instruction");
    if(((Number)cur.getFirst().get("sourceVersion")).longValue()>=r.sourceVersion())throw new Violation("stale_source_version");
    db.jdbc.update("UPDATE synqoria_nutrition.fasting_instructions SET status=?,reason_code=?,starts_at=?,ends_at=?,source_version=?,"+
     "verified_at=?,source_event_id=?,updated_at=clock_timestamp() WHERE instruction_ref=?",r.status(),r.reasonCode(),Timestamp(r.startsAt()),
     r.endsAt()==null?null:Timestamp(r.endsAt()),r.sourceVersion(),Timestamp(r.verifiedAt()),r.sourceEventId(),instruction);
   }
   return new Reply(200,m("status","applied","sourceVersion",r.sourceVersion()));
  });
 }
 static java.sql.Timestamp Timestamp(Instant i){return java.sql.Timestamp.from(i);}

 // ------------------------------------------------------------------ dietary orders (S02)
 public record OrderRequest(UUID patientRef,UUID clinicalOrderRef,String diet,String texture,List<String> restrictions,
   List<String> excludedAllergens,List<String> mealTiming,String specialInstructions,Instant effectiveFrom,Instant effectiveTo){}
 public record AmendRequest(long expectedVersion,String reason,OrderRequest content){}
 public record TransitionRequest(long expectedVersion,String reason){}

 private OrderContent content(OrderRequest r){
  if(r==null||r.patientRef()==null||r.clinicalOrderRef()==null)throw new Violation("invalid_order");
  List<MealType> timing=new ArrayList<>(new TreeSet<>(Optional.ofNullable(r.mealTiming()).orElse(List.of())
   .stream().map(v->Domain.parse(MealType.class,v,"meal_timing")).toList()));
  OrderContent c=new OrderContent(Domain.parse(DietCategory.class,r.diet(),"diet"),Domain.parse(Texture.class,r.texture(),"texture"),
   Domain.codes(r.restrictions(),20,"restrictions"),Domain.codes(r.excludedAllergens(),64,"excluded_allergens"),
   timing,r.specialInstructions()==null?"":r.specialInstructions().strip(),r.effectiveFrom(),r.effectiveTo());
  Domain.validateOrder(c,db.clock.instant());
  return c;
 }
 private void insertOrder(Actor a,UUID id,OrderRequest r,OrderContent c,UUID supersedes){
  List<Map<String,Object>> p=db.rows("SELECT admitted FROM synqoria_nutrition.patient_context WHERE patient_ref=? FOR SHARE",r.patientRef());
  if(p.isEmpty())throw new Violation("patient_unknown");
  if(!Boolean.TRUE.equals(p.getFirst().get("admitted")))throw new Violation("patient_not_admitted");
  if(!db.rows("SELECT 1 FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='PRESCRIBED'",r.patientRef()).isEmpty())
   throw new Violation("pending_order_exists");
  db.jdbc.update("INSERT INTO synqoria_nutrition.dietary_orders(id,cell_id,facility_id,patient_ref,clinical_order_ref,status,diet_category,"+
   "texture,restrictions,excluded_allergens,meal_timing,special_instructions,effective_from,effective_to,prescriber_id,supersedes_id,version) "+
   "VALUES (?,?,?,?,?,'PRESCRIBED',?,?,?,?,?,?,?,?,?,?,1)",id,a.cell(),a.facility(),r.patientRef(),r.clinicalOrderRef(),
   c.diet().name(),c.texture().name(),c.restrictions().toArray(new String[0]),c.excludedAllergens().toArray(new String[0]),
   c.mealTiming().stream().map(Enum::name).toArray(String[]::new),c.specialInstructions(),Timestamp(c.effectiveFrom()),
   c.effectiveTo()==null?null:Timestamp(c.effectiveTo()),a.id(),supersedes);
 }

 public Reply prescribe(Actor a,UUID cmd,OrderRequest r){
  a.require(Perm.ORDER_PRESCRIBE);
  return db.command(a,cmd,"ORDER_PRESCRIBE",r,()->{
   OrderContent c=content(r);UUID id=UUID.randomUUID();
   insertOrder(a,id,r,c,null);
   db.audit(a,cmd,"ORDER",id,"PRESCRIBE",null,"PRESCRIBED",1,m("patientRef",r.patientRef().toString(),
    "diet",c.diet().name(),"texture",c.texture().name()));
   return new Reply(201,order(id));
  });
 }

 public Reply amend(Actor a,UUID cmd,UUID id,AmendRequest r){
  a.require(Perm.ORDER_AMEND);
  return db.command(a,cmd,"ORDER_AMEND|"+id,r,()->{
   Map<String,Object> cur=lock(id,r.expectedVersion());
   if(!"APPROVED".equals(cur.get("status")))throw new Violation("invalid_transition");
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("reason_required");
   if(!cur.get("patientRef").equals(r.content().patientRef()))throw new Violation("patient_mismatch");
   OrderContent c=content(r.content());UUID nid=UUID.randomUUID();
   insertOrder(a,nid,r.content(),c,id);
   db.audit(a,cmd,"ORDER",nid,"AMEND_SUBMIT",null,"PRESCRIBED",1,m("supersedes",id.toString(),"reason",r.reason()));
   return new Reply(201,order(nid));
  });
 }

 public Reply approve(Actor a,UUID cmd,UUID id,TransitionRequest r){
  a.require(Perm.ORDER_APPROVE);
  return db.command(a,cmd,"ORDER_APPROVE|"+id,r,()->{
   Map<String,Object> cur=lock(id,r.expectedVersion());
   if(!"PRESCRIBED".equals(cur.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(cur.get("prescriberId")))throw new Violation("approver_must_be_independent");
   UUID patient=(UUID)cur.get("patientRef");
   List<Map<String,Object>> al=db.rows("SELECT * FROM synqoria_nutrition.allergy_snapshots WHERE patient_ref=? FOR SHARE",patient);
   if(al.isEmpty())throw new Violation("allergy_reconciliation_required",List.of("ALLERGY_UNKNOWN"));
   if(Instant.parse((String)al.getFirst().get("verifiedAt")).plus(db.maxAllergyAge).isBefore(db.clock.instant()))
    throw new Violation("allergy_reconciliation_required",List.of("ALLERGY_OUTDATED"));
   @SuppressWarnings("unchecked") List<String> allergens=(List<String>)al.getFirst().get("allergenCodes");
   @SuppressWarnings("unchecked") List<String> excluded=(List<String>)cur.get("excludedAllergens");
   if(!excluded.containsAll(allergens))throw new Violation("allergen_not_excluded",
    allergens.stream().filter(x->!excluded.contains(x)).toList());
   Instant effTo=cur.get("effectiveTo")==null?null:Instant.parse((String)cur.get("effectiveTo"));
   if(effTo!=null&&!effTo.isAfter(db.clock.instant()))throw new Violation("invalid_effective_window");
   UUID sup=(UUID)cur.get("supersedesId");
   List<Map<String,Object>> existing=db.rows("SELECT id,version FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED' FOR UPDATE",patient);
   if(!existing.isEmpty()){
    UUID eid=(UUID)existing.getFirst().get("id");
    if(!eid.equals(sup))throw new Violation("approved_order_exists");
    long ev=((Number)existing.getFirst().get("version")).longValue();
    db.jdbc.update("UPDATE synqoria_nutrition.dietary_orders SET status='SUPERSEDED',version=version+1,updated_at=clock_timestamp() WHERE id=?",eid);
    db.audit(a,cmd,"ORDER",eid,"SUPERSEDE","APPROVED","SUPERSEDED",ev+1,m("supersededBy",id.toString()));
    holdMeals(a,cmd,eid,"ORDER_SUPERSEDED");
   }else if(sup!=null){
    // the order being amended was cancelled/revoked meanwhile: an amendment cannot silently reinstate it
    throw new Violation("amended_order_no_longer_current");
   }
   db.jdbc.update("UPDATE synqoria_nutrition.dietary_orders SET status='APPROVED',approver_id=?,approved_at=clock_timestamp(),"+
    "reconciled_allergy_version=?,reconciled_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",
    a.id(),((Number)al.getFirst().get("sourceVersion")).longValue(),id);
   long v=r.expectedVersion()+1;
   db.audit(a,cmd,"ORDER",id,"APPROVE","PRESCRIBED","APPROVED",v,m("allergyVersion",al.getFirst().get("sourceVersion")));
   return new Reply(200,order(id));
  });
 }

 public Reply cancel(Actor a,UUID cmd,UUID id,TransitionRequest r){
  a.require(Perm.ORDER_CANCEL);return terminate(a,cmd,id,r,"CANCELLED","CANCEL");
 }
 public Reply revoke(Actor a,UUID cmd,UUID id,TransitionRequest r){
  a.require(Perm.ORDER_REVOKE);return terminate(a,cmd,id,r,"REVOKED","REVOKE");
 }
 private Reply terminate(Actor a,UUID cmd,UUID id,TransitionRequest r,String target,String action){
  return db.command(a,cmd,"ORDER_"+action+"|"+id,r,()->{
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("reason_required");
   Map<String,Object> cur=lock(id,r.expectedVersion());
   OrderStatus from=OrderStatus.valueOf((String)cur.get("status"));
   if(!from.canMoveTo(OrderStatus.valueOf(target))||(target.equals("REVOKED")&&from!=OrderStatus.APPROVED))
    throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_nutrition.dietary_orders SET status=?,status_reason=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
    target,r.reason(),id);
   db.audit(a,cmd,"ORDER",id,action,from.name(),target,r.expectedVersion()+1,m("reason",r.reason()));
   if(from==OrderStatus.APPROVED)holdMeals(a,cmd,id,"ORDER_"+target);
   return new Reply(200,order(id));
  });
 }

 /** Any not-yet-delivered meal built on this order is held immediately; undelivered trays go to EXCEPTION. */
 void holdMeals(Actor a,UUID cmd,UUID orderId,String reason){
  for(Map<String,Object> meal:db.rows("SELECT id,status,version FROM synqoria_nutrition.meal_services WHERE order_id=? "+
    "AND status IN ('PLANNED','PREPARED','DISPATCHED') FOR UPDATE",orderId)){
   UUID mid=(UUID)meal.get("id");long v=((Number)meal.get("version")).longValue()+1;
   db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='HELD',hold_reason=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",reason,mid);
   db.audit(a,cmd,"MEAL",mid,"HOLD",(String)meal.get("status"),"HELD",v,m("reason",reason));
   for(Map<String,Object> t:db.rows("SELECT id,status,version FROM synqoria_nutrition.trays WHERE meal_service_id=? AND status IN ('LOADED','IN_TRANSIT') FOR UPDATE",mid)){
    long tv=((Number)t.get("version")).longValue()+1;
    db.jdbc.update("UPDATE synqoria_nutrition.trays SET status='EXCEPTION',version=version+1,updated_at=clock_timestamp() WHERE id=?",t.get("id"));
    db.audit(a,cmd,"TRAY",(UUID)t.get("id"),"EXCEPTION",(String)t.get("status"),"EXCEPTION",tv,m("reason",reason));
   }
  }
 }

 private Map<String,Object> lock(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.dietary_orders WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private Map<String,Object> order(UUID id){return db.one("SELECT * FROM synqoria_nutrition.dietary_orders WHERE id=?",id);}

 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.ORDER_READ);
  return db.scoped(a,()->order(id));
 }
 public List<Map<String,Object>> history(Actor a,UUID id){
  a.require(Perm.ORDER_READ);
  return db.scoped(a,()->{
   order(id);
   return db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,details,occurred_at FROM "+
    "synqoria_nutrition.audit_events WHERE aggregate_type='ORDER' AND aggregate_id=? ORDER BY occurred_at,event_id",id);
  });
 }
 public Map<String,Object> list(Actor a,UUID patient,String status,int page,int size){
  a.require(Perm.ORDER_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(OrderStatus.class,status,"status");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(patient!=null){w.append(" AND patient_ref=?");args.add(patient);}
   if(status!=null){w.append(" AND status=?");args.add(status);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_nutrition.dietary_orders"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT * FROM synqoria_nutrition.dietary_orders"+w+" ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
}
