package org.synqoria.facility;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
public class FacilityLifecycleTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args) throws Exception {
  var start=new FacilityBinding(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"ACTIVE",1);
  var l=new FacilityLifecycle(start);
  ok(l.transition(1,"RETIRED",true)==FacilityLifecycle.Result.TRANSITION_DENIED,"no direct retire");
  ok(l.transition(1,"SUSPENDED",false)==FacilityLifecycle.Result.APPROVAL_REQUIRED,"approval required");
  ok(l.transition(1,"SUSPENDED",true)==FacilityLifecycle.Result.APPLIED,"suspend");
  ok(l.snapshot().revision()==2,"revision advanced");
  ok(l.transition(1,"ACTIVE",true)==FacilityLifecycle.Result.REVISION_CONFLICT,"stale revision denied");
  ok(l.transition(2,"ACTIVE",true)==FacilityLifecycle.Result.APPLIED,"reactivate");
  ok(l.transition(3,"SUSPENDED",true)==FacilityLifecycle.Result.APPLIED,"suspend again");
  ok(l.transition(4,"RETIRED",true)==FacilityLifecycle.Result.APPLIED,"retire after suspension");
  ok(l.transition(5,"ACTIVE",true)==FacilityLifecycle.Result.TRANSITION_DENIED,"retired terminal");
  var c=new FacilityLifecycle(start);var pool=Executors.newFixedThreadPool(8);var latch=new CountDownLatch(1);var winners=new AtomicInteger();var tasks=new ArrayList<Future<?>>();
  for(int i=0;i<20;i++)tasks.add(pool.submit(()->{try{latch.await();if(c.transition(1,"SUSPENDED",true)==FacilityLifecycle.Result.APPLIED)winners.incrementAndGet();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
  latch.countDown();for(var t:tasks)t.get(10,TimeUnit.SECONDS);pool.shutdownNow();
  ok(winners.get()==1,"only one CAS transition");
  ok(c.snapshot().revision()==2,"no lost update");
  System.out.println("PASS: "+n+" facility lifecycle assertions (in-memory only)");
 }
}
