package org.synqoria.scheduling;
import java.sql.*;
import java.util.*;
public final class AppointmentTransitionFailureMatrixTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void expectDenied(AppointmentTransitionServiceTest.Fixture f,java.sql.Connection c,String state,boolean shouldUpdate) {
  try {AppointmentTransitionService.execute(c,f.identity,f.proof,f.patient,f.now,"synqoria_test_role",
   AppointmentTransitionServiceTest.key(),f.cmd);throw new AssertionError("expected transaction denial");}
  catch(SQLException ex){ok(state.equals(ex.getSQLState()));}
  ok(f.steps.contains("rollback"));ok(!f.steps.contains("commit"));
  if(!shouldUpdate)ok(!f.steps.contains("update"));
  ok(!f.steps.contains("idempotency-complete"));
 }
 public static void main(String[] args){
  var stale=new AppointmentTransitionServiceTest.Fixture();
  expectDenied(stale,stale.db(false,false,"REQUESTED",2,1,false,false),"40001",false);
  var terminal=new AppointmentTransitionServiceTest.Fixture();
  expectDenied(terminal,terminal.db(false,false,"CANCELLED",1,1,false,false),"23514",false);
  var cas=new AppointmentTransitionServiceTest.Fixture();
  expectDenied(cas,cas.db(false,false,"REQUESTED",1,0,false,false),"40001",true);
  var audit=new AppointmentTransitionServiceTest.Fixture();
  expectDenied(audit,audit.db(false,false,"REQUESTED",1,1,true,false),"23505",true);
  ok(!audit.steps.contains("outbox-insert"));
  var digest=new AppointmentTransitionServiceTest.Fixture();
  expectDenied(digest,digest.db(false,false,"REQUESTED",1,1,false,true),"23505",false);
  ok(!digest.steps.contains("lock"));
  System.out.println("PASS AppointmentTransitionFailureMatrixTest assertions="+checks);
 }
}
