package org.synqoria.nutrition;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.synqoria.nutrition.domain.Domain;
import org.synqoria.nutrition.domain.Domain.*;

/** Pure domain tests. Every fixture is SYNTHETIC; no real patient data exists in this repository. */
class DomainRulesTest {
 static final Instant NOW=Instant.parse("2026-11-07T06:00:00Z");

 static OrderContent content(DietCategory d,Texture t,List<String> restr,List<MealType> meals){
  return new OrderContent(d,t,restr,List.of("PEANUT"),meals,"",NOW.minusSeconds(60),null);
 }
 static SafetyInput input(OrderStatus s,boolean current,Long reconciled,Long allergyV,List<String> allergens,Instant verified,List<Fasting> f){
  return new SafetyInput(s,current,NOW.minusSeconds(3600),null,reconciled,List.of("PEANUT"),List.of(MealType.LUNCH),List.of(),
   DietCategory.REGULAR,Texture.NORMAL,allergyV,allergens,verified,f,List.of(),MealType.LUNCH,
   Instant.parse("2026-11-07T12:00:00Z"),NOW,Duration.ofHours(24));
 }
 static SafetyInput safe(){return input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW.minusSeconds(600),List.of());}

 @Test void safeInputPasses(){assertEquals(List.of(),Domain.evaluate(safe()));}

 @Test void unknownRevokedOutdatedAndExpiredDietsAreBlocked(){
  assertEquals(List.of("DIET_UNKNOWN"),codes(input(null,false,3L,3L,List.of("PEANUT"),NOW,List.of())));
  assertEquals(List.of("DIET_UNKNOWN"),codes(input(OrderStatus.PRESCRIBED,false,3L,3L,List.of("PEANUT"),NOW,List.of())));
  assertTrue(codes(input(OrderStatus.REVOKED,false,3L,3L,List.of("PEANUT"),NOW,List.of())).contains("DIET_REVOKED"));
  assertTrue(codes(input(OrderStatus.CANCELLED,false,3L,3L,List.of("PEANUT"),NOW,List.of())).contains("DIET_REVOKED"));
  assertTrue(codes(input(OrderStatus.SUPERSEDED,false,3L,3L,List.of("PEANUT"),NOW,List.of())).contains("DIET_OUTDATED"));
  assertTrue(codes(input(OrderStatus.APPROVED,false,3L,3L,List.of("PEANUT"),NOW,List.of())).contains("DIET_OUTDATED"));
 }
 static List<String> codes(SafetyInput i){return Domain.evaluate(i);}

 @Test void missingStaleOrChangedAllergyInformationIsBlocked(){
  assertTrue(codes(input(OrderStatus.APPROVED,true,3L,null,null,null,List.of())).contains("ALLERGY_UNKNOWN"));
  assertTrue(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW.minus(Duration.ofHours(25)),List.of())).contains("ALLERGY_OUTDATED"));
  assertFalse(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW.minus(Duration.ofHours(23)),List.of())).contains("ALLERGY_OUTDATED"));
  assertTrue(codes(input(OrderStatus.APPROVED,true,3L,4L,List.of("PEANUT"),NOW,List.of())).contains("ALLERGY_CHANGED"));
  List<String> newAllergy=codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT","SHELLFISH"),NOW,List.of()));
  assertTrue(newAllergy.contains("ALLERGEN_CONFLICT"));
 }

 @Test void fastingBlocksOnlyOverlappingActiveWindows(){
  Instant noon=Instant.parse("2026-11-07T12:00:00Z");
  assertTrue(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW,
   List.of(new Fasting("ACTIVE",NOW,null)))).contains("FASTING_ACTIVE"));
  assertTrue(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW,
   List.of(new Fasting("ACTIVE",NOW,noon.plusSeconds(60))))).contains("FASTING_ACTIVE"));
  assertFalse(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW,
   List.of(new Fasting("ACTIVE",NOW,noon)))).contains("FASTING_ACTIVE"));
  assertFalse(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW,
   List.of(new Fasting("REVOKED",NOW,null)))).contains("FASTING_ACTIVE"));
  assertFalse(codes(input(OrderStatus.APPROVED,true,3L,3L,List.of("PEANUT"),NOW,
   List.of(new Fasting("ACTIVE",noon.plus(Duration.ofHours(3)),null)))).contains("FASTING_ACTIVE"));
 }

 @Test void substituteAllergenAgainstPatientOrOrderExclusionIsBlocked(){
  SafetyInput base=safe();
  SafetyInput withSubst=new SafetyInput(base.orderStatus(),true,base.orderEffectiveFrom(),null,3L,base.orderExcludedAllergens(),
   base.orderMealTiming(),base.orderRestrictions(),base.diet(),base.texture(),3L,List.of("PEANUT"),NOW,List.of(),
   List.of("PEANUT"),MealType.LUNCH,base.mealStart(),NOW,Duration.ofHours(24));
  assertTrue(Domain.evaluate(withSubst).contains("ALLERGEN_CONFLICT"));
 }

 @Test void mealNotInOrderTimingIsBlocked(){
  SafetyInput b=safe();
  SafetyInput dinner=new SafetyInput(b.orderStatus(),true,b.orderEffectiveFrom(),null,3L,b.orderExcludedAllergens(),
   List.of(MealType.BREAKFAST),b.orderRestrictions(),b.diet(),b.texture(),3L,List.of("PEANUT"),NOW,List.of(),List.of(),
   MealType.LUNCH,b.mealStart(),NOW,Duration.ofHours(24));
  assertEquals(List.of("MEAL_NOT_ORDERED"),Domain.evaluate(dinner));
 }

 @Test void contradictoryOrderContentIsRejected(){
  List<MealType> m=List.of(MealType.LUNCH);
  assertDoesNotThrow(()->Domain.validateOrder(content(DietCategory.REGULAR,Texture.NORMAL,List.of(),m),NOW));
  assertContradiction(content(DietCategory.NIL_BY_MOUTH,Texture.NORMAL,List.of(),List.of()));
  assertContradiction(content(DietCategory.NIL_BY_MOUTH,Texture.NONE,List.of(),m));
  assertContradiction(content(DietCategory.REGULAR,Texture.NONE,List.of(),m));
  assertContradiction(content(DietCategory.CLEAR_LIQUID,Texture.NORMAL,List.of(),m));
  assertContradiction(content(DietCategory.PUREED,Texture.SOFT_BITE_SIZED,List.of(),m));
  assertContradiction(content(DietCategory.RENAL,Texture.NORMAL,List.of("FLUID_RESTRICTED","HIGH_FLUID"),m));
  assertEquals("meal_timing_required",assertThrows(Violation.class,
   ()->Domain.validateOrder(content(DietCategory.REGULAR,Texture.NORMAL,List.of(),List.of()),NOW)).code());
  assertDoesNotThrow(()->Domain.validateOrder(content(DietCategory.NIL_BY_MOUTH,Texture.NONE,List.of(),List.of()),NOW));
 }
 static void assertContradiction(OrderContent c){
  assertEquals("diet_contradictory",assertThrows(Violation.class,()->Domain.validateOrder(c,NOW)).code());
 }

 @Test void effectiveWindowMustBeOrdered(){
  OrderContent c=new OrderContent(DietCategory.REGULAR,Texture.NORMAL,List.of(),List.of(),List.of(MealType.LUNCH),"",NOW,NOW.minusSeconds(1));
  assertEquals("invalid_effective_window",assertThrows(Violation.class,()->Domain.validateOrder(c,NOW)).code());
 }

 @Test void stateMachinesAllowOnlyDeclaredTransitions(){
  assertTrue(OrderStatus.PRESCRIBED.canMoveTo(OrderStatus.APPROVED));
  assertFalse(OrderStatus.PRESCRIBED.canMoveTo(OrderStatus.REVOKED));
  assertFalse(OrderStatus.REVOKED.canMoveTo(OrderStatus.APPROVED));
  assertFalse(OrderStatus.SUPERSEDED.canMoveTo(OrderStatus.APPROVED));
  assertTrue(BatchStatus.PREPARED.canMoveTo(BatchStatus.DISPATCH_APPROVED));
  assertFalse(BatchStatus.DRAFT.canMoveTo(BatchStatus.DISPATCH_APPROVED));
  assertFalse(BatchStatus.DISPATCH_APPROVED.canMoveTo(BatchStatus.CANCELLED));
  assertTrue(TrayStatus.IN_TRANSIT.canMoveTo(TrayStatus.DELIVERED));
  assertFalse(TrayStatus.LOADED.canMoveTo(TrayStatus.DELIVERED));
  assertFalse(TrayStatus.LOADED.canMoveTo(TrayStatus.ACKNOWLEDGED));
  assertFalse(TrayStatus.ACKNOWLEDGED.canMoveTo(TrayStatus.EXCEPTION));
 }

 @Test void portionsAreCountedPerDietAndTexture(){
  var p=Domain.portions(List.of(new String[]{"REGULAR","NORMAL"},new String[]{"REGULAR","NORMAL"},new String[]{"RENAL","SOFT_BITE_SIZED"}));
  assertEquals(2,p.get("REGULAR/NORMAL"));assertEquals(1,p.get("RENAL/SOFT_BITE_SIZED"));
  assertEquals(2,p.get("DIET:REGULAR"));assertEquals(3,p.get("TOTAL"));
 }

 @Test void codeVocabularyIsStrictAndDeduplicated(){
  assertEquals(List.of("EGG","PEANUT"),Domain.codes(List.of("PEANUT","EGG","EGG"),10,"x"));
  assertThrows(Violation.class,()->Domain.codes(List.of("peanut"),10,"x"));
  assertThrows(Violation.class,()->Domain.codes(List.of("A"),10,"x"));
 }

 @Test void kitchenRolesNeverHoldClinicalPermissions(){
  Set<String> kitchen=Set.of(Perm.PLAN_MANAGE,Perm.PLAN_RELEASE,Perm.PREP_RECORD,Perm.DISPATCH_APPROVE,Perm.SUBST_PROPOSE,
   Perm.DELIVERY_RECORD,Perm.WORKLIST_READ);
  assertTrue(kitchen.stream().noneMatch(Perm.CLINICAL_ONLY::contains));
  Actor a=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),kitchen);
  assertEquals("permission_denied",assertThrows(Violation.class,()->a.require(Perm.ORDER_PRESCRIBE)).code());
 }
}
