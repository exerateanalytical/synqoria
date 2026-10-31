package org.synqoria.scheduling;
import java.util.*;
public final class AppointmentTransitionCommandTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),p=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,a,p);
  var cmd=new AppointmentTransitionCommand(app,"CONFIRMED",1,"aLongUniqueKey123456789",event);
  String d=cmd.digest(scope);ok(d.matches("[0-9a-f]{64}"));ok(d.equals(cmd.digest(scope)));
  ok(!d.equals(new AppointmentTransitionCommand(app,"CANCELLED",1,cmd.idempotencyKey(),event).digest(scope)));
  ok(!d.equals(new AppointmentTransitionCommand(app,"CONFIRMED",2,cmd.idempotencyKey(),event).digest(scope)));
  ok(!d.equals(cmd.digest(new AppointmentTenantContext(UUID.randomUUID(),f,a,p))));
  ok(!d.equals(cmd.digest(new AppointmentTenantContext(t,f,a,UUID.randomUUID()))));
  ok(!d.equals(cmd.digest(new AppointmentTenantContext(t,f,UUID.randomUUID(),p))));
  ok(!d.equals(new AppointmentTransitionCommand(UUID.randomUUID(),"CONFIRMED",1,cmd.idempotencyKey(),event).digest(scope)));
  ok(d.equals(new AppointmentTransitionCommand(app,"CONFIRMED",1,"differentUniqueKey123",UUID.randomUUID()).digest(scope)));
  try{new AppointmentTransitionCommand(app,"UNRECOGNIZED",1,cmd.idempotencyKey(),event);throw new AssertionError();}
  catch(IllegalArgumentException ex){ok(true);}
  try{new AppointmentTransitionCommand(app,"CONFIRMED",0,cmd.idempotencyKey(),event);throw new AssertionError();}
  catch(IllegalArgumentException ex){ok(true);}
  System.out.println("PASS AppointmentTransitionCommandTest assertions="+checks);
 }
}
