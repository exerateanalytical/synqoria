package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentTableRoleFenceTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;return null;}
 static final String[] TABLES={"synqoria_appointment_reservations_quarantine","synqoria_appointment_command_audit_quarantine",
  "synqoria_appointment_outbox_quarantine","synqoria_appointment_idempotency_quarantine"};
 static Connection db(int count,boolean forced,boolean owned){
  ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n=-1;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n<count;
    case "getString" -> (Integer)a[0]==1?TABLES[n%4]:owned?"synqoria_test_role":"schema_owner";
    case "getBoolean" -> forced;
    default -> zero(m.getReturnType());};}});
  Statement stmt=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{Statement.class},(p,m,a)->{
   if(m.getName().equals("executeQuery")){ok(((String)a[0]).contains("pg_get_userbyid"));return rs;}return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->m.getName().equals("createStatement")?stmt:zero(m.getReturnType()));
 }
 public static void main(String[] args)throws Exception{
  AppointmentTableRoleFence.require(db(4,true,false),"synqoria_test_role");ok(true);
  for(int n:new int[]{0,3,5})try{AppointmentTableRoleFence.require(db(n,true,false),"synqoria_test_role");throw new AssertionError();}
   catch(SQLException ex){ok(true);}
  try{AppointmentTableRoleFence.require(db(4,false,false),"synqoria_test_role");throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  try{AppointmentTableRoleFence.require(db(4,true,true),"synqoria_test_role");throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  System.out.println("PASS AppointmentTableRoleFenceTest assertions="+checks);
 }
}
