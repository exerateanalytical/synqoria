package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentOutboxStoreTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==int.class)return 0;if(t==boolean.class)return false;return null;}
 static Connection db(Map<Integer,Object> bound,int updated){
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().startsWith("set")){bound.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeUpdate"))return updated;return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement" -> {ok(((String)a[0]).equals(AppointmentOutboxStore.INSERT));yield ps;}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),pat=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,a,pat);Map<Integer,Object> bound=new HashMap<>();
  AppointmentOutboxStore.append(db(bound,1),scope,app,event,5,AppointmentOutboxStore.EVENT_TYPE,"a".repeat(64));
  ok(bound.size()==7);ok(bound.get(1).equals(event));ok(bound.get(2).equals(t));ok(bound.get(3).equals(f));
  ok(bound.get(4).equals(app));ok(bound.get(5).equals(5L));ok(bound.get(6).equals(AppointmentOutboxStore.EVENT_TYPE));
  ok(bound.get(7).equals("a".repeat(64)));
  try{AppointmentOutboxStore.append(db(new HashMap<>(),1),scope,app,event,5,"appointment.patient_name","a".repeat(64));throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  try{AppointmentOutboxStore.append(db(new HashMap<>(),1),scope,app,event,5,AppointmentOutboxStore.EVENT_TYPE,"not digest");throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  try{AppointmentOutboxStore.append(db(new HashMap<>(),0),scope,app,event,5,AppointmentOutboxStore.EVENT_TYPE,"a".repeat(64));throw new AssertionError();}
  catch(SQLException ex){ok("40001".equals(ex.getSQLState()));}
  System.out.println("PASS AppointmentOutboxStoreTest assertions="+checks);
 }
}
