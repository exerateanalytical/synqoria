package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentAuditStoreTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==int.class)return 0;if(t==long.class)return 0L;if(t==boolean.class)return false;return null;}
 static Connection db(long priorRevision,String priorDigest,List<String> statements,Map<Integer,Object> bound){
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement" -> {
    String sql=(String)a[0];statements.add(sql);
    ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
     public Object invoke(Object x,Method method,Object[] b){return switch(method.getName()){
      case "next" -> priorRevision>0&&++n==1;
      case "getLong" -> priorRevision;
      case "getString" -> switch((Integer)b[0]){case 2->priorDigest;case 3->"0".repeat(64);case 4->"a".repeat(64);case 5->"pseudonym_123";default->null;};
      default -> zero(method.getReturnType());};}});
    yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(x,method,b)->{
     if(method.getName().startsWith("set")){if(sql.startsWith("INSERT"))bound.put((Integer)b[0],b[1]);return null;}
     if(method.getName().equals("executeQuery"))return rs;
     if(method.getName().equals("executeUpdate"))return 1;
     return zero(method.getReturnType());});}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),pat=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,a,pat);List<String> statements=new ArrayList<>();Map<Integer,Object> bound=new HashMap<>();
  String next=AppointmentAuditStore.append(db(0,null,statements,bound),scope,app,event,2,"a".repeat(64),"pseudonym_123");
  ok(next.matches("[a-f0-9]{64}"));ok(statements.size()==2);ok(statements.get(0).contains("FOR UPDATE"));
  ok(statements.get(1).contains("synqoria_appointment_command_audit_quarantine"));
  ok(bound.get(1).equals(event));ok(bound.get(4).equals(app));ok(bound.get(6).equals(2L));
  ok(bound.get(8).equals("0".repeat(64)));ok(bound.get(9).equals(next));
  statements.clear();bound.clear();
  String next2=AppointmentAuditStore.append(db(2,next,statements,bound),scope,app,event,3,"b".repeat(64),"pseudonym_123");
  ok(!next2.equals(next));ok(bound.get(8).equals(next));
  try{AppointmentAuditStore.append(db(2,"f".repeat(64),statements,bound),scope,app,event,3,"b".repeat(64),"pseudonym_123");throw new AssertionError("tampered previous audit accepted");}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentAuditStore.append(db(1,next,statements,bound),scope,app,event,4,"b".repeat(64),"pseudonym_123");throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentAuditStore.append(db(0,null,statements,bound),scope,app,event,2,"b".repeat(64),"actor name");throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  System.out.println("PASS AppointmentAuditStoreTest assertions="+checks);
 }
}
