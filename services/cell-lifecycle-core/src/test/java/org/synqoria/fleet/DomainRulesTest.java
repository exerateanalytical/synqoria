package org.synqoria.fleet;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;

class DomainRulesTest {
 static final Instant NOW=Instant.parse("2026-11-07T10:00:00Z");
 static final String KEY="k".repeat(40);
 static final UUID CELL=UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

 @Test void lifecycleAllowsOnlyDeclaredTransitions(){
  assertTrue(Domain.canMove("PROVISIONING","QUALIFIED"));assertTrue(Domain.canMove("QUALIFIED","ACTIVE"));assertTrue(Domain.canMove("ACTIVE","DRAINING"));
  assertTrue(Domain.canMove("FENCED","MIGRATING"));assertTrue(Domain.canMove("FAILED","RETIRED"));
  assertFalse(Domain.canMove("ACTIVE","RETIRED"));assertFalse(Domain.canMove("PROVISIONING","ACTIVE"));assertFalse(Domain.canMove("RETIRED","ACTIVE"));assertFalse(Domain.canMove("ACTIVE","MIGRATING"));
  for(String s:Domain.CELL_STATES)assertFalse(Domain.canMove(s,s),s);
 }
 @Test void routingDecisionsAreSignedAndTamperEvident(){
  Decision d=new Decision("acme.prod",CELL,3,7,NOW,NOW.plusSeconds(60));
  String sig=Domain.sign(KEY,d);
  assertTrue(Domain.verify(KEY,d,sig,NOW.plusSeconds(10)));
  assertFalse(Domain.verify(KEY,new Decision("acme.prod",CELL,4,7,NOW,NOW.plusSeconds(60)),sig,NOW),"epoch tamper");
  assertFalse(Domain.verify(KEY,new Decision("other.prod",CELL,3,7,NOW,NOW.plusSeconds(60)),sig,NOW),"route tamper");
  assertFalse(Domain.verify("z".repeat(40),d,sig,NOW),"wrong key");
  assertFalse(Domain.verify(KEY,d,sig,NOW.plusSeconds(61)),"expired");
  assertFalse(Domain.verify(KEY,d,null,NOW));
  assertThrows(IllegalStateException.class,()->Domain.sign("short",d));
 }
 @Test void routingFailsClosed(){
  assertTrue(Domain.routable("ACTIVE","HEALTHY",NOW.minusSeconds(30),NOW));
  assertTrue(Domain.routable("ACTIVE","DEGRADED",NOW.minusSeconds(30),NOW));
  assertFalse(Domain.routable("ACTIVE","UNHEALTHY",NOW,NOW));
  assertFalse(Domain.routable("ACTIVE",null,null,NOW),"unknown health is not healthy");
  assertFalse(Domain.routable("ACTIVE","HEALTHY",NOW.minusSeconds(Domain.HEALTH_MAX_AGE_SECONDS+1),NOW),"stale health");
  for(String s:List.of("PROVISIONING","QUALIFIED","DRAINING","FENCED","MIGRATING","RETIRED","FAILED"))assertFalse(Domain.routable(s,"HEALTHY",NOW,NOW),s);
 }
 @Test void releaseGates(){
  assertTrue(Domain.schemaCompatible(5,4,6));assertTrue(Domain.schemaCompatible(4,4,6));assertFalse(Domain.schemaCompatible(3,4,6));assertFalse(Domain.schemaCompatible(7,4,6));
  assertTrue(Domain.stageComplete(List.of("DEPLOYED","DEPLOYED")));assertFalse(Domain.stageComplete(List.of("DEPLOYED","PENDING")));assertFalse(Domain.stageComplete(List.of()));assertFalse(Domain.stageComplete(List.of("FAILED")));
 }
 @Test void migrationMachineIsALine(){
  String state="PREPARED";
  for(String step:Domain.STEPS){assertEquals(state,Domain.stepFrom(step),step);state=Domain.stepTo(step);}
  assertEquals("RETIRED",state);
  assertThrows(Violation.class,()->Domain.stepFrom("TELEPORT"));
  assertTrue(Domain.rollbackPossible("VERIFIED"));assertTrue(Domain.rollbackPossible("OBSERVING"));assertFalse(Domain.rollbackPossible("RETIRED"));assertFalse(Domain.rollbackPossible("ROLLED_BACK"));
  assertFalse(Domain.rollbackNeedsApproval("FENCED"));assertTrue(Domain.rollbackNeedsApproval("SWITCHED"));
 }
 @Test void tenantScopeAndVocabulary(){
  UUID t=UUID.randomUUID();
  Actor scoped=new Actor(UUID.randomUUID(),CELL,CELL,Set.of(Perm.FLEET_READ),Set.of(t));
  assertDoesNotThrow(()->scoped.requireTenant(t));
  assertEquals("tenant_scope_denied",assertThrows(Violation.class,()->scoped.requireTenant(UUID.randomUUID())).code());
  assertEquals("tenant_scope_denied",assertThrows(Violation.class,scoped::requirePlatformWide).code());
  assertDoesNotThrow(()->new Actor(UUID.randomUUID(),CELL,CELL,Set.of(),Set.of()).requirePlatformWide());
  assertEquals(15,Perm.ALL.size());
 }
}
