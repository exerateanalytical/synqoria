package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentJdbcScopeTest {
 static int checks;static void ok(boolean v){checks++;if(!v)throw new AssertionError("assertion "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
 static Connection db(List<String> bound,int mismatch,int rows){
  ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n<=rows;
    case "getString" -> mismatch==(Integer)a[0]?"unexpected-scope":bound.get((Integer)a[0]-1);
    default -> zero(m.getReturnType());};}});
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().equals("setString")){bound.add((String)a[1]);return null;}
   if(m.getName().equals("executeQuery"))return rs;
   return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement" -> {ok(AppointmentJdbcScope.SQL.equals(a[0]));yield ps;}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception {
  UUID id=UUID.randomUUID();var ctx=new AppointmentTenantContext(id,id,id,id);
  List<String> bound=new ArrayList<>();AppointmentJdbcScope.install(db(bound,0,1),ctx);
  ok(bound.equals(List.of(id.toString(),id.toString(),id.toString(),id.toString())));
  for(int mismatch=1;mismatch<=4;mismatch++){
   try{AppointmentJdbcScope.install(db(new ArrayList<>(),mismatch,1),ctx);throw new AssertionError("mismatch accepted");}
   catch(SQLException ex){ok("42501".equals(ex.getSQLState()));}
  }
  for(int rows:new int[]{0,2}){
   try{AppointmentJdbcScope.install(db(new ArrayList<>(),0,rows),ctx);throw new AssertionError("cardinality accepted");}
   catch(SQLException ex){ok("42501".equals(ex.getSQLState()));}
  }
  System.out.println("PASS AppointmentJdbcScopeTest assertions="+checks);
 }
}
