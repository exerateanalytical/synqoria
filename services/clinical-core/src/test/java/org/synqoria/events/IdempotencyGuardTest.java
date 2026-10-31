package org.synqoria.events;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class IdempotencyGuardTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args) throws Exception {
  var g=new IdempotencyGuard();var scope=new IdempotencyGuard.Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"patient:write",UUID.randomUUID());
  var a="a".repeat(64);var b="b".repeat(64);
  ok(g.begin(scope,a).status()==IdempotencyGuard.Status.NEW,"first accepted");
  ok(g.begin(scope,a).status()==IdempotencyGuard.Status.IN_PROGRESS,"retry pending");
  ok(g.begin(scope,b).status()==IdempotencyGuard.Status.CONFLICT,"mismatched body");
  ok(g.unknownOutcome(scope)==IdempotencyGuard.Status.UNKNOWN,"unknown in progress");
  ok(g.complete(scope,a,"receipt_000001"),"first completion");
  ok(!g.complete(scope,a,"receipt_000002"),"cannot rewrite receipt");
  ok(g.begin(scope,a).status()==IdempotencyGuard.Status.REPLAY,"replay");
  ok(g.begin(scope,a).receipt().equals("receipt_000001"),"same receipt");
  ok(g.begin(scope,b).status()==IdempotencyGuard.Status.CONFLICT,"post-completion conflict");
  var other=new IdempotencyGuard.Scope(scope.tenant(),scope.facility(),UUID.randomUUID(),scope.operation(),scope.requestId());
  ok(g.begin(other,a).status()==IdempotencyGuard.Status.NEW,"actor isolation");
  var otherTenant=new IdempotencyGuard.Scope(UUID.randomUUID(),scope.facility(),scope.actor(),scope.operation(),scope.requestId());
  ok(g.begin(otherTenant,a).status()==IdempotencyGuard.Status.NEW,"tenant isolation");
  var h=new IdempotencyGuard();var same=new IdempotencyGuard.Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"patient:write",UUID.randomUUID());
  var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var wins=new AtomicInteger();var futures=new ArrayList<Future<?>>();
  for(int i=0;i<40;i++)futures.add(pool.submit(()->{try{start.await();if(h.begin(same,a).status()==IdempotencyGuard.Status.NEW)wins.incrementAndGet();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
  start.countDown();for(var f:futures)f.get(10,TimeUnit.SECONDS);pool.shutdownNow();
  ok(wins.get()==1,"exactly one winner under concurrent requests");
  boolean rejected=false;try{g.begin(scope,"raw payload");}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"bad digest denied");
  System.out.println("PASS: "+n+" idempotency guard assertions (in-memory only)");
 }
}
