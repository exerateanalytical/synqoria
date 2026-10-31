package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentReplayProofTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static Connection db(List<String> queries,String missing,AppointmentTenantContext scope,UUID app,String command,boolean tamper){
  String prev="0".repeat(64),actor="Abcdefghijklmnop";
  String digest=AppointmentAuditStore.digestFor(scope,app,2,prev,command,actor);
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->false;case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->{String sql=(String)a[0];queries.add(sql);
    ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
     public Object invoke(Object x,Method method,Object[] b){return switch(method.getName()){
      case "next"->++n==1;
      case "getLong"->sql.equals(missing)?0L:1L;
      case "getString"->switch((Integer)b[0]){case 1->command;case 2->prev;case 3->actor;case 4->digest;case 5->tamper?"e".repeat(64):digest;default->null;};
      default->zero(method.getReturnType());};}});
    yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(x,method,b)->{
     if(method.getName().startsWith("set"))return null;
     if(method.getName().equals("executeQuery"))return rs;
     return zero(method.getReturnType());});}
   default->zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),p=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,a,p);List<String> queries=new ArrayList<>();String command="a".repeat(64);
  AppointmentReplayProof.require(db(queries,"",scope,app,command,false),scope,app,event,2,command);
  ok(queries.equals(List.of(AppointmentCommitProof.AUDIT,AppointmentCommitProof.OUTBOX,AppointmentEventIntegrityProof.SQL)));
  for(String missing:List.of(AppointmentCommitProof.AUDIT,AppointmentCommitProof.OUTBOX)){
   try{AppointmentReplayProof.require(db(new ArrayList<>(),missing,scope,app,command,false),scope,app,event,2,command);throw new AssertionError();}
   catch(SQLException ex){ok("40001".equals(ex.getSQLState()));}
  }
  try{AppointmentReplayProof.require(db(new ArrayList<>(),"",scope,app,command,true),scope,app,event,2,command);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentReplayProof.require(db(new ArrayList<>(),"",scope,app,command,false),scope,app,event,2,"b".repeat(64));throw new AssertionError("replay command mismatch accepted");}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentReplayProof.require(db(new ArrayList<>(),"",scope,app,command,false),scope,app,event,1,command);throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  System.out.println("PASS AppointmentReplayProofTest assertions="+checks);
 }
}
