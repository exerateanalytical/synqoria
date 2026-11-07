package org.synqoria.reliability;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;

class DomainRulesTest {
 static final Instant NOW=Instant.parse("2026-11-07T10:00:00Z");
 static Signal s(String kind,String state,long agoSeconds){return new Signal(kind,state,NOW.minusSeconds(agoSeconds));}

 @Test void signalThresholds(){
  assertEquals("OK",Domain.signalState("READINESS",1));assertEquals("CRIT",Domain.signalState("READINESS",0));
  assertEquals("OK",Domain.signalState("LATENCY_P95_MS",800));assertEquals("WARN",Domain.signalState("LATENCY_P95_MS",801));assertEquals("CRIT",Domain.signalState("LATENCY_P95_MS",2001));
  assertEquals("WARN",Domain.signalState("ERROR_RATE_PCT",1.5));assertEquals("CRIT",Domain.signalState("SATURATION_PCT",96));assertEquals("WARN",Domain.signalState("BACKLOG",2000));
  assertThrows(Violation.class,()->Domain.signalState("MOOD",1));
 }
 @Test void unknownIsNeverHealthy(){
  assertEquals("UNKNOWN",Domain.serviceHealth(List.of(),List.of(),NOW));
  assertEquals("UNKNOWN",Domain.serviceHealth(List.of(s("LATENCY_P95_MS","OK",10)),List.of(),NOW),"no readiness signal");
  assertEquals("UNKNOWN",Domain.serviceHealth(List.of(s("READINESS","OK",301)),List.of(),NOW),"stale readiness");
  assertEquals("HEALTHY",Domain.serviceHealth(List.of(s("READINESS","OK",10)),List.of(),NOW));
 }
 @Test void healthCombinesSignalsAndHardDependenciesOnly(){
  assertEquals("DOWN",Domain.serviceHealth(List.of(s("READINESS","CRIT",10)),List.of(),NOW));
  assertEquals("DEGRADED",Domain.serviceHealth(List.of(s("READINESS","OK",10),s("LATENCY_P95_MS","WARN",10)),List.of(),NOW));
  assertEquals("DEGRADED",Domain.serviceHealth(List.of(s("READINESS","OK",10),s("SATURATION_PCT","CRIT",10)),List.of(),NOW));
  assertEquals("DEGRADED",Domain.serviceHealth(List.of(s("READINESS","OK",10)),List.of("DOWN"),NOW));
  assertEquals("DEGRADED",Domain.serviceHealth(List.of(s("READINESS","OK",10)),List.of("UNKNOWN"),NOW));
  assertEquals("HEALTHY",Domain.serviceHealth(List.of(s("READINESS","OK",10)),List.of("HEALTHY","DEGRADED"),NOW));
  assertEquals("DOWN",Domain.serviceHealth(List.of(s("READINESS","CRIT",10)),List.of("HEALTHY"),NOW));
  assertEquals("HEALTHY",Domain.serviceHealth(List.of(s("READINESS","OK",10),s("BACKLOG","CRIT",900)),List.of(),NOW),"a stale bad signal is ignored, not trusted");
 }
 @Test void redactionRemovesIdentifiersSecretsAndLongNumbers(){
  assertEquals("contact [REDACTED] now",Domain.redactText("contact jane.doe@example.org now").value());
  assertEquals("id [REDACTED] ok",Domain.redactText("id 123456789 ok").value());
  assertEquals("[REDACTED]",Domain.redactText("Bearer abcdefgh12345678").value());
  assertEquals(0,Domain.redactText("queue depth 42").count());
  Map<String,Object> in=new HashMap<>();in.put("patientName","X");in.put("Auth-Token","t");in.put("count",3);in.put("note","free text");in.put("path","/a?email=a@b.co");in.put("nested",Map.of("MRN","1","ok","fine"));in.put("list",List.of("a"));
  var out=Domain.redactAttributes(in);
  assertEquals("[REDACTED]",out.value().get("patientName"));assertEquals("[REDACTED]",out.value().get("Auth-Token"));assertEquals("[REDACTED]",out.value().get("note"));
  assertEquals(3,out.value().get("count"));assertEquals("/a?email=[REDACTED]",out.value().get("path"));assertEquals("[REDACTED]",out.value().get("list"));
  @SuppressWarnings("unchecked") Map<String,Object> nested=(Map<String,Object>)out.value().get("nested");
  assertEquals("[REDACTED]",nested.get("MRN"));assertEquals("fine",nested.get("ok"));
  assertTrue(out.count()>=6);
 }
 @Test void walContinuityFindsTheFirstGap(){
  var r=List.of(new WalRange(200,300),new WalRange(350,400));
  assertTrue(Domain.walContinuity(200,290,r).continuous());
  var gap=Domain.walContinuity(200,380,r);
  assertFalse(gap.continuous());assertEquals(300L,gap.gapStart());assertEquals(350L,gap.gapEnd());assertEquals(300L,gap.coveredUpTo());
  assertTrue(Domain.walContinuity(200,400,List.of(new WalRange(200,300),new WalRange(250,400))).continuous(),"overlap is fine");
  assertFalse(Domain.walContinuity(200,500,List.of(new WalRange(200,300))).continuous(),"target beyond the archive");
  assertTrue(Domain.walContinuity(200,150,List.of()).continuous());
  assertFalse(Domain.walContinuity(200,300,List.of(new WalRange(210,300))).continuous(),"a hole right after the base backup");
 }
 @Test void recoveryLineHasNoShortcuts(){
  String state="AUTHORIZED";
  for(String step:Domain.STEPS){assertEquals(state,Domain.stepFrom(step),step);state=Domain.stepTo(step);}
  assertEquals("VALIDATING",state);
  assertThrows(Violation.class,()->Domain.stepFrom("YOLO"));
  assertEquals(6,Domain.VALIDATION_ITEMS.size());assertEquals(5,Domain.BACKUP_CHECKS.size());
 }
 @Test void acknowledgementSla(){
  Instant declared=NOW.minusSeconds(6*60);
  assertTrue(Domain.ackOverdue("SEV1","OPEN",declared,NOW));assertFalse(Domain.ackOverdue("SEV2","OPEN",declared,NOW));
  assertFalse(Domain.ackOverdue("SEV1","ACKNOWLEDGED",declared,NOW));
  assertEquals(240,Domain.ackSlaMinutes("SEV4"));
 }
 @Test void vocabulary(){assertEquals(16,Perm.ALL.size());}
}
