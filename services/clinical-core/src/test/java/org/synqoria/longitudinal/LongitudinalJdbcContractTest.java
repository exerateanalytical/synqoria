package org.synqoria.longitudinal;

import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

/** Synthetic JDBC protocol recorder. NOT a substitute for PostgreSQL, RLS or deployment qualification. */
public final class LongitudinalJdbcContractTest {
 private LongitudinalJdbcContractTest(){}
 private static int checks;
 private static void check(boolean b){checks++;if(!b)throw new AssertionError("JDBC contract assertion "+checks);}
 private static void denied(Runnable r){checks++;try{r.run();throw new AssertionError("unsafe operation accepted");}
  catch(SecurityException|IllegalStateException expected){/* rejected */}}
 private static Object fallback(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;
  if(type==long.class)return 0L;if(type==double.class)return 0d;return null;}
 @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 private static Object objectMethod(Object proxy,Method method,Object[] args){return switch(method.getName()){
  case "toString" -> "SyntheticJdbc("+proxy.getClass().getInterfaces()[0].getSimpleName()+")";
  case "equals" -> proxy==args[0];case "hashCode" -> System.identityHashCode(proxy);default -> null;};}
 public static final class FakePg {
  final Scope scope; final SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");
  String keyId="clinical-key-v1"; State current;
  int commits,rollbacks,connections,statements,caseUpdates,eventInserts,receiptInserts,outboxInserts,scopeInstalls;
  boolean scopeMismatch=false,autoCommitInitiallyFalse=false,missingCase=false,duplicateCase=false,corruptAudit=false,
   missingReceipt=false,missingOutbox=false,wrongHead=false,casConflict=false,outboxUnavailable=false,
   malformedCiphertext=false,duplicateEvent=false,sqlFailure=false;
  FakePg(State initial){scope=initial.scope;current=new State(initial);}
  final LongitudinalEnvelopeCrypto.KeyResolver keys=new LongitudinalEnvelopeCrypto.KeyResolver(){
   public String activeId(){return keyId;}public SecretKey activeKey(){return key;}
   public SecretKey resolve(String id){if(!id.equals("clinical-key-v1")&&!id.equals("clinical-key-v2"))throw new SecurityException("key unknown");return key;}
  };
  DataSource source(){return proxy(DataSource.class,(proxy,m,args)->{
   if(m.getDeclaringClass()==Object.class)return objectMethod(proxy,m,args);
   if(m.getName().equals("getConnection")){connections++;return connection();}
   return fallback(m.getReturnType());
  });}
  Connection connection(){
   class Session {boolean autoCommit=!autoCommitInitiallyFalse;boolean closed,failed;State next;
    int updated,events,receipts,outbox;}
   final Session tx=new Session();
   return proxy(Connection.class,(p,m,a)->{
    String name=m.getName();if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
    switch(name){
     case "getAutoCommit" -> {return tx.autoCommit;}
     case "setAutoCommit" -> {tx.autoCommit=(boolean)a[0];return null;}
     case "setTransactionIsolation" -> {check(Objects.equals(a[0],Connection.TRANSACTION_READ_COMMITTED));return null;}
     case "createStatement" -> {return statement();}
     case "prepareStatement" -> {return statement((String)a[0],tx);}
     case "commit" -> {if(tx.failed)throw new SQLException("transaction aborted");commits++;if(tx.next!=null){current=tx.next;caseUpdates+=tx.updated;eventInserts+=tx.events;receiptInserts+=tx.receipts;outboxInserts+=tx.outbox;}return null;}
     case "rollback" -> {rollbacks++;tx.next=null;return null;}
     case "close" -> {tx.closed=true;return null;}
     case "isClosed" -> {return tx.closed;}
    }
    return fallback(m.getReturnType());
   });
  }
  Statement statement(){return proxy(Statement.class,(p,m,a)->{
   if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
   if(m.getName().equals("execute")){check(((String)a[0]).startsWith("SET LOCAL "));return true;}
   return fallback(m.getReturnType());
  });}
  PreparedStatement statement(String sql,Object txObject){
   // The session is retained inside the mutable handler through reflection-free lambdas.
   final Map<Integer,Object> params=new HashMap<>();
   return proxy(PreparedStatement.class,(p,m,a)->{
    String name=m.getName();if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
    switch(name){
     case "setQueryTimeout" -> {check((int)a[0]>0);return null;}
     case "setString","setObject","setLong","setBytes","setTimestamp","setInt" -> {params.put((int)a[0],a[1]);return null;}
     case "executeQuery" -> {statements++;if(sqlFailure)throw new SQLException("synthetic query unavailable");return results(sql,params);}
     case "executeUpdate" -> {statements++;if(sqlFailure)throw new SQLException("synthetic DB failure");
      if(sql.contains("INSERT INTO synqoria_longitudinal_cases_quarantine")){
       if(current!=null)return 0;
       byte[] cipher=(byte[])params.get(6);String id=(String)params.get(5);
       byte[] clear=LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(scope,0,id),
         new LongitudinalEnvelopeCrypto.Envelope(id,cipher),key);
       try{State next=LongitudinalSnapshotCodec.decode(clear);set(txObject,"next",next);set(txObject,"updated",1);}
       finally{Arrays.fill(clear,(byte)0);}return 1;
      }
      if(sql.contains("UPDATE synqoria_longitudinal_cases_quarantine")){
       if(casConflict)return 0;
       byte[] cipher=(byte[])params.get(3);String id=(String)params.get(2);
       byte[] clear=LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(scope,(Long)params.get(1),id),
           new LongitudinalEnvelopeCrypto.Envelope(id,cipher),key);
       try{State next=LongitudinalSnapshotCodec.decode(clear);set(txObject,"next",next);set(txObject,"updated",1);}finally{Arrays.fill(clear,(byte)0);}return 1;
      }
      if(sql.contains("INSERT INTO synqoria_longitudinal_events_quarantine")){set(txObject,"events",1);return 1;}
      if(sql.contains("INSERT INTO synqoria_longitudinal_receipts_quarantine")){set(txObject,"receipts",1);return 1;}
      if(sql.contains("INSERT INTO synqoria_longitudinal_outbox_quarantine")){
       if(outboxUnavailable)throw new SQLException("synthetic outbox unavailable");set(txObject,"outbox",1);return 1;}
      throw new SQLException("unexpected INSERT/UPDATE");}
    }
    return fallback(m.getReturnType());
   });
  }
  private static void set(Object session,String field,Object value){try{
   Field f=session.getClass().getDeclaredField(field);f.setAccessible(true);f.set(session,value);
  }catch(ReflectiveOperationException e){throw new AssertionError("synthetic fixture",e);}}
  ResultSet results(String sql,Map<Integer,Object> args){
   List<Object[]> rows=new ArrayList<>();
   if(sql.contains("set_config('app.tenant_id'")){
    scopeInstalls++;String[] values={scope.tenant().toString(),scope.facility().toString(),scope.patient().toString(),scope.encounter().toString()};
    for(int i=0;i<4;i++)check(Objects.equals(args.get(i+1),values[i]));
    if(scopeMismatch)values[2]=UUID.randomUUID().toString();rows.add(values);
   }else if(sql.startsWith("SELECT revision,key_id,encrypted_snapshot,audit_head")){
    if(!missingCase&&Objects.equals(args.get(1),scope.tenant())&&Objects.equals(args.get(2),scope.facility())&&
       Objects.equals(args.get(3),scope.patient())&&Objects.equals(args.get(4),scope.encounter())){
     byte[] clear=LongitudinalSnapshotCodec.encode(current);LongitudinalEnvelopeCrypto.Envelope encrypted;
     try{encrypted=LongitudinalEnvelopeCrypto.seal(new LongitudinalEnvelopeCrypto.Context(scope,current.revision,keyId),clear,key);}finally{Arrays.fill(clear,(byte)0);}
     byte[] cipher=encrypted.value();if(malformedCiphertext)cipher[cipher.length-1]^=1;
     String head=current.events.isEmpty()?"0".repeat(64):current.events.get(current.events.size()-1).hash();
     if(wrongHead)head="9".repeat(64);
     Object[] result={current.revision,keyId,cipher,head};rows.add(result);if(duplicateCase)rows.add(result);
    }
   }else if(sql.startsWith("SELECT e.revision,e.command_id")){
    for(Event e:current.events){Receipt r=current.receipts.get(e.commandId());
     Object[] result={e.revision(),e.commandId(),e.action(),e.actor(),Timestamp.from(e.at()),e.previousHash(),
      e.stateDigest(),corruptAudit?"f".repeat(64):e.hash(),missingReceipt?null:r.fingerprint(),
      missingReceipt?null:e.hash(),missingOutbox?null:e.hash()};rows.add(result);if(duplicateEvent)rows.add(result);
    }
   }else throw new AssertionError("unexpected query: "+sql);
   final int[] pos={-1};
   return proxy(ResultSet.class,(p,m,a)->{
    String name=m.getName();if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
    if(name.equals("next")){pos[0]++;return pos[0]<rows.size();}
    if(name.equals("getString")||name.equals("getObject")||name.equals("getBytes")||name.equals("getTimestamp")||name.equals("getLong")){
     Object x=rows.get(pos[0])[(int)a[0]-1];if(name.equals("getLong"))return ((Number)x).longValue();return x;
    }
    return fallback(m.getReturnType());
   });
  }
  LongitudinalJdbcPersistence adapter(){return new LongitudinalJdbcPersistence(source(),keys);}
 }
 static State initial(){var f=new ClinicalVerticalBatchTest.F();f.active();return f.state();}
 static void read(){var f=new FakePg(initial());State s=f.adapter().transaction(f.scope,tx->tx.loadLocked());
  check(s.revision==f.current.revision&&s.scope.equals(f.scope));check(f.commits==1&&f.rollbacks==0&&f.scopeInstalls==1);
 }
 static void write(){var f=new FakePg(initial());Actor actor=new Actor(ClinicalVerticalBatchTest.A,Set.of("encounter.write"));
  long before=f.current.revision;var r=EncounterActions.run(new ClinicalCaseCore(f.adapter()),f.scope,actor,
   new Command(UUID.randomUUID(),before,Instant.parse("2026-10-31T09:30:00Z")),"encounter.record_room",Map.of("room","Ward 12"));
  check(r.revision()==before+1&&!r.replay());check(f.commits==1&&f.rollbacks==0);
  check(f.caseUpdates==1&&f.eventInserts==1&&f.receiptInserts==1&&f.outboxInserts==1);
  check(f.current.room.equals("Ward 12")&&auditValid(f.current));
  State reread=f.adapter().transaction(f.scope,tx->tx.loadLocked());check(reread.room.equals("Ward 12"));
 }
 static void replay(){var f=new FakePg(initial());Actor a=new Actor(ClinicalVerticalBatchTest.A,Set.of("encounter.write"));
  Command cmd=new Command(UUID.randomUUID(),f.current.revision,Instant.parse("2026-10-31T09:30:00Z"));
  var core=new ClinicalCaseCore(f.adapter());var one=EncounterActions.run(core,f.scope,a,cmd,"encounter.record_room",Map.of("room","Ward 4"));
  var two=EncounterActions.run(core,f.scope,a,cmd,"encounter.record_room",Map.of("room","Ward 4"));
  check(!one.replay()&&two.replay()&&one.revision()==two.revision());check(f.caseUpdates==1&&f.outboxInserts==1);
 }
 static void rollback(){var f=new FakePg(initial());f.outboxUnavailable=true;Actor a=new Actor(ClinicalVerticalBatchTest.A,Set.of("encounter.write"));
  long original=f.current.revision;
  denied(()->EncounterActions.run(new ClinicalCaseCore(f.adapter()),f.scope,a,
   new Command(UUID.randomUUID(),original,Instant.parse("2026-10-31T09:30:00Z")),"encounter.record_room",Map.of("room","Ward 8")));
  check(f.rollbacks==1&&f.commits==0&&f.current.revision==original&&f.outboxInserts==0);
 }
 static void conflict(){var f=new FakePg(initial());f.casConflict=true;Actor a=new Actor(ClinicalVerticalBatchTest.A,Set.of("encounter.write"));
  denied(()->EncounterActions.run(new ClinicalCaseCore(f.adapter()),f.scope,a,
   new Command(UUID.randomUUID(),f.current.revision,Instant.parse("2026-10-31T09:30:00Z")),"encounter.record_room",Map.of("room","Ward 9")));
  check(f.rollbacks==1&&f.commits==0&&f.caseUpdates==0);
 }
 static void failure(String name){var f=new FakePg(initial());switch(name){
  case "rls-no-row" -> f.missingCase=true;case "scope-echo" -> f.scopeMismatch=true;
  case "duplicate-case" -> f.duplicateCase=true;case "audit-corruption" -> f.corruptAudit=true;
  case "missing-receipt" -> f.missingReceipt=true;case "missing-outbox" -> f.missingOutbox=true;
  case "wrong-head" -> f.wrongHead=true;case "ciphertext" -> f.malformedCiphertext=true;
  case "duplicate-event" -> f.duplicateEvent=true;case "connection-unmanaged" -> f.autoCommitInitiallyFalse=true;
  case "sql-failure" -> f.sqlFailure=true;
  default -> throw new IllegalArgumentException(name);
 }
  if(name.equals("rls-no-row")){check(f.adapter().transaction(f.scope,tx->tx.loadLocked())==null);return;}
  denied(()->f.adapter().transaction(f.scope,tx->tx.loadLocked()));
  check(f.commits==0||name.equals("connection-unmanaged"));
 }
 static void provision(){var f=new FakePg(new State(initial().scope));f.current=null;
  var now=Instant.parse("2026-10-31T09:30:00Z");UUID actor=ClinicalVerticalBatchTest.A;
  var identity=new org.synqoria.security.TrustedAccessContext(f.scope.tenant(),f.scope.facility(),actor,
   Set.of("encounter.provision"),Set.of(f.scope.patient()));
  var proof=new org.synqoria.security.TrustedClinicalAccessEvidence(f.scope.tenant(),f.scope.facility(),actor,
   f.scope.patient(),now.minusSeconds(10),now.plusSeconds(60),true,true,true,true,true,true,true,Set.of("encounter.provision"));
  var target=new LongitudinalJdbcProvisioning(f.source(),f.keys);
  target.initialize(identity,proof,f.scope,now);
  check(f.current!=null&&f.current.revision==0&&f.current.stage==Stage.PLANNED&&f.commits==1);
  denied(()->target.initialize(identity,proof,f.scope,now));check(f.rollbacks==1);
 }
 static void provisionDenied(){var f=new FakePg(new State(initial().scope));f.current=null;
  var now=Instant.parse("2026-10-31T09:30:00Z");UUID actor=ClinicalVerticalBatchTest.A;
  var identity=new org.synqoria.security.TrustedAccessContext(f.scope.tenant(),f.scope.facility(),actor,
   Set.of("encounter.provision"),Set.of(f.scope.patient()));
  var proof=new org.synqoria.security.TrustedClinicalAccessEvidence(f.scope.tenant(),f.scope.facility(),actor,
   f.scope.patient(),now.minusSeconds(10),now.plusSeconds(60),true,true,true,true,false,true,true,Set.of("encounter.provision"));
  denied(()->new LongitudinalJdbcProvisioning(f.source(),f.keys).initialize(identity,proof,f.scope,now));
  check(f.connections==0&&f.commits==0);
 }
 static final List<String> CASES=List.of("read","write","replay","atomic-rollback","cas-conflict","provision","provision-denied",
  "rls-no-row","scope-echo","duplicate-case","audit-corruption","missing-receipt","missing-outbox",
  "wrong-head","ciphertext","duplicate-event","connection-unmanaged","sql-failure");
 public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("supply scenario");
  if(args[0].equals("all")){for(String n:CASES)run(n);}else run(args[0]);
  System.out.println("PASS JDBC "+args[0]+" ("+checks+" assertions, simulated PostgreSQL protocol only)");
 }
 static void run(String name){switch(name){case "read"->read();case "write"->write();case "replay"->replay();case "atomic-rollback"->rollback();case "cas-conflict"->conflict();case "provision"->provision();case "provision-denied"->provisionDenied();default->failure(name);}}
}
