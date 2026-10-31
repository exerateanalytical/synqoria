package org.synqoria.scheduling;
import java.time.*;import java.util.*;import java.util.concurrent.*;
public class ResourceReservationIndexTest {
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args)throws Exception{
  var tenant=UUID.randomUUID();var facility=UUID.randomUUID();var resource=UUID.randomUUID();var patient=UUID.randomUUID();
  var window=new AppointmentWindow(Instant.parse("2026-11-02T09:00:00Z"),Instant.parse("2026-11-02T09:30:00Z"),ZoneId.of("Africa/Douala"));
  var idx=new ResourceReservationIndex();
  var a=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,window,AppointmentAggregate.State.CONFIRMED,2,null);
  var b=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,window,AppointmentAggregate.State.CONFIRMED,2,null);
  var c=new AppointmentAggregate(UUID.randomUUID(),tenant,UUID.randomUUID(),patient,resource,window,AppointmentAggregate.State.CONFIRMED,2,null);
  ok(idx.reserve(a));ok(!idx.reserve(b));ok(idx.conflicts(b));ok(idx.reserve(c));ok(idx.activeCount(tenant,facility,resource)==1);
  ok(!idx.release(b));ok(idx.release(a));ok(idx.reserve(b));
  var requested=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,window,AppointmentAggregate.State.REQUESTED,1,null);
  ok(!idx.reserve(requested));
  var later=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,new AppointmentWindow(window.end(),window.end().plusSeconds(600),window.zone()),AppointmentAggregate.State.CONFIRMED,2,null);
  ok(!idx.conflicts(later));ok(idx.reserve(later));
  var concurrent=new ResourceReservationIndex();
  var pool=Executors.newFixedThreadPool(8);
  try{
   var tasks=new ArrayList<Callable<Boolean>>();
   for(int i=0;i<40;i++){var x=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,window,AppointmentAggregate.State.CONFIRMED,2,null);tasks.add(()->concurrent.reserve(x));}
   int accepted=0;for(var f:pool.invokeAll(tasks))if(f.get())accepted++;
   ok(accepted==1);ok(concurrent.activeCount(tenant,facility,resource)==1);
  }finally{pool.shutdownNow();}
  System.out.println("PASS P06-B0003 "+n+" overlap/isolation/release/concurrency assertions (in-memory only)");
 }
}
