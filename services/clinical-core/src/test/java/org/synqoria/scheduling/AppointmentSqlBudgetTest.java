package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentSqlBudgetTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
 static Connection db(List<String> bound,int mismatch,int rows,boolean canonicalSeconds){
  ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n<=rows;
    case "getString" -> {int i=(Integer)a[0];yield i==mismatch?"1ms":canonicalSeconds?Integer.parseInt(bound.get(i-1).replace("ms",""))/1000+"s":bound.get(i-1);}
    default -> zero(m.getReturnType());};}});
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().equals("setString")){bound.add((String)a[1]);return null;}
   if(m.getName().equals("executeQuery"))return rs;
   return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement" -> {ok(AppointmentSqlBudget.SQL.equals(a[0]));yield ps;}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception {
  List<String> b=new ArrayList<>();AppointmentSqlBudget.install(db(b,0,1,false),5000,15000,30000);
  ok(b.equals(List.of("5000ms","15000ms","30000ms")));
  AppointmentSqlBudget.install(db(new ArrayList<>(),0,1,true),5000,15000,30000);
  for(int i=1;i<=3;i++){
   try{AppointmentSqlBudget.install(db(new ArrayList<>(),i,1,false),5000,15000,30000);throw new AssertionError("mismatch accepted");}
   catch(SQLException ex){ok("42501".equals(ex.getSQLState()));}
  }
  for(int rows:new int[]{0,2}){
   try{AppointmentSqlBudget.install(db(new ArrayList<>(),0,rows,false),5000,15000,30000);throw new AssertionError("rows accepted");}
   catch(SQLException ex){ok("42501".equals(ex.getSQLState()));}
  }
  for(int[] values:new int[][]{{0,15000,30000},{5000,5000,30000},{5000,31000,35000},{5000,15000,10000}}){
   try{AppointmentSqlBudget.install(db(new ArrayList<>(),0,1,false),values[0],values[1],values[2]);throw new AssertionError();}
   catch(SQLException ex){ok("22023".equals(ex.getSQLState()));}
  }
  try{AppointmentSqlBudget.parseMillis("9999999999min");throw new AssertionError();}catch(SQLException expected){ok(true);}
  System.out.println("PASS AppointmentSqlBudgetTest assertions="+checks);
 }
}
