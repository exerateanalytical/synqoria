package org.synqoria.scheduling;
import java.time.*;import java.util.*;
public class AppointmentAggregateTest {
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 static void deny(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalStateException|IllegalArgumentException e){n++;}}
 static AppointmentAggregate fresh(){return new AppointmentAggregate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new AppointmentWindow(Instant.now(),Instant.now().plusSeconds(600),ZoneId.of("Africa/Douala")),AppointmentAggregate.State.REQUESTED,1,null);}
 public static void main(String[] args){
  var a=fresh();var confirmed=a.transition(AppointmentAggregate.State.CONFIRMED,1,null);
  ok(confirmed.revision()==2&&confirmed.state()==AppointmentAggregate.State.CONFIRMED);
  deny(()->a.transition(AppointmentAggregate.State.FULFILLED,1,null));
  deny(()->confirmed.transition(AppointmentAggregate.State.ARRIVED,1,null));
  deny(()->confirmed.transition(AppointmentAggregate.State.NO_SHOW,2,null));
  var arrived=confirmed.transition(AppointmentAggregate.State.ARRIVED,2,null);
  ok(arrived.transition(AppointmentAggregate.State.FULFILLED,3,null).revision()==4);
  deny(()->arrived.transition(AppointmentAggregate.State.CANCELLED,3,"late"));
  var cancelled=a.transition(AppointmentAggregate.State.CANCELLED,1,"caller requested");
  deny(()->cancelled.transition(AppointmentAggregate.State.CONFIRMED,2,null));
  ok(confirmed.transition(AppointmentAggregate.State.NO_SHOW,2,"did not arrive").state()==AppointmentAggregate.State.NO_SHOW);
  System.out.println("PASS P06-B0002 "+n+" state/revision/terminal safety assertions (unit)");
 }
}
