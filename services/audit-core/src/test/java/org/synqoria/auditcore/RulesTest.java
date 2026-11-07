package org.synqoria.auditcore;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.synqoria.auditcore.Rules.*;

class RulesTest {
 @Test void exactCodeAndHashValidation(){
  assertEquals("invalid_code",assertThrows(Problem.class,()->code("bad-value")).code());
  assertEquals("invalid_sha256",assertThrows(Problem.class,()->hex("deadbeef")).code());
  assertEquals("0".repeat(64),hex("0".repeat(64)));
 }
 @Test void strictReviewTransitions(){
  step("PROPOSED","APPROVED");step("PROPOSED","DENIED");
  assertEquals("invalid_transition",assertThrows(Problem.class,()->step("APPROVED","DENIED")).code());
 }
 @Test void recordBoundariesRejectEmptyScopes(){
  Instant time=Instant.now();
  assertEquals("invalid_export_range",assertThrows(Problem.class,()->
   new ExportRequest(UUID.randomUUID(),"AUDIT","OFFICER",4,2,time)).code());
  assertEquals("invalid_outcome",assertThrows(Problem.class,()->
   new Ingest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
    null,1,"EDIT","UNKNOWN","0".repeat(64),UUID.randomUUID(),time)).code());
 }
 @Test void actorMustHaveExplicitPermission(){
  var actor=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Set.of("m34:search"));
  actor.require("m34:search");
  assertEquals("permission_denied",assertThrows(Problem.class,()->actor.require("m34:ingest")).code());
 }
 @Test void envelopeBindsClinicalOpaqueSubjectActionAndSource(){
  Instant now=Instant.parse("2026-11-07T01:00:00Z");
  UUID stream=UUID.randomUUID(),source=UUID.randomUUID(),event=UUID.randomUUID();
  UUID actor=UUID.randomUUID(),proof=UUID.randomUUID();
  Ingest a=new Ingest(stream,source,event,actor,null,1,"SIGN","SUCCESS","a".repeat(64),proof,now);
  Ingest b=new Ingest(stream,source,event,actor,null,1,"DELETE","SUCCESS","a".repeat(64),proof,now);
  assertNotEquals(AuditStore.envelope(a),AuditStore.envelope(b));
  assertNotEquals(AuditStore.eventHash(UUID.randomUUID(),UUID.randomUUID(),a,"0".repeat(64)),
   AuditStore.eventHash(UUID.randomUUID(),UUID.randomUUID(),b,"0".repeat(64)));
 }
}
