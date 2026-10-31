package org.synqoria.scheduling;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
public final class AppointmentTransitionMutationTest {
 static int assertions=0;
 static void check(boolean v) { assertions++; if(!v) throw new AssertionError("assertion "+assertions); }
 static void denied(RunnableWithSql r) { try { r.run(); throw new AssertionError("should deny"); } catch(SQLException expected) {check("23514".equals(expected.getSQLState()));} }
 interface RunnableWithSql {void run() throws SQLException;}
 public static void main(String[] args) throws Exception {
  Map<Integer,Object> bound=new HashMap<>();
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(proxy,method,a)->{if(method.getName().startsWith("set")){bound.put((Integer)a[0],a[1]);return null;}throw new UnsupportedOperationException(method.getName());});
  UUID ap=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID();
  AppointmentTransitionMutation.bind(ps,"REQUESTED","CONFIRMED",ap,t,f,p,4);
  check("CONFIRMED".equals(bound.get(1)));check(ap.equals(bound.get(2)));check(t.equals(bound.get(3)));check(f.equals(bound.get(4)));check(p.equals(bound.get(5)));check(Long.valueOf(4).equals(bound.get(6)));check("REQUESTED".equals(bound.get(7)));
  bound.clear();denied(()->AppointmentTransitionMutation.bind(ps,"CANCELLED","ARRIVED",ap,t,f,p,4));check(bound.isEmpty());
  denied(()->AppointmentTransitionMutation.bind(ps,"CONFIRMED","CONFIRMED",ap,t,f,p,4));check(bound.isEmpty());
  denied(()->AppointmentTransitionMutation.bind(ps,"BOGUS","ARRIVED",ap,t,f,p,4));check(bound.isEmpty());
  try {AppointmentTransitionMutation.bind(ps,"CONFIRMED","ARRIVED",ap,t,f,null,4);throw new AssertionError();}catch(IllegalArgumentException ok){check(true);}
  try {AppointmentTransitionMutation.requireUpdated(0);throw new AssertionError();}catch(SQLException ok){check(true);}
  try {AppointmentTransitionMutation.requireUpdated(2);throw new AssertionError();}catch(SQLException ok){check(true);}
  AppointmentTransitionMutation.requireUpdated(1);check(true);
  System.out.println("PASS AppointmentTransitionMutationTest assertions="+assertions);
 }
}
