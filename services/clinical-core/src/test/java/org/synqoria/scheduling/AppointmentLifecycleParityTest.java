package org.synqoria.scheduling;
import java.sql.*;import java.time.*;import java.util.*;
public final class AppointmentLifecycleParityTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  var window=new AppointmentWindow(Instant.parse("2026-10-30T10:00:00Z"),Instant.parse("2026-10-30T10:30:00Z"),ZoneId.of("Africa/Douala"));
  for(var from:AppointmentAggregate.State.values())for(var to:AppointmentAggregate.State.values()){
   var booking=new AppointmentAggregate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),window,from,7,null);
   boolean domainAllowed=true,sqlAllowed=true;
   try{booking.transition(to,7,"reviewed administrative reason");}catch(IllegalStateException|IllegalArgumentException ex){domainAllowed=false;}
   try{AppointmentTransitionPolicy.requireAllowed(from.name(),to.name());}catch(SQLException ex){sqlAllowed=false;}
   ok(domainAllowed==sqlAllowed);
  }
  try{AppointmentTransitionPolicy.requireAllowed("ARRIVED","CANCELLED");throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentTransitionPolicy.requireAllowed("REQUESTED","UNKNOWN");throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  System.out.println("PASS AppointmentLifecycleParityTest assertions="+checks+" (36 transition pairs + 2 denial checks)");
 }
}
