package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentEventIntegrityProofTest {
 static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("assertion "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static Connection db(AppointmentTenantContext scope,UUID appointment,UUID event,long revision,
                      String command,String previous,String actor,String audit,String outbox,int rows,boolean badBinding){
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->false;case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->{ok(AppointmentEventIntegrityProof.SQL.equals(a[0]));Map<Integer,Object> bound=new HashMap<>();
    ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
     public Object invoke(Object x,Method method,Object[] b){return switch(method.getName()){
      case "next"->++n<=rows;
      case "getString"->switch((Integer)b[0]){case 1->command;case 2->previous;case 3->actor;case 4->audit;case 5->outbox;default->null;};
      default->zero(method.getReturnType());};}});
    yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(x,method,b)->{
     if(method.getName().startsWith("set")){bound.put((Integer)b[0],b[1]);return null;}
     if(method.getName().equals("executeQuery")){
      if(!badBinding)ok(bound.equals(Map.of(1,scope.patientId(),2,event,3,scope.tenantId(),4,scope.facilityId(),5,appointment,6,revision)));
      return rs;
     }return zero(method.getReturnType());});}
   default->zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID id=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();var scope=new AppointmentTenantContext(id,id,id,id);
  String command="a".repeat(64),previous="0".repeat(64),actor="Abcdefghijklmnop";
  String digest=AppointmentAuditStore.digestFor(scope,app,2,previous,command,actor);
  AppointmentEventIntegrityProof.require(db(scope,app,event,2,command,previous,actor,digest,digest,1,false),scope,app,event,2,command);
  for(String[] mismatch:new String[][]{{"b".repeat(64),digest,digest},{command,"c".repeat(64),digest},{command,digest,"d".repeat(64)}}){
   try{AppointmentEventIntegrityProof.require(db(scope,app,event,2,mismatch[0],previous,actor,mismatch[1],mismatch[2],1,false),scope,app,event,2,command);throw new AssertionError("tampering accepted");}
   catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  }
  for(int count:new int[]{0,2}){
   try{AppointmentEventIntegrityProof.require(db(scope,app,event,2,command,previous,actor,digest,digest,count,false),scope,app,event,2,command);throw new AssertionError("cardinality accepted");}
   catch(SQLException ex){ok((count==0?"40001":"23514").equals(ex.getSQLState()));}
  }
  try{AppointmentEventIntegrityProof.require(db(scope,app,event,2,command,previous,actor,digest,digest,1,false),scope,app,event,1,command);throw new AssertionError();}
  catch(SQLException ex){ok("22023".equals(ex.getSQLState()));}
  System.out.println("PASS AppointmentEventIntegrityProofTest assertions="+checks);
 }
}
