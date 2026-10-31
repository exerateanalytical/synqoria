package org.synqoria.scheduling;

import org.synqoria.security.*;
import java.sql.*;import java.lang.reflect.*;import java.time.*;import java.util.*;

public final class AppointmentTransitionServiceTest {
 static byte[] key(){byte[] b=new byte[32];Arrays.fill(b,(byte)0x3A);return b;}
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static final class Fixture {
  final UUID t=UUID.randomUUID(),f=UUID.randomUUID(),actor=UUID.randomUUID(),patient=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
  final Instant now=Instant.parse("2026-10-30T10:00:00Z");
  final TrustedAccessContext identity=new TrustedAccessContext(t,f,actor,Set.of("appointment:manage"),Set.of(patient));
  final TrustedClinicalAccessEvidence proof=new TrustedClinicalAccessEvidence(t,f,actor,patient,now.minusSeconds(5),now.plusSeconds(60),true,true,true,true,true,true,true,Set.of("appointment:manage"));
  final AppointmentTransitionCommand cmd=new AppointmentTransitionCommand(app,"CONFIRMED",1,"aLongUniqueKey123456789",event);
  final List<String> steps=new ArrayList<>();
  Connection db(boolean replay,boolean outboxFailure) {return db(replay,outboxFailure,"REQUESTED",1L,1,false,false);}
  Connection db(boolean replay,boolean outboxFailure,String lifecycle,long revision,int updated,boolean auditFailure,boolean digestConflict) {
   String digest=cmd.digest(new AppointmentTenantContext(t,f,actor,patient));
   return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
    case "getAutoCommit" -> false;case "isReadOnly" -> false;case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
    case "createStatement" -> {
     Statement stmt=(Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class[]{Statement.class},(sp,sm,sa)->{
      if(sm.getName().equals("executeQuery")){
       if(((String)sa[0]).contains("pg_class")) {steps.add("table-proof");return rows(new Object[][]{
        {"synqoria_appointment_reservations_quarantine",true,true,"schema_owner"},
        {"synqoria_appointment_command_audit_quarantine",true,true,"schema_owner"},
        {"synqoria_appointment_outbox_quarantine",true,true,"schema_owner"},
        {"synqoria_appointment_idempotency_quarantine",true,true,"schema_owner"}});}
       steps.add("role");return rows(new Object[][]{{"synqoria_test_role",false,false}});}
      return zero(sm.getReturnType());});yield stmt;}
    case "prepareStatement" -> {
     String sql=(String)a[0];String label=label(sql);Map<Integer,Object> bound=new HashMap<>();
     PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(pp,pm,pa)->{
      if(pm.getName().startsWith("set")){bound.put((Integer)pa[0],pa[1]);return null;}
      if(pm.getName().equals("executeQuery")){
       steps.add(label);
       return switch(label){
        case "scope" -> rows(new Object[][]{{bound.get(1),bound.get(2),bound.get(3),bound.get(4)}});
        case "budget" -> rows(new Object[][]{{bound.get(1),bound.get(2),bound.get(3)}});
        case "idempotency-select" -> rows(new Object[][]{{patient,digestConflict?"c".repeat(64):digest,replay?"COMMITTED":"PENDING",app,replay?2L:0L,replay?event:null}});
        case "lock" -> rows(new Object[][]{{replay?"CONFIRMED":lifecycle,replay?2L:revision}});
        case "audit-previous" -> rows(new Object[][]{});
        case "audit-integrity" -> {if(!replay)yield rows(new Object[][]{});var scope=new AppointmentTenantContext(t,f,actor,patient);String pseudonym=AppointmentActorPseudonym.derive(t,actor,key());String prev="0".repeat(64);String auditDigest=AppointmentAuditStore.digestFor(scope,app,2,prev,digest,pseudonym);yield rows(new Object[][]{{2L,prev,digest,pseudonym,auditDigest,auditDigest}});}
        case "proof-appointment","proof-audit","proof-outbox","proof-idempotency" -> rows(new Object[][]{{1L}});
        case "proof-event-link" -> {var scope=new AppointmentTenantContext(t,f,actor,patient);String pseudonym=AppointmentActorPseudonym.derive(t,actor,key());String prev="0".repeat(64);String auditDigest=AppointmentAuditStore.digestFor(scope,app,2,prev,digest,pseudonym);yield rows(new Object[][]{{digest,prev,pseudonym,auditDigest,auditDigest}});}
        default -> throw new AssertionError("unexpected query "+label);};}
      if(pm.getName().equals("executeUpdate")){
       steps.add(label);
       if(label.equals("outbox-insert")&&outboxFailure)throw new SQLException("synthetic outbox failure", "23505");
       if(label.equals("audit-insert")&&auditFailure)throw new SQLException("synthetic audit failure", "23505");
       if(label.equals("update"))return updated;
       if(label.equals("idempotency-insert"))return replay?0:1;
       return 1;}
      return zero(pm.getReturnType());});yield ps;}
    case "commit" -> {steps.add("commit");yield null;}
    case "rollback" -> {steps.add("rollback");yield null;}
    default -> zero(m.getReturnType());});
  }
  static String label(String sql){
   if(sql.contains("set_config('app.tenant_id'"))return "scope";
   if(sql.equals(AppointmentSqlBudget.SQL))return "budget";
   if(sql.equals(AppointmentIdempotencyStore.INSERT))return "idempotency-insert";
   if(sql.equals(AppointmentIdempotencyStore.SELECT))return "idempotency-select";
   if(sql.equals(AppointmentIdempotencyStore.COMPLETE))return "idempotency-complete";
   if(sql.equals(AppointmentLockedSnapshot.SQL))return "lock";
   if(sql.equals(AppointmentTransitionMutation.SQL))return "update";
   if(sql.equals(AppointmentAuditStore.PREVIOUS))return "audit-previous";
   if(sql.equals(AppointmentAuditIntegrityReader.SQL))return "audit-integrity";
   if(sql.equals(AppointmentAuditStore.INSERT))return "audit-insert";
   if(sql.equals(AppointmentOutboxStore.INSERT))return "outbox-insert";
   if(sql.equals(AppointmentCommitProof.APPOINTMENT))return "proof-appointment";
   if(sql.equals(AppointmentCommitProof.AUDIT))return "proof-audit";
   if(sql.equals(AppointmentCommitProof.OUTBOX))return "proof-outbox";
   if(sql.equals(AppointmentCommitProof.IDEMPOTENCY)||sql.equals(AppointmentCommitProof.IDEMPOTENCY_WITH_DIGEST))return "proof-idempotency";
   if(sql.equals(AppointmentEventIntegrityProof.SQL))return "proof-event-link";
   throw new AssertionError("unknown SQL: "+sql);
  }
  static ResultSet rows(Object[][] data){return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int index=-1;
   public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
    case "next" -> ++index<data.length;
    case "getObject" -> data[index][(Integer)a[0]-1];
    case "getString" -> String.valueOf(data[index][(Integer)a[0]-1]);
    case "getLong" -> (Long)data[index][(Integer)a[0]-1];
    case "getBoolean" -> (Boolean)data[index][(Integer)a[0]-1];
    case "wasNull" -> data[index].length>=6 && "PENDING".equals(data[index][2]);
    default -> zero(m.getReturnType());};}});}
 }
 public static void main(String[] args)throws Exception{
  Fixture success=new Fixture();
  var result=AppointmentTransitionService.execute(success.db(false,false),success.identity,success.proof,
   success.patient,success.now,"synqoria_test_role",key(),success.cmd);
  ok(result.revision()==2);ok(result.eventId().equals(success.event));ok(!result.replay());
  ok(success.steps.equals(List.of("role","table-proof","scope","budget","idempotency-insert","idempotency-select","lock","lock","audit-integrity","update","audit-previous","audit-insert","outbox-insert","idempotency-complete","proof-appointment","proof-audit","proof-outbox","proof-idempotency","proof-event-link","commit")));
  Fixture replay=new Fixture();
  var replayed=AppointmentTransitionService.execute(replay.db(true,false),replay.identity,replay.proof,
   replay.patient,replay.now,"synqoria_test_role",key(),replay.cmd);
  ok(replayed.replay());ok(replayed.revision()==2);
  ok(replay.steps.equals(List.of("role","table-proof","scope","budget","idempotency-insert","idempotency-select","proof-audit","proof-outbox","proof-event-link","lock","audit-integrity","commit")));
  Fixture fail=new Fixture();
  try{AppointmentTransitionService.execute(fail.db(false,true),fail.identity,fail.proof,fail.patient,
   fail.now,"synqoria_test_role",key(),fail.cmd);throw new AssertionError();}
  catch(SQLException ex){ok("23505".equals(ex.getSQLState()));}
  ok(fail.steps.contains("rollback"));ok(!fail.steps.contains("commit"));ok(!fail.steps.contains("idempotency-complete"));
  Fixture denied=new Fixture();
  var wrong=new TrustedAccessContext(denied.t,denied.f,denied.actor,Set.of(),Set.of(denied.patient));
  try{AppointmentTransitionService.execute(denied.db(false,false),wrong,denied.proof,denied.patient,
   denied.now,"synqoria_test_role",key(),denied.cmd);throw new AssertionError();}
  catch(SecurityException ex){ok(denied.steps.isEmpty());}
  System.out.println("PASS AppointmentTransitionServiceTest assertions="+checks);
 }
}
