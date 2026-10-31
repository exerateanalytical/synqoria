package org.synqoria.scheduling;
import org.synqoria.security.*;import java.sql.*;import java.lang.reflect.*;import java.time.*;import java.util.*;
public final class AppointmentStatusReadServiceTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static final class Fixture {
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),p=UUID.randomUUID(),app=UUID.randomUUID();
  Instant now=Instant.parse("2026-10-30T12:00:00Z");
  TrustedAccessContext identity=new TrustedAccessContext(t,f,a,Set.of("appointment:read"),Set.of(p));
  TrustedClinicalAccessEvidence proof=new TrustedClinicalAccessEvidence(t,f,a,p,now.minusSeconds(10),now.plusSeconds(60),true,true,true,true,true,true,true,Set.of("appointment:read"));
  List<String> steps=new ArrayList<>();
  ResultSet rs(Object[][] data){return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n=-1;
   public Object invoke(Object proxy,Method m,Object[] args){return switch(m.getName()){
    case "next" -> ++n<data.length;
    case "getString" -> data[n][(Integer)args[0]-1];
    case "getLong" -> data[n][(Integer)args[0]-1];
    case "getBoolean" -> data[n][(Integer)args[0]-1];
    default -> zero(m.getReturnType());};}});}
  Connection db(Object[][] rows){
   Statement st=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{Statement.class},(x,m,args)->{
    if(m.getName().equals("executeQuery")){
     String sql=(String)args[0];steps.add(sql.contains("pg_class")?"table-proof":"role-proof");
     return sql.contains("pg_class")?rs(new Object[][]{
      {"synqoria_appointment_reservations_quarantine",true,true,"schema_owner"},
      {"synqoria_appointment_command_audit_quarantine",true,true,"schema_owner"},
      {"synqoria_appointment_outbox_quarantine",true,true,"schema_owner"},
      {"synqoria_appointment_idempotency_quarantine",true,true,"schema_owner"}})
      :rs(new Object[][]{{"synqoria_test_role",false,false}});
    }return zero(m.getReturnType());});
   return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(x,m,args)->switch(m.getName()){
    case "getAutoCommit" -> false;case "isReadOnly" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
    case "createStatement" -> st;
    case "prepareStatement" -> {
     String sql=(String)args[0];steps.add(sql.equals(AppointmentStatusReadService.SQL)?"read":sql.equals(AppointmentSqlBudget.SQL)?"budget":"scope");
     Map<Integer,Object> bound=new HashMap<>();
     yield (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(y,pm,pa)->{
      if(pm.getName().startsWith("set")){bound.put((Integer)pa[0],pa[1]);return null;}
      if(pm.getName().equals("executeQuery")){
       if(sql.equals(AppointmentStatusReadService.SQL)){
        ok(bound.equals(Map.of(1,app,2,t,3,f,4,p)));return rs(rows);}
       return sql.equals(AppointmentSqlBudget.SQL)?rs(new Object[][]{{bound.get(1),bound.get(2),bound.get(3)}}):rs(new Object[][]{{bound.get(1),bound.get(2),bound.get(3),bound.get(4)}});
      }return zero(pm.getReturnType());});}
    case "commit" -> {steps.add("commit");yield null;}
    case "rollback" -> {steps.add("rollback");yield null;}
    default -> zero(m.getReturnType());});
  }
 }
 public static void main(String[] args)throws Exception{
  Fixture f=new Fixture();
  var status=AppointmentStatusReadService.read(f.db(new Object[][]{{"CONFIRMED",2L}}),f.identity,f.proof,f.p,f.now,"synqoria_test_role",f.app);
  ok(status.isPresent());ok(status.get().revision()==2);ok(status.get().lifecycle().equals("CONFIRMED"));
  ok(f.steps.equals(List.of("role-proof","table-proof","scope","budget","read","commit")));
  Fixture empty=new Fixture();
  ok(AppointmentStatusReadService.read(empty.db(new Object[][]{}),empty.identity,empty.proof,empty.p,empty.now,"synqoria_test_role",empty.app).isEmpty());
  Fixture denied=new Fixture();
  try{AppointmentStatusReadService.read(denied.db(new Object[][]{}),denied.identity,denied.proof,UUID.randomUUID(),denied.now,"synqoria_test_role",denied.app);throw new AssertionError();}
  catch(SecurityException ex){ok(denied.steps.isEmpty());}
  Fixture invalid=new Fixture();
  try{AppointmentStatusReadService.read(invalid.db(new Object[][]{{"UNEXPECTED",2L}}),invalid.identity,invalid.proof,invalid.p,invalid.now,"synqoria_test_role",invalid.app);throw new AssertionError();}
  catch(SQLException ex){ok("23514".equals(ex.getSQLState()));ok(invalid.steps.contains("rollback"));}
  System.out.println("PASS AppointmentStatusReadServiceTest assertions="+checks);
 }
}
