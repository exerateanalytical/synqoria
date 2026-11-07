package org.synqoria.nutrition;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/**
 * Loads time-of-use safety facts inside the caller's transaction and applies the pure domain gate.
 * The dietary order row is locked FOR SHARE so a concurrent revoke/supersede either completes first
 * (and is seen) or waits until this gate's transaction commits (and then holds the affected meals).
 */
@Component
public class Safety {
 private final Db db;
 public Safety(Db db){this.db=db;}

 static Instant nominalStart(LocalDate d,MealType t,java.time.ZoneId zone){
  int hour=switch(t){case BREAKFAST->7;case LUNCH->12;case SNACK->15;case DINNER->18;};
  return d.atTime(hour,0).atZone(zone).toInstant();
 }

 /** orderId may be null (patient has no order). */
 public List<String> check(Actor a,UUID patient,UUID orderId,UUID mealServiceId,LocalDate date,MealType meal,boolean skipMealTiming){
  Instant now=db.clock.instant();
  Map<String,Object> o=null;
  if(orderId!=null){
   List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_nutrition.dietary_orders WHERE id=? AND patient_ref=? FOR SHARE",orderId,patient);
   if(!r.isEmpty())o=r.getFirst();
  }
  UUID current=null;
  List<UUID> cur=db.jdbc.query("SELECT id FROM synqoria_nutrition.dietary_orders WHERE patient_ref=? AND status='APPROVED'",
   (rs,n)->rs.getObject(1,UUID.class),patient);
  if(!cur.isEmpty())current=cur.getFirst();
  List<Map<String,Object>> al=db.rows("SELECT * FROM synqoria_nutrition.allergy_snapshots WHERE patient_ref=?",patient);
  List<Fasting> fasting=db.jdbc.query("SELECT status,starts_at,ends_at FROM synqoria_nutrition.fasting_instructions WHERE patient_ref=?",
   (rs,n)->new Fasting(rs.getString(1),rs.getObject(2,java.time.OffsetDateTime.class).toInstant(),
    rs.getObject(3,java.time.OffsetDateTime.class)==null?null:rs.getObject(3,java.time.OffsetDateTime.class).toInstant()),patient);
  List<String> subst=new ArrayList<>();
  if(mealServiceId!=null)
   for(Map<String,Object> s:db.rows("SELECT substitute_allergens FROM synqoria_nutrition.substitutions WHERE meal_service_id=? AND status='APPROVED'",mealServiceId)){
    @SuppressWarnings("unchecked") List<String> l=(List<String>)s.get("substituteAllergens");
    subst.addAll(l);
   }
  Instant mealStart=nominalStart(date,meal,db.zone);
  @SuppressWarnings("unchecked") List<String> excl=o==null?null:(List<String>)o.get("excludedAllergens");
  @SuppressWarnings("unchecked") List<String> restr=o==null?null:(List<String>)o.get("restrictions");
  @SuppressWarnings("unchecked") List<String> timing=o==null||skipMealTiming?null:(List<String>)o.get("mealTiming");
  Long av=al.isEmpty()?null:((Number)al.getFirst().get("sourceVersion")).longValue();
  @SuppressWarnings("unchecked") List<String> codes=al.isEmpty()?null:(List<String>)al.getFirst().get("allergenCodes");
  Instant verified=al.isEmpty()?null:Instant.parse((String)al.getFirst().get("verifiedAt"));
  SafetyInput in=new SafetyInput(
   o==null?null:OrderStatus.valueOf((String)o.get("status")),
   o!=null&&Objects.equals(current,o.get("id")),
   o==null?null:Instant.parse((String)o.get("effectiveFrom")),
   o==null||o.get("effectiveTo")==null?null:Instant.parse((String)o.get("effectiveTo")),
   o==null||o.get("reconciledAllergyVersion")==null?null:((Number)o.get("reconciledAllergyVersion")).longValue(),
   excl,timing==null?null:timing.stream().map(MealType::valueOf).toList(),restr,
   o==null?null:DietCategory.valueOf((String)o.get("dietCategory")),
   o==null?null:Texture.valueOf((String)o.get("texture")),
   av,codes,verified,fasting,subst,meal,mealStart,now,db.maxAllergyAge);
  return evaluateWithTiming(in,timing==null);
 }

 private List<String> evaluateWithTiming(SafetyInput in,boolean timingSkipped){return Domain.evaluate(in);}

 /** Convenience: gate for an existing meal service row (map from the generic mapper). */
 public List<String> forMeal(Actor a,Map<String,Object> meal){
  return check(a,(UUID)meal.get("patientRef"),(UUID)meal.get("orderId"),(UUID)meal.get("id"),
   LocalDate.parse((String)meal.get("mealDate")),MealType.valueOf((String)meal.get("mealType")),false);
 }
 static Timestamp ts(Instant i){return Timestamp.from(i);}
}
