package org.synqoria.triage;

import java.time.*;
import java.util.*;

/** Executable adversarial tests for the arrival -> first mutation -> durable replay chain. Synthetic identities only. */
public final class TriageAuditContinuityTest {
  static int assertions;
  static void ok(boolean condition){assertions++;if(!condition)throw new AssertionError("audit continuity assertion "+assertions);}
  static void reject(Runnable action){assertions++;try{action.run();throw new AssertionError("expected security rejection");}catch(SecurityException|IllegalArgumentException expected){}}
  static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
     A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE;
  static final Instant NOW=TriageWorkflowBatchesTest.NOW;
  static final TriagePersistencePort.Scope SCOPE=new TriagePersistencePort.Scope(T,F,P,C);
  static final class Fixture {
   final TriageFakeAtomicStore db=new TriageFakeAtomicStore();
   final TriageCommandCoordinator coordinator=new TriageCommandCoordinator(db);
   final TriageAuthenticatedCommandGateway gateway=new TriageAuthenticatedCommandGateway(coordinator);
   final TriageCommand arrival=new TriageCommand(UUID.randomUUID(),A,NOW,0,"synthetic intake");
   TriageCase state(){return db.cases.get(SCOPE);}
   TriageCommandCoordinator.Result arrive(){return coordinator.arrive(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,SCOPE,arrival,null);}
   TriageCommand mutation(){return new TriageCommand(UUID.randomUUID(),A,NOW.plusSeconds(1),1,"documented arrival reason");}
   TriageCommandCoordinator.Result mutate(TriageCommand command){return gateway.execute(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,SCOPE,command,TriageAuthenticatedCommandGateway.Input.text("ARRIVAL_REASON","registered synthetic patient"));}
   List<TriageAuditEvidence.Event> ledger(){
    var rows=new ArrayList<TriageAuditEvidence.Event>();
    var c=state();var changes=new ArrayList<TriageCase.Change>();
    changes.add(c.genesis());changes.addAll(c.changes());
    for(var e:changes)rows.add(row(e));return rows;
   }
   TriageAuditEvidence.Event row(TriageCase.Change e){return new TriageAuditEvidence.Event((int)e.revision(),e.commandId(),e.operation(),
      e.previousDigest(),e.stateDigest(),e.digest(),TriageAudit.hash("triage-actor-v1|"+e.actor()),e.at(),e.digest(),e.digest());}
  }
  static TriageAuditEvidence.Event modify(TriageAuditEvidence.Event e,String field){
   String bad="f".repeat(64);
   return switch(field){
    case "revision" -> new TriageAuditEvidence.Event(e.revision()+1,e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "command" -> new TriageAuditEvidence.Event(e.revision(),UUID.randomUUID(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "operation" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),"FORGED",e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "previous" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),bad,e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "state" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),bad,e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "digest" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),bad,e.actorDigest(),e.at(),e.receiptDigest(),e.outboxDigest());
    case "actor" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),bad,e.at(),e.receiptDigest(),e.outboxDigest());
    case "time" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at().plusSeconds(42),e.receiptDigest(),e.outboxDigest());
    case "receipt" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),bad,e.outboxDigest());
    case "outbox" -> new TriageAuditEvidence.Event(e.revision(),e.commandId(),e.operation(),e.previousDigest(),e.stateDigest(),e.eventDigest(),e.actorDigest(),e.at(),e.receiptDigest(),bad);
    default -> throw new IllegalArgumentException("unsupported test field");
   };
  }
  public static void main(String[] args){
   var f=new Fixture();var initial=f.arrive();var c1=f.state();
   ok(initial.revision()==1&&!initial.replay());
   ok(c1.genesis()!=null&&c1.genesis().revision()==1&&TriageAudit.verify(c1));
   ok(TriageAudit.stateDigest(c1).equals(c1.genesis().stateDigest()));
   ok(c1.genesis().digest().equals(f.db.receipts.get(SCOPE+"|"+f.arrival.commandId()).eventDigest()));
   TriageAuditEvidence.requireFullChain(c1,f.ledger());ok(true);
   var byte1=TriageSnapshotCodec.encode(c1);var restore1=TriageSnapshotCodec.decode(byte1);
   ok(restore1.genesis().equals(c1.genesis())&&Arrays.equals(byte1,TriageSnapshotCodec.encode(restore1)));
   var first=f.mutation();var result=f.mutate(first);var c2=f.state();
   ok(result.revision()==2&&!result.replay()&&TriageAudit.verify(c2));
   ok(c2.changes().get(0).previousDigest().equals(c2.genesis().digest()));
   ok(c2.changes().get(0).digest().equals(result.auditDigest()));
   TriageAuditEvidence.requireFullChain(c2,f.ledger());ok(true);
   byte[] stored=TriageSnapshotCodec.encode(c2);
   ok(TriageSnapshotCodec.decode(stored).genesis().equals(c2.genesis()));
   ok(Arrays.equals(stored,TriageSnapshotCodec.encode(TriageSnapshotCodec.decode(stored))));
   var replay=f.mutate(first);ok(replay.replay()&&replay.revision()==2&&replay.auditDigest().equals(result.auditDigest()));
   reject(()->f.mutate(new TriageCommand(first.commandId(),A,NOW.plusSeconds(1),2,"changed revision")));
   reject(()->f.coordinator.arrive(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,SCOPE,f.arrival,UUID.randomUUID()));
   for(String field:List.of("revision","command","operation","previous","state","digest","actor","time","receipt","outbox")){
    var ledger=f.ledger();ledger.set(1,modify(ledger.get(1),field));final var firstTampered=ledger;reject(()->TriageAuditEvidence.requireFullChain(c2,firstTampered));
    ledger=f.ledger();ledger.set(0,modify(ledger.get(0),field));final var tampered=ledger;reject(()->TriageAuditEvidence.requireFullChain(c2,tampered));
   }
   reject(()->TriageAuditEvidence.requireFullChain(c2,List.of(f.ledger().get(0))));
   var swapped=new ArrayList<>(f.ledger());Collections.reverse(swapped);reject(()->TriageAuditEvidence.requireFullChain(c2,swapped));
   var duplicate=new ArrayList<>(f.ledger());duplicate.add(duplicate.get(1));reject(()->TriageAuditEvidence.requireFullChain(c2,duplicate));
   var legacy=new TriageCase(c2.id(),c2.tenant(),c2.facility(),c2.patient(),c2.appointment(),c2.station(),c2.assignee(),
     c2.stage(),c2.revision(),c2.clinicianPriority(),c2.escalationReason(),c2.handoverNote(),c2.paused(),c2.safetyHold(),c2.assessedSigned(),
     c2.arrivedAt(),c2.lastReassessment(),c2.observations(),c2.metadata(),c2.changes());
   reject(()->TriageAuditEvidence.requireFullChain(legacy,f.ledger()));
   byte[] truncated=Arrays.copyOf(stored,stored.length-1);reject(()->TriageSnapshotCodec.decode(truncated));
   byte[] corrupted=stored.clone();corrupted[7]++;reject(()->TriageSnapshotCodec.decode(corrupted));
   var receipt=f.db.receipts.get(SCOPE+"|"+first.commandId());ok(TriageAuditEvidence.receiptMatchesCase(c2,receipt));
   ok(!TriageAuditEvidence.receiptMatchesCase(c1,receipt));
   var forged=new TriagePersistencePort.Receipt(receipt.commandId(),receipt.fingerprint(),receipt.revision(),"e".repeat(64),receipt.committedAt());
   ok(!TriageAuditEvidence.receiptMatchesCase(c2,forged));
   var forgedTime=new TriagePersistencePort.Receipt(receipt.commandId(),receipt.fingerprint(),receipt.revision(),receipt.eventDigest(),receipt.committedAt().plusSeconds(1));
   ok(!TriageAuditEvidence.receiptMatchesCase(c2,forgedTime));
   System.out.println("TRIAGE_AUDIT_CONTINUITY PASS assertions="+assertions);
  }
}
