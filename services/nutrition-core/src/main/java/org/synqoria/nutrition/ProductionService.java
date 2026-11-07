package org.synqoria.nutrition;

import static org.synqoria.nutrition.Db.m;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.nutrition.Db.Reply;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/** M26-S03 meal production planning, controlled substitutions and dispatch approval. */
@Service
public class ProductionService {
 private final Db db;
 private final Safety safety;
 public ProductionService(Db db,Safety safety){this.db=db;this.safety=safety;}

 public record BatchRequest(String wardCode,LocalDate mealDate,String mealType,Instant cutoffAt){}
 public record Transition(long expectedVersion){}
 public record SubstitutionRequest(UUID mealServiceId,String originalItemCode,String substituteItemCode,
   List<String> substituteAllergens,String reasonCode){}
 public record Decision(long expectedVersion,boolean approve,String note){}

 private Map<String,Object> batchLock(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.production_batches WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private static String joinCodes(List<String> c){return String.join(",",c);}

 public Reply create(Actor a,UUID cmd,BatchRequest r){
  a.require(Perm.PLAN_MANAGE);
  return db.command(a,cmd,"BATCH_CREATE",r,()->{
   if(r==null||r.wardCode()==null||!r.wardCode().matches("[A-Z0-9_-]{1,32}")||r.mealDate()==null||r.cutoffAt()==null)
    throw new Violation("invalid_batch");
   MealType mt=Domain.parse(MealType.class,r.mealType(),"meal_type");
   if(!r.cutoffAt().isAfter(db.clock.instant()))throw new Violation("cutoff_passed");
   if(!db.rows("SELECT 1 FROM synqoria_nutrition.production_batches WHERE ward_code=? AND meal_date=? AND meal_type=? AND status<>'CANCELLED'",
     r.wardCode(),java.sql.Date.valueOf(r.mealDate()),mt.name()).isEmpty())throw new Violation("batch_already_planned");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_nutrition.production_batches(id,cell_id,facility_id,ward_code,meal_date,meal_type,status,cutoff_at,planned_by,version) "+
    "VALUES (?,?,?,?,?,?,'DRAFT',?,?,1)",id,a.cell(),a.facility(),r.wardCode(),java.sql.Date.valueOf(r.mealDate()),mt.name(),
    OrderService.Timestamp(r.cutoffAt()),a.id());
   List<Map<String,Object>> exceptions=new ArrayList<>();
   for(Map<String,Object> p:db.rows("SELECT patient_ref FROM synqoria_nutrition.patient_context WHERE ward_code=? AND admitted ORDER BY bed_code,patient_ref FOR SHARE",r.wardCode())){
    UUID patient=(UUID)p.get("patientRef");
    List<Map<String,Object>> o=db.rows("SELECT * FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",patient);
    UUID orderId=o.isEmpty()?null:(UUID)o.getFirst().get("id");
    List<String> block=safety.check(a,patient,orderId,null,r.mealDate(),mt,false);
    if(!block.isEmpty()){exceptions.add(m("patientRef",patient.toString(),"codes",block));continue;}
    UUID mid=UUID.randomUUID();
    db.jdbc.update("INSERT INTO synqoria_nutrition.meal_services(id,cell_id,facility_id,batch_id,patient_ref,order_id,ward_code,bed_code,meal_date,"+
     "meal_type,diet_category,texture,status,version) SELECT ?,?,?,?,?,?,pc.ward_code,pc.bed_code,?,?,o.diet_category,o.texture,'PLANNED',1 "+
     "FROM synqoria_nutrition.patient_context pc,synqoria_nutrition.dietary_orders o WHERE pc.patient_ref=? AND o.id=?",
     mid,a.cell(),a.facility(),id,patient,orderId,java.sql.Date.valueOf(r.mealDate()),mt.name(),patient,orderId);
    db.audit(a,cmd,"MEAL",mid,"PLAN",null,"PLANNED",1,m("batchId",id.toString()));
   }
   refreshPortions(id,exceptions);
   db.audit(a,cmd,"BATCH",id,"CREATE",null,"DRAFT",1,m("ward",r.wardCode(),"mealType",mt.name(),"exceptions",exceptions.size()));
   return new Reply(201,detail(id));
  });
 }

