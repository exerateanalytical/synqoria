package org.synqoria.events;
import java.time.Instant;import java.util.*;
public class OutboxClaimLedgerTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args){
  var l=new OutboxClaimLedger();var k=new OutboxClaimLedger.Key(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  var w1=UUID.randomUUID();var w2=UUID.randomUUID();var now=Instant.parse("2026-10-29T20:00:00Z");
  var a=l.claim(k,w1,now,now.plusSeconds(10));ok(a.outcome()==OutboxClaimLedger.Outcome.CLAIMED,"first claim");
  ok(a.claim().fence()==1,"initial fence");
  ok(l.claim(k,w2,now.plusSeconds(5),now.plusSeconds(20)).outcome()==OutboxClaimLedger.Outcome.BUSY,"cannot steal live lease");
  var b=l.claim(k,w2,now.plusSeconds(10),now.plusSeconds(30));ok(b.outcome()==OutboxClaimLedger.Outcome.CLAIMED,"expired lease reclaimed");
  ok(b.claim().fence()==2,"monotonic fencing");
  ok(!l.delivered(k,w1,1,now.plusSeconds(11)),"stale worker denied");
  ok(!l.delivered(k,w2,2,now.plusSeconds(30)),"expired acknowledgment denied");
  ok(l.delivered(k,w2,2,now.plusSeconds(15)),"current claim acknowledgment");
  ok(!l.delivered(k,w2,2,now.plusSeconds(15)),"no duplicate acknowledgment");
  ok(l.claim(k,w1,now.plusSeconds(40),now.plusSeconds(50)).outcome()==OutboxClaimLedger.Outcome.ALREADY_DELIVERED,"delivered immutable");
  ok(l.claim(k,w1,now,now.plusSeconds(301)).outcome()==OutboxClaimLedger.Outcome.REJECTED,"unbounded lease rejected");
  System.out.println("PASS: "+n+" outbox fencing assertions (in-memory only)");
 }
}
