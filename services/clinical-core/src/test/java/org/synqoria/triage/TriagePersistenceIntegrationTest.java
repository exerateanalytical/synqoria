package org.synqoria.triage;
import org.synqoria.security.*;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** Synthetic authenticated triage transaction simulation; not a claim of PostgreSQL integration. */
public final class TriagePersistenceIntegrationTest {
 private static int checks=0;
 private static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 private static void reject(Runnable r){checks++;try{r.run();throw new AssertionError("expected rejection "+checks);}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 private static final Instant NOW=Instant.parse("2026-10-30T13:00:00Z");
 private static final UUID TENANT=UUID.randomUUID(),FACILITY=UUID.randomUUID(),PATIENT=UUID.randomUUID(),ACTOR=UUID.randomUUID(),CASE=UUID.randomUUID(),STATION=UUID.randomUUID();
 private static final Set<String> CAP=Set.of("triage:intake","triage:queue","triage:manage","triage:read");
 private static final TrustedAccessContext CTX=new TrustedAccessContext(TENANT,FACILITY,ACTOR,CAP,Set.of(PATIENT));
 private static final TrustedClinicalAccessEvidence PROOF=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(600),NOW.plusSeconds(600),true,true,true,true,true,true,true,CAP);
 private static final TriagePersistencePort.Scope SCOPE=new TriagePersistencePort.Scope(TENANT,FACILITY,PATIENT,CASE);
 private static TriageCommand q(int rev){return new TriageCommand(UUID.randomUUID(),ACTOR,NOW,rev,"Reviewed rationale");}
 /** Snapshot-copy commit/rollback model: isolates transaction semantics while real PostgreSQL remains unavailable. */
 private static final class FakeDb implements TriagePersistencePort {
  Map<Scope,TriageCase> cases=new HashMap<>();Map<String,Receipt> receipts=new HashMap<>();Map<String,TriageCase.Change> audit=new HashMap<>();Map<String,String> outbox=new HashMap<>();
  enum Failure{NONE,BEFORE_CASE_WRITE,AFTER_CASE_WRITE,AFTER_AUDIT,AFTER_OUTBOX,AFTER_RECEIPT}
  Failure inject=Failure.NONE;
  private static String receiptKey(Scope s,UUID command){return s+"|"+command;}
  @Override public synchronized <T> T transaction(Function<Unit,T> action){
   Map<Scope,TriageCase> copyCases=new HashMap<>(cases);Map<String,Receipt> copyReceipts=new HashMap<>(receipts);Map<String,TriageCase.Change> copyAudit=new HashMap<>(audit);Map<String,String> copyOutbox=new HashMap<>(outbox);
   Unit tx=new Unit(){
    public TriageCase loadLocked(Scope s){return copyCases.get(s);}
    public Receipt receipt(Scope s,UUID id){return copyReceipts.get(receiptKey(s,id));}
    public void create(TriageCase c){if(inject==Failure.BEFORE_CASE_WRITE)throw new IllegalStateException("injected failure");if(copyCases.putIfAbsent(new Scope(c.tenant(),c.facility(),c.patient(),c.id()),c)!=null)throw new IllegalStateException("duplicate case");if(inject==Failure.AFTER_CASE_WRITE)throw new IllegalStateException("injected failure");}
    public void update(TriageCase b,TriageCase a){if(inject==Failure.BEFORE_CASE_WRITE)throw new IllegalStateException("injected failure");Scope key=new Scope(b.tenant(),b.facility(),b.patient(),b.id());if(!copyCases.replace(key,b,a))throw new IllegalStateException("stale revision");if(inject==Failure.AFTER_CASE_WRITE)throw new IllegalStateException("injected failure");}
    public void appendAuditAndOutbox(TriageCase c,TriageCase.Change ev){String k=receiptKey(new Scope(c.tenant(),c.facility(),c.patient(),c.id()),ev.commandId());if(copyAudit.putIfAbsent(k,ev)!=null)throw new IllegalStateException("duplicate audit");if(inject==Failure.AFTER_AUDIT)throw new IllegalStateException("injected failure");if(copyOutbox.putIfAbsent(k,ev.digest())!=null)throw new IllegalStateException("duplicate outbox");if(inject==Failure.AFTER_OUTBOX)throw new IllegalStateException("injected failure");}
    public void saveReceipt(Scope s,Receipt rec){if(copyReceipts.putIfAbsent(receiptKey(s,rec.commandId()),rec)!=null)throw new IllegalStateException("duplicate receipt");if(inject==Failure.AFTER_RECEIPT)throw new IllegalStateException("injected failure");}
   };
   T result=action.apply(tx);cases=copyCases;receipts=copyReceipts;audit=copyAudit;outbox=copyOutbox;return result;
  }
 }
 public static void main(String[] args){
  var db=new FakeDb();var coordinator=new TriageCommandCoordinator(db);var intake=q(0);UUID appointment=UUID.randomUUID();
  var a=coordinator.arrive(CTX,PROOF,SCOPE,intake,appointment);ok(a.revision()==1&&!a.replay());ok(a.auditDigest().length()==64);
  ok(db.cases.size()==1&&db.receipts.size()==1&&db.audit.size()==1&&db.outbox.size()==1);
  ok(coordinator.arrive(CTX,PROOF,SCOPE,intake,appointment).replay());
  reject(()->coordinator.arrive(CTX,PROOF,SCOPE,intake,UUID.randomUUID()));
  reject(()->coordinator.arrive(CTX,PROOF,SCOPE,q(0),appointment));
  var current=db.cases.get(SCOPE);ok(current!=null&&current.revision()==1);
  byte[] raw=TriageSnapshotCodec.encode(current);ok(Arrays.equals(raw,TriageSnapshotCodec.encode(current)));
  TriageCase restored=TriageSnapshotCodec.decode(raw);ok(restored.id().equals(current.id())&&restored.tenant().equals(current.tenant())&&restored.revision()==1);
  reject(()->TriageSnapshotCodec.decode(new byte[0]));
  byte[] trailing=Arrays.copyOf(raw,raw.length+1);reject(()->TriageSnapshotCodec.decode(trailing));
  byte[] version=raw.clone();version[7]++;reject(()->TriageSnapshotCodec.decode(version));
  byte[] truncated=Arrays.copyOf(raw,raw.length-3);reject(()->TriageSnapshotCodec.decode(truncated));
  byte[] corrupt=raw.clone();corrupt[1]=(byte)0;reject(()->TriageSnapshotCodec.decode(corrupt));
  var key=new SecretKeySpec(new byte[32],"AES");var scope=new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,1);
  var sealed=TriageEnvelopeCrypto.seal(scope,"kms-dev-test-01",key,raw);
  ok(Arrays.equals(TriageEnvelopeCrypto.open(scope,sealed,key),raw));
  ok(!Arrays.equals(TriageEnvelopeCrypto.seal(scope,"kms-dev-test-01",key,raw).ciphertext(),sealed.ciphertext()));
  byte[] tampered=sealed.ciphertext();tampered[tampered.length-1]^=0x01;
  reject(()->TriageEnvelopeCrypto.open(scope,new TriageEnvelopeCrypto.Envelope(sealed.keyId(),tampered),key));
  reject(()->TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,UUID.randomUUID(),CASE,1),sealed,key));
  reject(()->TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,2),sealed,key));
  reject(()->TriageEnvelopeCrypto.open(scope,sealed,new SecretKeySpec(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32},"AES")));
  reject(()->TriageEnvelopeCrypto.seal(scope,"unsafe key id",key,raw));
  reject(()->TriageEnvelopeCrypto.seal(scope,"key",new SecretKeySpec(new byte[16],"AES"),raw));
  var q=q(1);UnaryOperator<TriageCase> enqueue=c->new TriageQueueService().enqueue(CTX,PROOF,c,q,STATION);
  String payload="station:"+STATION;
  var result=coordinator.mutate(CTX,PROOF,SCOPE,q,"triage:queue","QUEUE_ENQUEUE",payload,enqueue);
  ok(result.revision()==2&&!result.replay());ok(db.cases.get(SCOPE).stage()==TriageCase.Stage.WAITING);ok(db.audit.size()==2&&db.outbox.size()==2);
  ok(db.audit.values().stream().anyMatch(e->e.digest().equals(result.auditDigest())));
  var replay=coordinator.mutate(CTX,PROOF,SCOPE,q,"triage:queue","QUEUE_ENQUEUE",payload,enqueue);ok(replay.replay()&&replay.revision()==2);
  reject(()->coordinator.mutate(CTX,PROOF,SCOPE,q,"triage:queue","QUEUE_ENQUEUE","different",enqueue));
  reject(()->coordinator.mutate(CTX,PROOF,SCOPE,q(1),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var other=new TriagePersistencePort.Scope(TENANT,FACILITY,UUID.randomUUID(),CASE);
  reject(()->coordinator.mutate(CTX,PROOF,other,q(2),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var otherFacility=new TrustedAccessContext(TENANT,UUID.randomUUID(),ACTOR,CAP,Set.of(PATIENT));
  reject(()->coordinator.mutate(otherFacility,PROOF,SCOPE,q(2),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var otherActor=new TrustedAccessContext(TENANT,FACILITY,UUID.randomUUID(),CAP,Set.of(PATIENT));
  reject(()->coordinator.mutate(otherActor,PROOF,SCOPE,q(2),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var missingCapability=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("triage:read"),Set.of(PATIENT));
  reject(()->coordinator.mutate(missingCapability,PROOF,SCOPE,q(2),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var expired=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(2000),NOW.minusSeconds(1),true,true,true,true,true,true,true,CAP);
  reject(()->coordinator.mutate(CTX,expired,SCOPE,q(2),"triage:queue","QUEUE_ENQUEUE",payload,enqueue));
  var next=q(2);UnaryOperator<TriageCase> call=c->new TriageQueueService().call(CTX,PROOF,c,next);
  for(FakeDb.Failure failure:List.of(FakeDb.Failure.BEFORE_CASE_WRITE,FakeDb.Failure.AFTER_CASE_WRITE,FakeDb.Failure.AFTER_AUDIT,FakeDb.Failure.AFTER_OUTBOX,FakeDb.Failure.AFTER_RECEIPT)){
    int count=db.receipts.size();db.inject=failure;reject(()->coordinator.mutate(CTX,PROOF,SCOPE,next,"triage:queue","QUEUE_CALL","call",call));
    ok(db.cases.get(SCOPE).revision()==2&&db.receipts.size()==count&&db.outbox.size()==2&&db.audit.size()==2);
  }
  db.inject=FakeDb.Failure.NONE;
  var success=coordinator.mutate(CTX,PROOF,SCOPE,next,"triage:queue","QUEUE_CALL","call",call);ok(success.revision()==3&&db.outbox.size()==3&&db.audit.size()==3);
  TriageCase after=db.cases.get(SCOPE);ok(TriageAudit.verify(after));
  var encrypted=TriageEnvelopeCrypto.seal(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,after.revision()),"kms-dev-test-01",key,TriageSnapshotCodec.encode(after));
  TriageCase decoded=TriageSnapshotCodec.decode(TriageEnvelopeCrypto.open(new TriageEnvelopeCrypto.Scope(TENANT,FACILITY,PATIENT,CASE,3),encrypted,key));
  ok(decoded.stage()==TriageCase.Stage.CALLED&&decoded.changes().size()==2&&TriageAudit.verify(decoded));
  var altered=new TriageCase(after.id(),after.tenant(),after.facility(),after.patient(),after.appointment(),after.station(),after.assignee(),after.stage(),after.revision(),"spoofed",after.escalationReason(),after.handoverNote(),after.paused(),after.safetyHold(),after.assessedSigned(),after.arrivedAt(),after.lastReassessment(),after.observations(),after.metadata(),after.changes());
  reject(()->TriageSnapshotCodec.encode(altered));
  ok(db.receipts.size()==3);System.out.println("TRIAGE_V520_TRANSACTION_TEST_PASS checks="+checks+" audit="+db.audit.size()+" outbox="+db.outbox.size());
 }
}