 private void refreshPortions(UUID batch,List<Map<String,Object>> exceptions){
  List<String[]> live=db.jdbc.query("SELECT diet_category,texture FROM synqoria_nutrition.meal_services WHERE batch_id=? AND status NOT IN ('HELD','CANCELLED')",
   (rs,n)->new String[]{rs.getString(1),rs.getString(2)},batch);
  db.jdbc.update("UPDATE synqoria_nutrition.production_batches SET portions=?::jsonb,exceptions=?::jsonb,updated_at=clock_timestamp() WHERE id=?",
   db.encode(Domain.portions(live)),db.encode(exceptions),batch);
 }

 /** Re-evaluates every non-held line under the CURRENT clinical facts; unsafe lines are held, never shipped. */
 private int reevaluate(Actor a,UUID cmd,UUID batch,List<String> statuses,List<Map<String,Object>> exceptions){
  int live=0;
  String in="'"+String.join("','",statuses)+"'";
  // lock order ORDER -> MEAL (shared with revoke/supersede): take the share locks first, sorted
  db.rows("SELECT o.id FROM synqoria_nutrition.dietary_orders o WHERE o.id IN (SELECT order_id FROM synqoria_nutrition.meal_services "+
   "WHERE batch_id=?) ORDER BY o.id FOR SHARE",batch);
  for(Map<String,Object> meal:db.rows("SELECT * FROM synqoria_nutrition.meal_services WHERE batch_id=? AND status IN ("+in+") ORDER BY bed_code,id FOR UPDATE",batch)){
   List<String> block=safety.forMeal(a,meal);
   if(block.isEmpty()){live++;continue;}
   UUID mid=(UUID)meal.get("id");long v=((Number)meal.get("version")).longValue()+1;
   db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='HELD',hold_reason=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
    block.getFirst(),mid);
   db.audit(a,cmd,"MEAL",mid,"HOLD",(String)meal.get("status"),"HELD",v,m("codes",joinCodes(block)));
   exceptions.add(m("patientRef",meal.get("patientRef").toString(),"codes",block));
  }
  return live;
 }
 @SuppressWarnings("unchecked")
 private List<Map<String,Object>> existingExceptions(Map<String,Object> b){
  return new ArrayList<>((List<Map<String,Object>>)b.get("exceptions"));
 }

 private Reply advance(Actor a,UUID cmd,UUID id,Transition t,BatchStatus target,String action,List<String> lineStatuses,String lineTarget){
  Map<String,Object> b=batchLock(id,t.expectedVersion());
  BatchStatus from=BatchStatus.valueOf((String)b.get("status"));
  if(!from.canMoveTo(target))throw new Violation("invalid_transition");
  List<Map<String,Object>> ex=existingExceptions(b);
  int live=lineStatuses.isEmpty()?0:reevaluate(a,cmd,id,lineStatuses,ex);
  if(!lineStatuses.isEmpty()&&live==0)throw new Violation("batch_empty");
  if(lineTarget!=null)
   for(Map<String,Object> meal:db.rows("SELECT id,status,version FROM synqoria_nutrition.meal_services WHERE batch_id=? AND status IN ("+
     "'"+String.join("','",lineStatuses)+"') FOR UPDATE",id)){
    db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",lineTarget,meal.get("id"));
    db.audit(a,cmd,"MEAL",(UUID)meal.get("id"),action,(String)meal.get("status"),lineTarget,((Number)meal.get("version")).longValue()+1,m("batchId",id.toString()));
   }
  refreshPortions(id,ex);
  return null;
 }

 public Reply release(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PLAN_RELEASE);
  return db.command(a,cmd,"BATCH_RELEASE|"+id,t,()->{
   Map<String,Object> b=batchLock(id,t.expectedVersion());
   if(!BatchStatus.DRAFT.name().equals(b.get("status")))throw new Violation("invalid_transition");
   if(Instant.parse((String)b.get("cutoffAt")).isBefore(db.clock.instant()))throw new Violation("cutoff_passed");
   advance(a,cmd,id,t,BatchStatus.RELEASED,"RELEASE",List.of("PLANNED"),null);
   db.jdbc.update("UPDATE synqoria_nutrition.production_batches SET status='RELEASED',released_by=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.audit(a,cmd,"BATCH",id,"RELEASE","DRAFT","RELEASED",t.expectedVersion()+1,m());
   return new Reply(200,detail(id));
  });
 }

 public Reply prepared(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PREP_RECORD);
  return db.command(a,cmd,"BATCH_PREPARED|"+id,t,()->{
   batchLock(id,t.expectedVersion());
   if(!db.rows("SELECT 1 FROM synqoria_nutrition.substitutions s JOIN synqoria_nutrition.meal_services ms ON ms.id=s.meal_service_id "+
     "WHERE ms.batch_id=? AND s.status='PROPOSED'",id).isEmpty())throw new Violation("substitutions_pending");
   advance(a,cmd,id,t,BatchStatus.PREPARED,"PREPARE",List.of("PLANNED"),"PREPARED");
   db.jdbc.update("UPDATE synqoria_nutrition.production_batches SET status='PREPARED',prepared_by=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.audit(a,cmd,"BATCH",id,"PREPARE","RELEASED","PREPARED",t.expectedVersion()+1,m());
   return new Reply(200,detail(id));
  });
 }

 public Reply approveDispatch(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.DISPATCH_APPROVE);
  return db.command(a,cmd,"BATCH_DISPATCH|"+id,t,()->{
   Map<String,Object> b=batchLock(id,t.expectedVersion());
   if(a.id().equals(b.get("plannedBy"))||a.id().equals(b.get("preparedBy")))throw new Violation("dispatch_must_be_independent");
   advance(a,cmd,id,t,BatchStatus.DISPATCH_APPROVED,"DISPATCH",List.of("PREPARED"),"DISPATCHED");
   db.jdbc.update("UPDATE synqoria_nutrition.production_batches SET status='DISPATCH_APPROVED',dispatch_approved_by=?,dispatch_approved_at=clock_timestamp(),"+
    "version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.audit(a,cmd,"BATCH",id,"DISPATCH_APPROVE","PREPARED","DISPATCH_APPROVED",t.expectedVersion()+1,m());
   return new Reply(200,detail(id));
  });
 }

 public Reply cancel(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PLAN_MANAGE);
  return db.command(a,cmd,"BATCH_CANCEL|"+id,t,()->{
   Map<String,Object> b=batchLock(id,t.expectedVersion());
   BatchStatus from=BatchStatus.valueOf((String)b.get("status"));
   if(!from.canMoveTo(BatchStatus.CANCELLED))throw new Violation("invalid_transition");
   for(Map<String,Object> meal:db.rows("SELECT id,status,version FROM synqoria_nutrition.meal_services WHERE batch_id=? AND status IN ('PLANNED','PREPARED','HELD') FOR UPDATE",id)){
    db.jdbc.update("UPDATE synqoria_nutrition.meal_services SET status='CANCELLED',hold_reason=NULL,version=version+1,updated_at=clock_timestamp() WHERE id=?",meal.get("id"));
    db.audit(a,cmd,"MEAL",(UUID)meal.get("id"),"CANCEL",(String)meal.get("status"),"CANCELLED",((Number)meal.get("version")).longValue()+1,m("batchId",id.toString()));
   }
   db.jdbc.update("UPDATE synqoria_nutrition.production_batches SET status='CANCELLED',version=version+1,updated_at=clock_timestamp() WHERE id=?",id);
   db.audit(a,cmd,"BATCH",id,"CANCEL",from.name(),"CANCELLED",t.expectedVersion()+1,m());
   return new Reply(200,detail(id));
  });
 }

 // ---------------------------------------------------------------- controlled substitutions
 public Reply propose(Actor a,UUID cmd,SubstitutionRequest r){
  a.require(Perm.SUBST_PROPOSE);
  return db.command(a,cmd,"SUBST_PROPOSE",r,()->{
   if(r==null||r.mealServiceId()==null||r.originalItemCode()==null||r.substituteItemCode()==null||r.reasonCode()==null)throw new Violation("invalid_substitution");
   Domain.codes(List.of(r.originalItemCode(),r.substituteItemCode(),r.reasonCode()),3,"substitution");
   List<String> allergens=Domain.codes(r.substituteAllergens(),64,"substitute_allergens");
   List<Map<String,Object>> ms=db.rows("SELECT status FROM synqoria_nutrition.meal_services WHERE id=? FOR SHARE",r.mealServiceId());
   if(ms.isEmpty())throw new Violation("not_found");
   if(!List.of("PLANNED","PREPARED").contains(ms.getFirst().get("status")))throw new Violation("invalid_transition");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_nutrition.substitutions(id,cell_id,facility_id,meal_service_id,original_item_code,substitute_item_code,"+
    "substitute_allergens,reason_code,status,proposed_by,version) VALUES (?,?,?,?,?,?,?,?,'PROPOSED',?,1)",id,a.cell(),a.facility(),
    r.mealServiceId(),r.originalItemCode(),r.substituteItemCode(),allergens.toArray(new String[0]),r.reasonCode(),a.id());
   db.audit(a,cmd,"SUBSTITUTION",id,"PROPOSE",null,"PROPOSED",1,m("mealServiceId",r.mealServiceId().toString()));
   return new Reply(201,db.one("SELECT * FROM synqoria_nutrition.substitutions WHERE id=?",id));
  });
 }

 public Reply decide(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.SUBST_DECIDE);
  return db.command(a,cmd,"SUBST_DECIDE|"+id,d,()->{
   List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.substitutions WHERE id=? FOR UPDATE",id);
   if(r.isEmpty())throw new Violation("not_found");
   Map<String,Object> s=r.getFirst();
   if(((Number)s.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"PROPOSED".equals(s.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(s.get("proposedBy")))throw new Violation("decider_must_be_independent");
   if(d.note()!=null&&d.note().length()>300)throw new Violation("invalid_note");
   if(d.approve()){
    Map<String,Object> meal=db.one("SELECT * FROM synqoria_nutrition.meal_services WHERE id=? FOR UPDATE",s.get("mealServiceId"));
    List<Map<String,Object>> al=db.rows("SELECT allergen_codes FROM synqoria_nutrition.allergy_snapshots WHERE patient_ref=?",meal.get("patientRef"));
    if(al.isEmpty())throw new Violation("substitution_allergen_conflict",List.of("ALLERGY_UNKNOWN"));
    @SuppressWarnings("unchecked") List<String> patient=(List<String>)al.getFirst().get("allergenCodes");
    @SuppressWarnings("unchecked") List<String> subst=(List<String>)s.get("substituteAllergens");
    List<String> clash=subst.stream().filter(patient::contains).toList();
    if(!clash.isEmpty())throw new Violation("substitution_allergen_conflict",clash);
   }
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_nutrition.substitutions SET status=?,decided_by=?,decided_at=clock_timestamp(),decision_note=?,version=version+1 WHERE id=?",
    to,a.id(),d.note(),id);
   db.audit(a,cmd,"SUBSTITUTION",id,d.approve()?"APPROVE":"REJECT","PROPOSED",to,d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_nutrition.substitutions WHERE id=?",id));
  });
 }

 // ---------------------------------------------------------------- reads
 Map<String,Object> detail(UUID id){
  Map<String,Object> b=new LinkedHashMap<>(db.one("SELECT * FROM synqoria_nutrition.production_batches WHERE id=?",id));
  b.put("lines",db.rows("SELECT ms.id,ms.patient_ref,pc.patient_label,ms.bed_code,ms.diet_category,ms.texture,ms.status,ms.hold_reason,ms.version "+
   "FROM synqoria_nutrition.meal_services ms JOIN synqoria_nutrition.patient_context pc ON pc.patient_ref=ms.patient_ref "+
   "WHERE ms.batch_id=? ORDER BY ms.bed_code,ms.id",id));
  b.put("substitutions",db.rows("SELECT s.* FROM synqoria_nutrition.substitutions s JOIN synqoria_nutrition.meal_services ms ON ms.id=s.meal_service_id "+
   "WHERE ms.batch_id=? ORDER BY s.created_at,s.id",id));
  return b;
 }
 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.WORKLIST_READ);
  return db.scoped(a,()->detail(id));
 }
 public Map<String,Object> list(Actor a,String ward,String status,int page,int size){
  a.require(Perm.WORKLIST_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(BatchStatus.class,status,"status");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(ward!=null){w.append(" AND ward_code=?");args.add(ward);}
   if(status!=null){w.append(" AND status=?");args.add(status);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_nutrition.production_batches"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT * FROM synqoria_nutrition.production_batches"+w+" ORDER BY meal_date DESC,ward_code,id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
}
