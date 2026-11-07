package org.synqoria.nutrition.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M26 domain model: vocabularies, permissions, state machines and safety rules. No I/O. */
public final class Domain {
 private Domain(){}

 /** Stable, code-only business failure. Messages never carry patient data. */
 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }
 /** A rejection whose evidence (incident, hold) must be committed before the error is returned. */
 public static final class CommittedRejection extends Violation {
  public CommittedRejection(String code,List<String> details){super(code,details);}
 }

 public static final class Perm {
  private Perm(){}
  public static final String WORKLIST_READ="m26:worklist_read", ORDER_READ="m26:order_read",
   ORDER_PRESCRIBE="m26:order_prescribe", ORDER_AMEND="m26:order_amend", ORDER_APPROVE="m26:order_approve",
   ORDER_CANCEL="m26:order_cancel", ORDER_REVOKE="m26:order_revoke",
   PLAN_MANAGE="m26:plan_manage", PLAN_RELEASE="m26:plan_release", PREP_RECORD="m26:prep_record",
   DISPATCH_APPROVE="m26:dispatch_approve", SUBST_PROPOSE="m26:subst_propose", SUBST_DECIDE="m26:subst_decide",
   DELIVERY_READ="m26:delivery_read", DELIVERY_RECORD="m26:delivery_record", ACK_RECORD="m26:ack_record",
   INCIDENT_ESCALATE="m26:incident_escalate", INCIDENT_RESOLVE="m26:incident_resolve",
   CLINICAL_FEED="m26:clinical_feed";
  public static final Set<String> ALL=Set.of(WORKLIST_READ,ORDER_READ,ORDER_PRESCRIBE,ORDER_AMEND,ORDER_APPROVE,
   ORDER_CANCEL,ORDER_REVOKE,PLAN_MANAGE,PLAN_RELEASE,PREP_RECORD,DISPATCH_APPROVE,SUBST_PROPOSE,SUBST_DECIDE,
   DELIVERY_READ,DELIVERY_RECORD,ACK_RECORD,INCIDENT_ESCALATE,INCIDENT_RESOLVE,CLINICAL_FEED);
  /** Only a clinical source may hold these; the kitchen role set must never contain them. */
  public static final Set<String> CLINICAL_ONLY=Set.of(ORDER_PRESCRIBE,ORDER_AMEND,ORDER_APPROVE,ORDER_REVOKE,SUBST_DECIDE);
 }

 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String permission){if(!grants.contains(permission))throw new Violation("permission_denied");}
 }

 public enum DietCategory{REGULAR,DIABETIC,RENAL,CARDIAC,LOW_SODIUM,HIGH_PROTEIN,CLEAR_LIQUID,FULL_LIQUID,PUREED,NIL_BY_MOUTH}
 public enum Texture{NORMAL,SOFT_BITE_SIZED,MINCED_MOIST,PUREED,LIQUIDISED,NONE}
 public enum MealType{BREAKFAST,LUNCH,DINNER,SNACK}

 public enum OrderStatus{
  PRESCRIBED,APPROVED,SUPERSEDED,CANCELLED,REVOKED,EXPIRED;
  public boolean canMoveTo(OrderStatus next){
   return switch(this){
    case PRESCRIBED->next==APPROVED||next==CANCELLED;
    case APPROVED->next==SUPERSEDED||next==CANCELLED||next==REVOKED||next==EXPIRED;
    default->false;
   };
  }
 }
 public enum BatchStatus{
  DRAFT,RELEASED,PREPARED,DISPATCH_APPROVED,CANCELLED;
  public boolean canMoveTo(BatchStatus n){
   return switch(this){
    case DRAFT->n==RELEASED||n==CANCELLED;
    case RELEASED->n==PREPARED||n==CANCELLED;
    case PREPARED->n==DISPATCH_APPROVED||n==CANCELLED;
    default->false;
   };
  }
 }
 public enum TrayStatus{
  LOADED,IN_TRANSIT,DELIVERED,ACKNOWLEDGED,EXCEPTION,RETURNED;
  public boolean canMoveTo(TrayStatus n){
   return switch(this){
    case LOADED->n==IN_TRANSIT||n==EXCEPTION||n==RETURNED;
    case IN_TRANSIT->n==DELIVERED||n==EXCEPTION||n==RETURNED;
    case DELIVERED->n==ACKNOWLEDGED||n==EXCEPTION;
    case EXCEPTION->n==RETURNED;
    default->false;
   };
  }
 }

 private static final Pattern CODE=Pattern.compile("[A-Z0-9_]{2,40}");
 public static List<String> codes(Collection<String> in,int max,String field){
  if(in==null)return List.of();
  if(in.size()>max)throw new Violation("invalid_"+field);
  TreeSet<String> out=new TreeSet<>();
  for(String c:in){
   if(c==null||!CODE.matcher(c).matches())throw new Violation("invalid_"+field);
   out.add(c);
  }
  return List.copyOf(out);
 }
 public static <E extends Enum<E>> E parse(Class<E> type,String value,String field){
  try{return Enum.valueOf(type,value);}catch(RuntimeException e){throw new Violation("invalid_"+field);}
 }

 /** Content of a dietary order as prescribed by a clinical source. */
 public record OrderContent(DietCategory diet,Texture texture,List<String> restrictions,
   List<String> excludedAllergens,List<MealType> mealTiming,String specialInstructions,
   Instant effectiveFrom,Instant effectiveTo){}

 private static final List<Set<String>> CONFLICTING_RESTRICTIONS=List.of(
  Set.of("FLUID_RESTRICTED","HIGH_FLUID"),Set.of("LOW_POTASSIUM","HIGH_POTASSIUM"),
  Set.of("LOW_PROTEIN","HIGH_PROTEIN"),Set.of("VEGAN","CONTAINS_ANIMAL_PRODUCT"));

 /** Throws on any explicit contradiction inside one order. Mirrors the DB CHECK constraints. */
 public static void validateOrder(OrderContent c,Instant now){
  if(c.diet()==null||c.texture()==null||c.effectiveFrom()==null)throw new Violation("invalid_order");
  boolean nbm=c.diet()==DietCategory.NIL_BY_MOUTH;
  if(nbm!=(c.texture()==Texture.NONE))throw new Violation("diet_contradictory",List.of("texture_vs_diet"));
  boolean liquid=c.diet()==DietCategory.CLEAR_LIQUID||c.diet()==DietCategory.FULL_LIQUID;
  if(!nbm&&liquid!=(c.texture()==Texture.LIQUIDISED))throw new Violation("diet_contradictory",List.of("texture_vs_diet"));
  if(c.diet()==DietCategory.PUREED&&c.texture()!=Texture.PUREED)throw new Violation("diet_contradictory",List.of("texture_vs_diet"));
  if(nbm&&!c.mealTiming().isEmpty())throw new Violation("diet_contradictory",List.of("nbm_with_meals"));
  if(!nbm&&c.mealTiming().isEmpty())throw new Violation("meal_timing_required");
  if(c.specialInstructions()!=null&&c.specialInstructions().length()>500)throw new Violation("invalid_special_instructions");
  if(c.effectiveTo()!=null&&!c.effectiveTo().isAfter(c.effectiveFrom()))throw new Violation("invalid_effective_window");
  if(c.effectiveTo()!=null&&!c.effectiveTo().isAfter(now))throw new Violation("invalid_effective_window");
  Set<String> r=new HashSet<>(c.restrictions());
  for(Set<String> pair:CONFLICTING_RESTRICTIONS)
   if(r.containsAll(pair))throw new Violation("diet_contradictory",List.of("restrictions_conflict"));
 }

 /** Portion calculation: one portion per live meal service, grouped by diet category and texture. */
 public static SortedMap<String,Integer> portions(Collection<String[]> dietTexturePairs){
  TreeMap<String,Integer> out=new TreeMap<>();
  int total=0;
  for(String[] p:dietTexturePairs){
   out.merge(p[0]+"/"+p[1],1,Integer::sum);out.merge("DIET:"+p[0],1,Integer::sum);total++;
  }
  out.put("TOTAL",total);
  return out;
 }

 public record Fasting(String status,Instant startsAt,Instant endsAt){}
 /** Everything the safety gate needs, read under row locks inside the same transaction. */
 public record SafetyInput(
  OrderStatus orderStatus,boolean orderIsCurrentApproved,Instant orderEffectiveFrom,Instant orderEffectiveTo,
  Long orderReconciledAllergyVersion,Collection<String> orderExcludedAllergens,Collection<MealType> orderMealTiming,
  Collection<String> orderRestrictions,DietCategory diet,Texture texture,
  Long allergyVersion,Collection<String> allergenCodes,Instant allergyVerifiedAt,
  List<Fasting> fasting,Collection<String> approvedSubstituteAllergens,
  MealType mealType,Instant mealStart,Instant now,Duration maxAllergyAge){}

 /**
  * Time-of-use safety gate. Empty list means "safe to proceed". Applied at plan, release, dispatch,
  * tray load, tray dispatch and delivery. Unknown, outdated, revoked or contradictory => blocked.
  */
 public static List<String> evaluate(SafetyInput in){
  TreeSet<String> block=new TreeSet<>();
  if(in.orderStatus()==null){block.add("DIET_UNKNOWN");}
  else{
   switch(in.orderStatus()){
    case REVOKED,CANCELLED->block.add("DIET_REVOKED");
    case EXPIRED->block.add("DIET_EXPIRED");
    case SUPERSEDED->block.add("DIET_OUTDATED");
    case PRESCRIBED->block.add("DIET_UNKNOWN");
    case APPROVED->{
     if(!in.orderIsCurrentApproved())block.add("DIET_OUTDATED");
     if(in.orderEffectiveFrom().isAfter(in.now())||
        (in.orderEffectiveTo()!=null&&!in.orderEffectiveTo().isAfter(in.now())))block.add("DIET_EXPIRED");
     if(in.orderMealTiming()!=null&&!in.orderMealTiming().contains(in.mealType()))block.add("MEAL_NOT_ORDERED");
     if(in.diet()==DietCategory.NIL_BY_MOUTH)block.add("DIET_NIL_BY_MOUTH");
    }
   }
  }
  if(in.allergyVersion()==null||in.allergenCodes()==null)block.add("ALLERGY_UNKNOWN");
  else{
   if(in.allergyVerifiedAt().plus(in.maxAllergyAge()).isBefore(in.now()))block.add("ALLERGY_OUTDATED");
   if(in.orderReconciledAllergyVersion()!=null&&in.allergyVersion()>in.orderReconciledAllergyVersion())
    block.add("ALLERGY_CHANGED");
   if(in.orderExcludedAllergens()!=null&&!in.orderExcludedAllergens().containsAll(in.allergenCodes()))
    block.add("ALLERGEN_CONFLICT");
   for(String a:in.approvedSubstituteAllergens())
    if(in.allergenCodes().contains(a)||(in.orderExcludedAllergens()!=null&&in.orderExcludedAllergens().contains(a)))
     block.add("ALLERGEN_CONFLICT");
  }
  for(Fasting f:in.fasting()){
   if(!"ACTIVE".equals(f.status()))continue;
   boolean startsBeforeMealEnds=f.startsAt().isBefore(in.mealStart().plus(Duration.ofHours(2)));
   boolean endsAfterMealStarts=f.endsAt()==null||f.endsAt().isAfter(in.mealStart());
   if(startsBeforeMealEnds&&endsAfterMealStarts)block.add("FASTING_ACTIVE");
  }
  if(in.diet()!=null&&in.texture()!=null){
   boolean nbm=in.diet()==DietCategory.NIL_BY_MOUTH;
   if(nbm!=(in.texture()==Texture.NONE))block.add("DIET_CONTRADICTORY");
  }
  if(in.orderRestrictions()!=null)
   for(Set<String> pair:CONFLICTING_RESTRICTIONS)if(in.orderRestrictions().containsAll(pair))block.add("DIET_CONTRADICTORY");
  return List.copyOf(block);
 }
}
