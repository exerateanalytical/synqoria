package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentCommitProofTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static Connection db(List<String> queries,String fail,AppointmentTenantContext scope,UUID app,UUID event,String command,boolean tamper){
  String prev="0".repeat(64),actor="Abcdefghijklmnop";
  String audit=AppointmentAuditStore.digestFor(scope,app,2,prev,command,actor);
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->false;case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->{String sql=(String)a[0];queries.add(sql);Map<Integer,Object> bound=new HashMap<>();
    ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
     public Object invoke(Object x,Method method,Object[] b){return switch(method.getName()){
      case "next"->++n==1;
      case "getLong"->sql.equals(fail)?0L:1L;
      case "getString"->switch((Integer)b[0]){case 1->command;case 2->prev;case 3->actor;case 4->audit;case 5->tamper?"f".repeat(64):audit;default->null;};
      default->zero(method.getReturnType());};}});
    yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(x,method,b)->{
     if(method.getName().startsWith("set")){bound.put((Integer)b[0],b[1]);return null;}
     if(method.getName().equals("executeQuery")){
      if(sql.equals(AppointmentEventIntegrityProof.SQL))ok(bound.equals(Map.of(1,scope.patientId(),2,event,3,scope.tenantId(),4,scope.facilityId(),5,app,6,2L)));
      if(sql.equals(AppointmentCommitProof.IDEMPOTENCY_WITH_DIGEST))ok(command.equals(bound.get(8)));
      return rs;
     }return zero(method.getReturnType());});}
   default->zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),p=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,a,p);String command="a".repeat(64);List<String> queries=new ArrayList<>();
  AppointmentCommitProof.require(db(queries,"",scope,app,event,command,false),scope,app,event,2,"CONFIRMED",command);
  ok(queries.equals(List.of(AppointmentCommitProof.APPOINTMENT,AppointmentCommitProof.AUDIT,
   AppointmentCommitProof.OUTBOX,AppointmentCommitProof.IDEMPOTENCY_WITH_DIGEST,AppointmentEventIntegrityProof.SQL)));
  for(String failing:List.of(AppointmentCommitProof.APPOINTMENT,AppointmentCommitProof.AUDIT,
   AppointmentCommitProof.OUTBOX,AppointmentCommitProof.IDEMPOTENCY_WITH_DIGEST)){
   try{AppointmentCommitProof.require(db(new ArrayList<>(),failing,scope,app,event,command,false),scope,app,event,2,"CONFIRMED",command);throw new AssertionError();}
   catch(SQLException ex){ok("40001".equals(ex.getSQLState()));}
  }
  try{AppointmentCommitProof.require(db(new ArrayList<>(),"",scope,app,event,command,true),scope,app,event,2,"CONFIRMED",command);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  try{AppointmentCommitProof.require(db(new ArrayList<>(),"",scope,app,event,command,false),scope,app,event,2,"INVALID",command);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  System.out.println("PASS AppointmentCommitProofTest assertions="+checks);
 }
}
