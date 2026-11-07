package org.synqoria.privacy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.privacy.Rules.*;

class RulesTest {
 @Test void defaultDenyOutsideExactConsentWindow(){
  Instant now=Instant.parse("2026-11-07T10:00:00Z");
  assertTrue(allowed("ACTIVE",now.minusSeconds(60),now.plusSeconds(60),now,true));
  assertFalse(allowed("ACTIVE",now.minusSeconds(60),now.plusSeconds(60),now,false));
  assertFalse(allowed("VERIFIED",now.minusSeconds(60),now.plusSeconds(60),now,true));
  assertFalse(allowed("ACTIVE",now.plusSeconds(60),now.plusSeconds(600),now,true));
  assertFalse(allowed("ACTIVE",now.minusSeconds(600),now,now,true));
 }
 @Test void lifecycleRejectsUnsafeTransitions(){
  transition("DRAFT","VERIFIED");transition("VERIFIED","ACTIVE");transition("ACTIVE","REVOKED");
  for(String bad:List.of("DRAFT:ACTIVE","ACTIVE:DRAFT","REVOKED:ACTIVE","VERIFIED:REVOKED")){
   var parts=bad.split(":");
   assertEquals("invalid_transition",assertThrows(Problem.class,()->transition(parts[0],parts[1])).code());
  }
 }
 @Test void actorPermissionsAreNotImplicit(){
  Actor a=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Set.of("m32:read"));
  a.require("m32:read");
  assertEquals("permission_denied",assertThrows(Problem.class,()->a.require("m32:directive_activate")).code());
 }
 @Test void codesAndTimeAreBounded(){
  assertEquals("invalid_code",assertThrows(Problem.class,()->code("all-patients")).code());
  Instant now=Instant.now();
  assertEquals("invalid_effective_window",assertThrows(Problem.class,()->
   new Directive(UUID.randomUUID(),"CARE","WARD","READ",now,now)).code());
 }
 @Test void privacyCaseCategoriesAreExplicit(){
  assertEquals("invalid_case_category",assertThrows(Problem.class,()->
   new PrivacyCase(UUID.randomUUID(),"MARKETING","REASON")).code());
 }
}
