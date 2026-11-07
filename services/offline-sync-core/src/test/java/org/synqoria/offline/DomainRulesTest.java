package org.synqoria.offline;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.synqoria.offline.domain.Domain;
import org.synqoria.offline.domain.Domain.*;

class DomainRulesTest {
 static final Instant NOW=Instant.parse("2026-11-07T10:00:00Z");
 static final Contract OPEN=new Contract(true,true,false,false,60), MERGE=new Contract(true,true,false,true,60);
 static Cmd cmd(long base,long epoch,String... fields){return new Cmd("note.add",base,epoch,Set.of(fields),NOW.minusSeconds(60),NOW.plusSeconds(3600));}
 static Verdict ev(Cmd c,Contract k,Head h,Set<String> changed){return Domain.evaluate(c,k,h,changed,NOW);}

 @Test void currentBaseApplies(){assertEquals(Kind.APPLY,ev(cmd(3,1),OPEN,new Head(1,3,false),Set.of()).kind());}
 @Test void staleBaseIsAConflictNeverAnOverwrite(){
  Verdict v=ev(cmd(2,1,"a"),OPEN,new Head(1,3,false),Set.of("b"));
  assertEquals(Kind.CONFLICT,v.kind());assertEquals("STALE_BASE",v.conflictKind());
 }
 @Test void disjointFieldsMergeOnlyWhenContractAllows(){
  assertEquals(Kind.MERGE,ev(cmd(2,1,"a"),MERGE,new Head(1,3,false),Set.of("b")).kind());
  assertEquals(Kind.CONFLICT,ev(cmd(2,1,"a"),OPEN,new Head(1,3,false),Set.of("b")).kind());
  Verdict v=ev(cmd(2,1,"a","c"),MERGE,new Head(1,3,false),Set.of("a"));
  assertEquals("INCOMPATIBLE",v.conflictKind());assertEquals(List.of("a"),v.differing());
 }
 @Test void emptyFieldSetNeverAutoMerges(){assertEquals(Kind.CONFLICT,ev(cmd(2,1),MERGE,new Head(1,3,false),Set.of("b")).kind());}
 @Test void staleOwnershipEpochIsFenced(){
  assertEquals("OWNER_FENCED",ev(cmd(3,1),OPEN,new Head(2,3,false),Set.of()).conflictKind());
  assertEquals("invalid_epoch",ev(cmd(3,5),OPEN,new Head(2,3,false),Set.of()).code());
 }
 @Test void frozenRecordsAskTheDeviceToRetry(){assertEquals(Kind.RETRY,ev(cmd(3,1),OPEN,new Head(1,3,true),Set.of()).kind());}
 @Test void baseFromTheFutureIsRejected(){assertEquals("invalid_base",ev(cmd(9,1),OPEN,new Head(1,3,false),Set.of()).code());}
 @Test void safetyGateAndNonOfflineOperationsCannotRunOffline(){
  assertEquals("safety_gate_required",ev(cmd(3,1),new Contract(true,false,true,false,60),new Head(1,3,false),Set.of()).code());
  assertEquals("operation_not_offline",ev(cmd(3,1),new Contract(true,false,false,false,60),new Head(1,3,false),Set.of()).code());
  assertEquals("no_offline_contract",ev(cmd(3,1),null,new Head(1,3,false),Set.of()).code());
  assertEquals("no_offline_contract",ev(cmd(3,1),new Contract(false,true,false,false,60),new Head(1,3,false),Set.of()).code());
 }
 @Test void expiryAndQueueAgeAndClockSkew(){
  Cmd old=new Cmd("n.add",3,1,Set.of(),NOW.minusSeconds(7200),NOW.plusSeconds(60));
  assertEquals("queue_age_exceeded",ev(old,OPEN,new Head(1,3,false),Set.of()).code());
  Cmd dead=new Cmd("n.add",3,1,Set.of(),NOW.minusSeconds(60),NOW.minusSeconds(1));
  assertEquals(Kind.EXPIRED,ev(dead,OPEN,new Head(1,3,false),Set.of()).kind());
  Cmd future=new Cmd("n.add",3,1,Set.of(),NOW.plusSeconds(3600),NOW.plusSeconds(7200));
  assertEquals("clock_skew",ev(future,OPEN,new Head(1,3,false),Set.of()).code());
  assertEquals("unknown_record",ev(cmd(3,1),OPEN,null,Set.of()).code());
 }
 @Test void hashChainIsDeterministic(){
  assertEquals(Domain.nextHash("a","b"),Domain.nextHash("a","b"));
  assertNotEquals(Domain.nextHash("a","b"),Domain.nextHash("a","c"));
  assertEquals(64,Domain.nextHash("a","b").length());
 }
 @Test void closedPermissionVocabulary(){
  assertEquals(11,Perm.ALL.size());
  assertThrows(Violation.class,()->new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Set.of()).require(Perm.SYNC_SUBMIT));
 }
}
