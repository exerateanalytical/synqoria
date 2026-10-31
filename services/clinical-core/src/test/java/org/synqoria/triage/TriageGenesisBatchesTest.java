package org.synqoria.triage;

import org.synqoria.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Distinct intake genesis / replay / atomic rollback regression scenarios; synthetic transactional store only. */
public final class TriageGenesisBatchesTest {
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
  A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final TrustedAccessContext CTX=TriageWorkflowBatchesTest.a;
 static final TrustedClinicalAccessEvidence PROOF=TriageWorkflowBatchesTest.e;
 static final TriagePersistencePort.Scope SCOPE=new TriagePersistencePort.Scope(T,F,P,C);
 static int checks;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError("genesis check "+checks);}
 static void denied(Runnable r){checks++;try{r.run();throw new AssertionError("expected denial");}catch(SecurityException|IllegalStateException expected){}}
 static TriageCommand command(){return new TriageCommand(UUID.randomUUID(),A,NOW,0,"reviewed arrival");}
 static final class Fixture {
  final TriageFakeAtomicStore db=new TriageFakeAtomicStore();
  final TriageCommandCoordinator gateway=new TriageCommandCoordinator(db);
  final TriageCommand cmd=command();
  final UUID appointment=UUID.randomUUID();
  void created(){ok(db.cases.size()==1&&db.audits.size()==1&&db.outbox.size()==1&&db.receipts.size()==1);
   var event=db.audits.get(cmd.commandId());var receipt=db.receipts.get(SCOPE+"|"+cmd.commandId());
   ok(event!=null&&event.revision()==1&&event.operation().equals("ARRIVAL")&&event.previousDigest().equals("0".repeat(64)));
   ok(receipt!=null&&receipt.eventDigest().equals(event.digest())&&db.outbox.get(cmd.commandId()).equals(event.digest()));
   ok(TriageAudit.verify(db.cases.get(SCOPE))&&db.cases.get(SCOPE).clinicianPriority()==null);
  }
  void empty(){ok(db.cases.isEmpty()&&db.audits.isEmpty()&&db.outbox.isEmpty()&&db.receipts.isEmpty());}
  void arrive(){gateway.arrive(CTX,PROOF,SCOPE,cmd,appointment);}
 }
 static void inject(int stage){
  Fixture f=new Fixture();
  TriagePersistencePort fault=new TriagePersistencePort(){
   public <R> R transaction(Function<Unit,R> work){return f.db.transaction(tx->work.apply(new Unit(){
    void failure(int index){if(index==stage)throw new IllegalStateException("synthetic injected rollback");}
    public TriageCase loadLocked(Scope s){return tx.loadLocked(s);}
    public Receipt receipt(Scope s,UUID id){return tx.receipt(s,id);}
    public void create(TriageCase c){tx.create(c);failure(1);}
    public void update(TriageCase before,TriageCase after){tx.update(before,after);}
    public void appendAuditAndOutbox(TriageCase c,TriageCase.Change e){tx.appendAuditAndOutbox(c,e);failure(2);}
    public void saveReceipt(Scope s,Receipt r){tx.saveReceipt(s,r);failure(3);}
   }));}
  };
  denied(()->new TriageCommandCoordinator(fault).arrive(CTX,PROOF,SCOPE,f.cmd,f.appointment));f.empty();
 }
 static void scenario(String id){Fixture f=new Fixture();switch(id){
  case "P06-B0229" -> {var r=f.gateway.arrive(CTX,PROOF,SCOPE,f.cmd,f.appointment);ok(r.revision()==1&&!r.replay());f.created();}
  case "P06-B0230" -> {f.arrive();var r=f.gateway.arrive(CTX,PROOF,SCOPE,f.cmd,f.appointment);ok(r.replay()&&r.revision()==1);f.created();}
  case "P06-B0231" -> {f.arrive();denied(()->f.gateway.arrive(CTX,PROOF,SCOPE,f.cmd,UUID.randomUUID()));f.created();}
  case "P06-B0232" -> {f.arrive();denied(()->f.gateway.arrive(CTX,PROOF,SCOPE,command(),f.appointment));f.created();}
  case "P06-B0233" -> {denied(()->f.gateway.arrive(new TrustedAccessContext(OTHER,F,A,TriageWorkflowBatchesTest.CAPS,Set.of(P)),PROOF,SCOPE,f.cmd,f.appointment));f.empty();}
  case "P06-B0234" -> {denied(()->f.gateway.arrive(CTX,PROOF,new TriagePersistencePort.Scope(T,F,OTHER,C),f.cmd,f.appointment));f.empty();}
  case "P06-B0235" -> {denied(()->f.gateway.arrive(new TrustedAccessContext(T,F,OTHER,TriageWorkflowBatchesTest.CAPS,Set.of(P)),PROOF,SCOPE,f.cmd,f.appointment));f.empty();}
  case "P06-B0236" -> {denied(()->f.gateway.arrive(new TrustedAccessContext(T,F,A,Set.of("triage:read"),Set.of(P)),PROOF,SCOPE,f.cmd,f.appointment));f.empty();}
  case "P06-B0237" -> {var expired=new TrustedClinicalAccessEvidence(T,F,A,P,NOW.minusSeconds(600),NOW.minusSeconds(1),true,true,true,true,true,true,true,TriageWorkflowBatchesTest.CAPS);
   denied(()->f.gateway.arrive(CTX,expired,SCOPE,f.cmd,f.appointment));f.empty();}
  case "P06-B0238" -> inject(1);
  case "P06-B0239" -> inject(2);
  case "P06-B0240" -> inject(3);
  default -> throw new IllegalArgumentException("unknown batch");
 }}
 public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("one batch required");scenario(args[0]);System.out.println(args[0]+" PASS checks="+checks);}
}
