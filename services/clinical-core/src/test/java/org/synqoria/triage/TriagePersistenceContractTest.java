package org.synqoria.triage;

import org.synqoria.security.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.*;

/** Synthetic JDBC protocol test. This is NOT a live PostgreSQL, KMS, OIDC, or clinical attestation. */
public final class TriagePersistenceContractTest {
 static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
 static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001"),FACILITY=UUID.fromString("00000000-0000-0000-0000-000000000002"),PATIENT=UUID.fromString("00000000-0000-0000-0000-000000000003"),ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000004"),CASE=UUID.fromString("00000000-0000-0000-0000-000000000005");
 static final Set<String> CAPS=Set.of("triage:intake","triage:manage");
 static final TrustedAccessContext ID=new TrustedAccessContext(TENANT,FACILITY,ACTOR,CAPS,Set.of(PATIENT));
 static final TrustedClinicalAccessEvidence PROOF=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(120),NOW.plusSeconds(120),true,true,true,true,true,true,true,CAPS);
 static final SecretKey KEY=new SecretKeySpec(new byte[32],"AES");
 static final TriageJdbcScope.Scope SCOPE=new TriageJdbcScope.Scope(TENANT,FACILITY,PATIENT,ACTOR);
 static final String ROLE="synqoria_triage_app";
 static final TriageCase BASE=new TriageIntakeService().register(ID,PROOF,CASE,PATIENT,null,NOW.minusSeconds(30));
 static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 static void rejected(Runnable action,String msg){checks++;try{action.run();throw new AssertionError(msg);}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 static void sqlRejected(SqlAction action,String msg){checks++;try{action.run();throw new AssertionError(msg);}catch(SQLException|SecurityException expected){}}
 @FunctionalInterface interface SqlAction{void run()throws SQLException;}
 static TriageCommand cmd(){return new TriageCommand(UUID.fromString("00000000-0000-0000-0000-000000000006"),ACTOR,NOW,1,"synthetic rationale");}
 static TriageCase next(TriageCase c){return new TriageIntakeService().recordArrivalReason(ID,PROOF,c,cmd(),"synthetic reason");}
 static TriageCase call(Fake f)throws SQLException{return TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",SCOPE,ROLE,CASE,cmd(),"test-key",KEY,TriagePersistenceContractTest::next);}
 static TriageEnvelopeCrypto.Scope cryptoScope(){return new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,1);}
 static void codec(String name){
  byte[] encoded=TriageSnapshotCodec.encode(BASE);
  switch(name){
   case "codec-roundtrip" -> {var out=TriageSnapshotCodec.decode(encoded);ok(out.id().equals(CASE)&&out.revision()==1&&out.stage()==TriageCase.Stage.ARRIVED,"codec roundtrip");}
   case "codec-determinism" -> ok(Arrays.equals(encoded,TriageSnapshotCodec.encode(BASE)),"codec deterministic");
   case "codec-bad-magic" -> {encoded[0]^=1;byte[] x=encoded;rejected(()->TriageSnapshotCodec.decode(x),"bad magic accepted");}
   case "codec-truncation" -> {byte[] x=Arrays.copyOf(encoded,encoded.length-1);rejected(()->TriageSnapshotCodec.decode(x),"truncation accepted");}
   case "codec-trailing" -> {byte[] x=Arrays.copyOf(encoded,encoded.length+1);rejected(()->TriageSnapshotCodec.decode(x),"trailing bytes accepted");}
   case "codec-oversize" -> {byte[] x=new byte[4_194_305];rejected(()->TriageSnapshotCodec.decode(x),"oversize accepted");}
   case "codec-audit" -> {TriageCase changed=next(BASE);ok(TriageAudit.verify(TriageSnapshotCodec.decode(TriageSnapshotCodec.encode(changed))),"audit roundtrip");}
   case "codec-tamper" -> {TriageCase changed=next(BASE);byte[] x=TriageSnapshotCodec.encode(changed);x[x.length-1]^=1;rejected(()->TriageSnapshotCodec.decode(x),"audit tamper accepted");}
   default -> throw new IllegalArgumentException(name);
  }
 }
 static void crypto(String name){
  byte[] clear=TriageSnapshotCodec.encode(BASE);
  var env=TriageEnvelopeCrypto.seal(cryptoScope(),"test-key",KEY,clear);
  switch(name){
   case "crypto-roundtrip" -> ok(Arrays.equals(clear,TriageEnvelopeCrypto.open(cryptoScope(),env,KEY)),"crypto roundtrip");
   case "crypto-random-nonce" -> ok(!Arrays.equals(env.ciphertext(),TriageEnvelopeCrypto.seal(cryptoScope(),"test-key",KEY,clear).ciphertext()),"nonce reused");
   case "crypto-scope-binding" -> rejected(()->TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,UUID.randomUUID(),CASE,1),env,KEY),"wrong patient decrypted");
   case "crypto-revision-binding" -> rejected(()->TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,2),env,KEY),"wrong revision decrypted");
   case "crypto-key-binding" -> rejected(()->TriageEnvelopeCrypto.open(cryptoScope(),env,new SecretKeySpec(new byte[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},"AES")),"wrong key decrypted");
   case "crypto-tamper" -> {byte[] x=env.ciphertext();x[14]^=1;var bad=new TriageEnvelopeCrypto.Envelope(env.keyId(),x);rejected(()->TriageEnvelopeCrypto.open(cryptoScope(),bad,KEY),"tampered ciphertext decrypted");}
   case "crypto-invalid-key" -> rejected(()->TriageEnvelopeCrypto.seal(cryptoScope(),"test-key",new SecretKeySpec(new byte[16],"AES"),clear),"short key accepted");
   case "crypto-defensive-copy" -> {byte[] x=env.ciphertext();x[0]^=1;ok(Arrays.equals(clear,TriageEnvelopeCrypto.open(cryptoScope(),env,KEY)),"mutable envelope");}
   default -> throw new IllegalArgumentException(name);
  }
 }
 static void jdbc(String name)throws SQLException{
  Fake f=new Fake();
  switch(name){
   case "jdbc-happy-path" -> {TriageCase out=call(f);ok(out.revision()==2&&out.metadata().containsKey("arrivalReason")&&f.commits==1&&f.rollbacks==0,"transaction failed");}
   case "jdbc-scoped-lock" -> {call(f);ok(f.params.get(TriageJdbcStore.LOCK).get(1).equals(TENANT)&&f.params.get(TriageJdbcStore.LOCK).get(3).equals(PATIENT)&&TriageJdbcStore.LOCK.contains("FOR UPDATE"),"lock unscoped");}
   case "jdbc-cas" -> {call(f);ok(f.params.get(TriageJdbcStore.UPDATE).get(17).equals(1)&&f.params.get(TriageJdbcStore.UPDATE).get(16).equals(CASE),"CAS missing");}
   case "jdbc-event" -> {call(f);ok(f.params.containsKey(TriageJdbcStore.EVENT)&&f.params.get(TriageJdbcStore.EVENT).get(7).equals(2L),"audit event absent");}
   case "jdbc-outbox" -> {call(f);ok(f.params.containsKey(TriageJdbcStore.OUTBOX)&&f.params.get(TriageJdbcStore.OUTBOX).get(6).equals(cmd().commandId()),"outbox absent");}
   case "jdbc-ciphertext" -> {call(f);byte[] blob=(byte[])f.params.get(TriageJdbcStore.UPDATE).get(9);ok(blob!=null&&!Arrays.equals(blob,TriageSnapshotCodec.encode(next(BASE))),"plaintext persisted");}
   case "jdbc-no-autocommit" -> {f.autoCommit=true;sqlRejected(()->call(f),"autocommit allowed");ok(f.commits==0&&f.rollbacks==1,"rollback missing");}
   case "jdbc-serializable" -> {f.isolation=Connection.TRANSACTION_READ_COMMITTED;sqlRejected(()->call(f),"weak isolation allowed");ok(f.commits==0&&f.rollbacks==1,"rollback missing");}
   case "jdbc-readonly" -> {f.readOnly=true;sqlRejected(()->call(f),"read-only connection allowed");ok(f.commits==0,"read-only committed");}
   case "jdbc-superuser" -> {f.superuser=true;sqlRejected(()->call(f),"superuser allowed");ok(f.commits==0,"superuser committed");}
   case "jdbc-role" -> {f.role="untrusted_role";sqlRejected(()->call(f),"wrong role allowed");ok(f.commits==0,"wrong role committed");}
   case "jdbc-bypassrls" -> {f.bypass=true;sqlRejected(()->call(f),"bypass role allowed");ok(f.commits==0,"bypass committed");}
   case "jdbc-rls" -> {f.rls=false;sqlRejected(()->call(f),"disabled RLS allowed");ok(f.commits==0,"RLS committed");}
   case "jdbc-scope-echo" -> {f.scopeEcho=false;sqlRejected(()->call(f),"mismatched scope allowed");ok(f.commits==0,"scope committed");}
   case "jdbc-missing-row" -> {f.missingRow=true;sqlRejected(()->call(f),"missing row allowed");ok(f.commits==0,"missing committed");}
   case "jdbc-snapshot-tamper" -> {f.tamper=true;sqlRejected(()->call(f),"tampered row allowed");ok(f.commits==0,"tampered committed");}
   case "jdbc-stale-revision" -> {f.rowRevision=2;sqlRejected(()->call(f),"stale row allowed");ok(f.commits==0,"stale committed");}
   case "jdbc-cas-race" -> {f.failSql=TriageJdbcStore.UPDATE;sqlRejected(()->call(f),"CAS race committed");ok(f.commits==0&&f.rollbacks==1,"race not rolled back");}
   case "jdbc-event-failure" -> {f.failSql=TriageJdbcStore.EVENT;sqlRejected(()->call(f),"event failure committed");ok(f.commits==0&&f.rollbacks==1,"event not rolled back");}
   case "jdbc-outbox-failure" -> {f.failSql=TriageJdbcStore.OUTBOX;sqlRejected(()->call(f),"outbox failure committed");ok(f.commits==0&&f.rollbacks==1,"outbox not rolled back");}
   case "jdbc-cross-tenant" -> {var scope=new TriageJdbcScope.Scope(UUID.randomUUID(),FACILITY,PATIENT,ACTOR);sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",scope,ROLE,CASE,cmd(),"test-key",KEY,TriagePersistenceContractTest::next),"cross tenant allowed");ok(f.commits==0,"cross tenant committed");}
   case "jdbc-revoked-evidence" -> {var revoked=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(120),NOW.plusSeconds(120),true,true,true,true,true,false,true,CAPS);sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,revoked,"triage:intake",SCOPE,ROLE,CASE,cmd(),"test-key",KEY,TriagePersistenceContractTest::next),"revoked allowed");ok(f.commits==0,"revoked committed");}
   case "jdbc-missing-tip" -> {f.snapshot=next(BASE);f.rowRevision=2;f.missingTip=true;sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",SCOPE,ROLE,CASE,new TriageCommand(UUID.randomUUID(),ACTOR,NOW,2,"synthetic rationale"),"test-key",KEY,TriagePersistenceContractTest::next),"missing audit tip allowed");ok(f.commits==0,"missing tip committed");}
   case "jdbc-outbox-tip-mismatch" -> {f.snapshot=next(BASE);f.rowRevision=2;f.badOutbox=true;sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",SCOPE,ROLE,CASE,new TriageCommand(UUID.randomUUID(),ACTOR,NOW,2,"synthetic rationale"),"test-key",KEY,TriagePersistenceContractTest::next),"outbox mismatch allowed");ok(f.commits==0,"outbox mismatch committed");}
   case "jdbc-row-stage-mismatch" -> {f.badStage=true;sqlRejected(()->call(f),"row stage mismatch allowed");ok(f.commits==0,"row mismatch committed");}
   case "jdbc-no-manage-capability" -> {var noManage=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("triage:intake"),Set.of(PATIENT));sqlRejected(()->TriageJdbcStore.mutate(f.connection(),noManage,PROOF,"triage:intake",SCOPE,ROLE,CASE,cmd(),"test-key",KEY,TriagePersistenceContractTest::next),"write without manage allowed");ok(f.commits==0,"unauthorized committed");}
   case "jdbc-duplicate-command" -> {TriageCase prev=next(BASE);f.snapshot=prev;f.rowRevision=2;TriageCommand replay=new TriageCommand(cmd().commandId(),ACTOR,NOW,2,"synthetic rationale");sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",SCOPE,ROLE,CASE,replay,"test-key",KEY,TriagePersistenceContractTest::next),"replay allowed");ok(f.commits==0,"replay committed");}
   case "jdbc-invalid-mutation" -> {sqlRejected(()->TriageJdbcStore.mutate(f.connection(),ID,PROOF,"triage:intake",SCOPE,ROLE,CASE,cmd(),"test-key",KEY,old->old),"no-op mutation accepted");ok(f.commits==0,"invalid mutation committed");}
   case "jdbc-budget" -> {f.budget="bad";sqlRejected(()->call(f),"budget mismatch allowed");ok(f.commits==0,"budget committed");}
   default -> throw new IllegalArgumentException(name);
  }
 }
 static final class Fake {
  boolean autoCommit=false,readOnly=false,bypass=false,superuser=false,rls=true,scopeEcho=true,missingRow=false,tamper=false,missingTip=false,badOutbox=false,badStage=false;
  int isolation=Connection.TRANSACTION_SERIALIZABLE,rowRevision=1,commits=0,rollbacks=0;
  String role=ROLE,budget="5s",failSql="";
  TriageCase snapshot=BASE;
  Map<String,Map<Integer,Object>> params=new HashMap<>();
  Connection connection(){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   switch(m.getName()){
    case "getAutoCommit":return autoCommit;case "isReadOnly":return readOnly;case "getTransactionIsolation":return isolation;
    case "commit":commits++;return null;case "rollback":rollbacks++;return null;
    case "prepareStatement":return statement((String)a[0]);case "close":return null;
    case "isClosed":return false;
   }
   throw new UnsupportedOperationException("unhandled JDBC "+m.getName());
  });}
  PreparedStatement statement(String sql){var args=new HashMap<Integer,Object>();params.put(sql,args);
   return (PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
    String method=m.getName();if(method.startsWith("set")){if(a!=null&&a.length>1)args.put((Integer)a[0],a[1]);return null;}
    if(method.equals("executeUpdate"))return sql.equals(failSql)?0:1;
    if(method.equals("executeQuery"))return result(sql,args);
    if(method.equals("close"))return null;
    throw new UnsupportedOperationException("unhandled statement "+method);
   });
  }
  ResultSet result(String sql,Map<Integer,Object> args){
   Object[] row;
   if(sql.equals(TriageJdbcFence.ROLE_SQL))row=new Object[]{role,superuser,bypass,true};
   else if(sql.equals(TriageJdbcFence.RLS_SQL))row=new Object[]{rls,rls,rls};
   else if(sql.equals(TriageJdbcScope.SQL))row=new Object[]{scopeEcho?args.get(1):"invalid",args.get(2),args.get(3),args.get(4)};
   else if(sql.contains("statement_timeout"))row=new Object[]{budget,"1500","15000"};
   else if(sql.equals(TriageJdbcStore.EVENT_TIP)){var e=snapshot.changes().isEmpty()?null:snapshot.changes().get(snapshot.changes().size()-1);row=e==null||missingTip?null:new Object[]{(long)e.revision(),e.digest()};}
   else if(sql.equals(TriageJdbcStore.OUTBOX_LINK)){var e=snapshot.changes().isEmpty()?null:snapshot.changes().get(snapshot.changes().size()-1);row=e==null?null:new Object[]{badOutbox?"0".repeat(64):e.digest()};}
   else if(sql.equals(TriageJdbcStore.LOCK)){
    var scope=new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,rowRevision);
    var env=TriageEnvelopeCrypto.seal(scope,"test-key",KEY,TriageSnapshotCodec.encode(snapshot));byte[] data=env.ciphertext();if(tamper)data[15]^=1;
    row=missingRow?null:new Object[]{rowRevision,badStage?"CLOSED":snapshot.stage().name(),snapshot.station(),snapshot.assignee(),snapshot.clinicianPriority(),snapshot.safetyHold(),snapshot.paused(),snapshot.assessedSigned(),data,"test-key",TriageAudit.stateDigest(snapshot)};
   } else throw new UnsupportedOperationException("unhandled query "+sql);
   final Object[] r=row;final int[] cursor={0};
   return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->{
    switch(m.getName()){
     case "next":return ++cursor[0]==1&&r!=null;
     case "getString":return (String)r[(Integer)a[0]-1];
     case "getBoolean":return (Boolean)r[(Integer)a[0]-1];
     case "getInt":return (Integer)r[(Integer)a[0]-1];
     case "getLong":return (Long)r[(Integer)a[0]-1];
     case "getObject":return r[(Integer)a[0]-1];
     case "getBytes":return (byte[])r[(Integer)a[0]-1];
     case "close":return null;
    }
    throw new UnsupportedOperationException("unhandled result "+m.getName());
   });
  }
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("one scenario required");
  String name=args[0];if(name.startsWith("codec-"))codec(name);else if(name.startsWith("crypto-"))crypto(name);else jdbc(name);
  System.out.println("PASS "+name+" checks="+checks);
 }
}
