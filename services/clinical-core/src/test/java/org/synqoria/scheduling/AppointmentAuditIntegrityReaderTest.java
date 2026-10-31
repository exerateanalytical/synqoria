package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentAuditIntegrityReaderTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
 static Connection db(Object[][] data,long revision){
  ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int i=-1;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++i<data.length;case "getLong" -> data[i][(Integer)a[0]-1];
    case "getString" -> data[i][(Integer)a[0]-1];default -> zero(m.getReturnType());};}});
  ResultSet lockRs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n==1;case "getString" -> "CONFIRMED";case "getLong" -> revision;case "wasNull" -> false;
    default -> zero(m.getReturnType());};}});
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().startsWith("set"))return null;
   if(m.getName().equals("executeQuery"))return rs;return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement" -> {
    if(((String)a[0]).equals(AppointmentLockedSnapshot.SQL)){
     yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(x,m2,b)->{
      if(m2.getName().startsWith("set"))return null;
      if(m2.getName().equals("executeQuery"))return lockRs;
      return zero(m2.getReturnType());});
    }
    ok(((String)a[0]).contains("a.tenant_id=? AND a.facility_id=? AND a.appointment_id=?"));yield ps;}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),actor=UUID.randomUUID(),pat=UUID.randomUUID(),app=UUID.randomUUID();
  var scope=new AppointmentTenantContext(t,f,actor,pat);
  String d0="0".repeat(64),c1="a".repeat(64),c2="b".repeat(64),a="pseudonym_123";
  String d1=AppointmentAuditStore.digestFor(scope,app,2,d0,c1,a);
  String d2=AppointmentAuditStore.digestFor(scope,app,3,d1,c2,a);
  Object[][] good={{2L,d0,c1,a,d1,d1},{3L,d1,c2,a,d2,d2}};
  var result=AppointmentAuditIntegrityReader.verify(db(good,3),scope,app);
  ok(result.entries()==2);ok(result.finalDigest().equals(d2));
  Object[][] tampered={{2L,d0,c1,a,d1,d1},{3L,d1,c2,a,"f".repeat(64),d2}};
  try{AppointmentAuditIntegrityReader.verify(db(tampered,3),scope,app);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  Object[][] gap={{2L,d0,c1,a,d1,d1},{4L,d1,c2,a,d2,d2}};
  try{AppointmentAuditIntegrityReader.verify(db(gap,3),scope,app);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  var empty=AppointmentAuditIntegrityReader.verify(db(new Object[][]{},1),scope,app);
  ok(empty.entries()==0);ok(empty.finalDigest().equals(d0));
  for(Object[][] incomplete:new Object[][][]{new Object[][]{},new Object[][]{{2L,d0,c1,a,d1,d1}}}){
   try{AppointmentAuditIntegrityReader.verify(db(incomplete,3),scope,app);throw new AssertionError("truncated audit accepted");}
   catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  }
  for(String invalid:new String[]{null,"f".repeat(64)}){
   try{AppointmentAuditIntegrityReader.verify(db(new Object[][]{{2L,d0,c1,a,d1,invalid}},2),scope,app);throw new AssertionError("missing/mismatched outbox accepted");}
   catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
  }
  System.out.println("PASS AppointmentAuditIntegrityReaderTest assertions="+checks);
 }
}
