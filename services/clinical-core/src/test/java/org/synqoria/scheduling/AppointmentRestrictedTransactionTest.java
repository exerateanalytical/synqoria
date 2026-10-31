package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class AppointmentRestrictedTransactionTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
 static ResultSet tableRows(String role) {
  String[] tables={"synqoria_appointment_reservations_quarantine","synqoria_appointment_command_audit_quarantine",
   "synqoria_appointment_outbox_quarantine","synqoria_appointment_idempotency_quarantine"};
  return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n=-1;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n<4;
    case "getString" -> (Integer)a[0]==1?tables[n]:"schema_owner";
    case "getBoolean" -> true;
    default -> zero(m.getReturnType());};}});
 }
 static Connection db(List<String> events,String role,boolean bypass){
  ResultSet roleRs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++n==1;case "getString" -> role;case "getBoolean" -> bypass;default -> zero(m.getReturnType());};}});
  Map<Integer,String> scopeBound=new HashMap<>();
  ResultSet scopeRs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){case "next"->++n==1;case "getString"->scopeBound.get((Integer)a[0]);default->zero(m.getReturnType());};}});
  Statement st=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{Statement.class},(p,m,a)->{
   if(m.getName().equals("executeQuery")){
    if(((String)a[0]).contains("pg_class")) {events.add("table-proof");return tableRows(role);}
    events.add("role-check");return roleRs;}return zero(m.getReturnType());});
  PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
   if(m.getName().equals("setString")){scopeBound.put((Integer)a[0],(String)a[1]);return null;}
   if(m.getName().equals("executeQuery")){
    events.add("scope-installed");return scopeRs;}return zero(m.getReturnType());});
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit" -> false;case "isReadOnly" -> false;
   case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
   case "createStatement" -> st;
   case "prepareStatement" -> {
    String sql=(String)a[0];
    if(sql.equals(AppointmentSqlBudget.SQL)) {
     events.add("budget");
     Map<Integer,String> budgetBound=new HashMap<>();
     ResultSet budgetRs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n;
      public Object invoke(Object p2,Method m2,Object[] a2){return switch(m2.getName()){case "next"->++n==1;case "getString"->budgetBound.get((Integer)a2[0]);default->zero(m2.getReturnType());};}});
     yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p2,m2,a2)->{
      if(m2.getName().equals("setString")){budgetBound.put((Integer)a2[0],(String)a2[1]);return null;}
      if(m2.getName().equals("executeQuery"))return budgetRs;
      return zero(m2.getReturnType());});
    }
    ok(sql.contains("set_config('app.patient_id'"));yield ps;}
   case "commit" -> {events.add("commit");yield null;}
   case "rollback" -> {events.add("rollback");yield null;}
   default -> zero(m.getReturnType());});
 }
 public static void main(String[] args)throws Exception{
  UUID id=UUID.randomUUID();var scope=new AppointmentTenantContext(id,id,id,id);
  List<String> events=new ArrayList<>();
  String result=AppointmentRestrictedTransaction.execute(db(events,"synqoria_test_role",false),scope,"synqoria_test_role",c->{events.add("work");return "ok";});
  ok("ok".equals(result));ok(events.equals(List.of("role-check","table-proof","scope-installed","budget","work","commit")));
  events.clear();
  try{AppointmentRestrictedTransaction.execute(db(events,"synqoria_test_role",false),scope,"synqoria_test_role",c->{events.add("work");throw new SQLException("synthetic fail");});throw new AssertionError();}
  catch(SQLException ex){ok("synthetic fail".equals(ex.getMessage()));}
  ok(events.equals(List.of("role-check","table-proof","scope-installed","budget","work","rollback")));
  events.clear();
  try{AppointmentRestrictedTransaction.execute(db(events,"synqoria_test_role",true),scope,"synqoria_test_role",c->{events.add("work");return null;});throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  ok(events.equals(List.of("role-check","rollback")));
  events.clear();
  try{AppointmentRestrictedTransaction.execute(db(events,"unexpected_role",false),scope,"synqoria_test_role",c->{events.add("work");return null;});throw new AssertionError();}
  catch(SQLException ex){ok(true);}
  ok(!events.contains("work"));
  System.out.println("PASS AppointmentRestrictedTransactionTest assertions="+checks);
 }
}
