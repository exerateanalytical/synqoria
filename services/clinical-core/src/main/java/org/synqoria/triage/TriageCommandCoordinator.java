package org.synqoria.triage;

import org.synqoria.security.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;

/** Secure orchestration. No caller-controlled identity claims; caller supplies server-verified access context. */
public final class TriageCommandCoordinator {
 public record Result(int revision,String auditDigest,boolean replay){
  public Result {if(revision<1||!TriageCase.hex(auditDigest))throw new IllegalArgumentException("invalid triage result");}
 }
 private final TriagePersistencePort database;
 public TriageCommandCoordinator(TriagePersistencePort database){this.database=Objects.requireNonNull(database);}
 private static String digest(String input){return TriageAudit.hash(input);}
 private static String fingerprint(TriagePersistencePort.Scope s,TriageCommand q,String operation,String canonicalPayload){
  if(!TriageCase.safe(operation,80)||canonicalPayload==null||canonicalPayload.length()>10000)throw new IllegalArgumentException("invalid command fingerprint data");
  return digest("triage-command-v1|"+s.tenant()+"|"+s.facility()+"|"+s.patient()+"|"+s.caseId()+"|"+q.actor()+"|"+q.expectedRevision()+"|"+operation+"|"+canonicalPayload.length()+":"+canonicalPayload);
 }
 public Result mutate(TrustedAccessContext identity,TrustedClinicalAccessEvidence evidence,
  TriagePersistencePort.Scope scope,TriageCommand command,String capability,String operation,
  String canonicalPayload,UnaryOperator<TriageCase> domainMutation){
  if(identity==null||scope==null||command==null||domainMutation==null||capability==null)throw new SecurityException("triage command unavailable");
  if(!identity.tenantId().equals(scope.tenant())||!identity.facilityId().equals(scope.facility())||!identity.actorId().equals(command.actor()))throw new SecurityException("triage resource unavailable");
  TriageAuthorization.require(identity,evidence,scope.tenant(),scope.facility(),scope.patient(),capability,command.trustedNow());
  String expectedFingerprint=fingerprint(scope,command,operation,canonicalPayload);
  return database.transaction(tx->{
   TriageCase c=tx.loadLocked(scope);
   if(c==null)throw new SecurityException("triage resource unavailable");
   if(!TriageAudit.verify(c))throw new SecurityException("triage audit integrity invalid");
   var existing=tx.receipt(scope,command.commandId());
   if(existing!=null){
      if(!existing.fingerprint().equals(expectedFingerprint))throw new SecurityException("idempotency key reused with different command");
      if(!TriageAuditEvidence.receiptMatchesCase(c,existing))throw new SecurityException("triage replay evidence mismatch");
      return new Result(existing.revision(),existing.eventDigest(),true);
   }
   if(c.revision()!=command.expectedRevision())throw new TriageCommandConflict(true);
   TriageCase after;try{after=domainMutation.apply(c);}catch(IllegalStateException invalidState){throw new TriageCommandConflict(false);}
   if(after==null||after.revision()!=c.revision()+1||!after.id().equals(c.id())||!after.tenant().equals(c.tenant())||!after.facility().equals(c.facility())||!after.patient().equals(c.patient())||
      !Objects.equals(after.appointment(),c.appointment())||!after.arrivedAt().equals(c.arrivedAt())||
      !Objects.equals(after.genesis(),c.genesis())||after.changes().size()!=c.changes().size()+1||
      !after.changes().subList(0,c.changes().size()).equals(c.changes())||!TriageAudit.verify(after))
      throw new SecurityException("triage mutation invariant failed");
   TriageCase.Change last=after.changes().get(after.changes().size()-1);
   if(!last.commandId().equals(command.commandId())||!last.actor().equals(command.actor())||!last.operation().equals(operation)||!last.at().equals(command.trustedNow()))throw new SecurityException("triage audit attribution mismatch");
   tx.update(c,after);tx.appendAuditAndOutbox(after,last);
   tx.saveReceipt(scope,new TriagePersistencePort.Receipt(command.commandId(),expectedFingerprint,after.revision(),last.digest(),command.trustedNow()));
   return new Result(after.revision(),last.digest(),false);
  });
 }
 /** Intake creation has a separate idempotent transaction and must not infer clinical priority. */
 public Result arrive(TrustedAccessContext identity,TrustedClinicalAccessEvidence evidence,TriagePersistencePort.Scope scope,TriageCommand command,UUID appointment){
  if(identity==null||scope==null||command==null||command.expectedRevision()!=0||!identity.actorId().equals(command.actor())||!identity.tenantId().equals(scope.tenant())||!identity.facilityId().equals(scope.facility()))throw new SecurityException("triage intake unavailable");
  TriageAuthorization.require(identity,evidence,scope.tenant(),scope.facility(),scope.patient(),"triage:intake",command.trustedNow());
  String fingerprint=fingerprint(scope,command,"ARRIVAL",String.valueOf(appointment));
  return database.transaction(tx->{
   TriageCase existingCase=tx.loadLocked(scope);
   var previous=tx.receipt(scope,command.commandId());
   if(previous!=null){
      if(!previous.fingerprint().equals(fingerprint)||existingCase==null||!TriageAudit.verify(existingCase)||
         !TriageAuditEvidence.receiptMatchesCase(existingCase,previous))throw new SecurityException("triage intake replay evidence mismatch");
      return new Result(previous.revision(),previous.eventDigest(),true);
   }
   if(existingCase!=null)throw new TriageCommandConflict(false);
   var c=new TriageIntakeService().register(identity,evidence,scope.caseId(),scope.patient(),appointment,command.trustedNow());
   // A genesis event is persisted with the case, outbox and receipt in ONE transaction.
   // The aggregate's mutation chain begins at revision 2; revision 1 is the external genesis event.
   String initialState=TriageAudit.stateDigest(c);
   String genesisDigest=TriageAudit.eventDigest(c,"0".repeat(64),initialState,"ARRIVAL",command.actor(),command.commandId(),command.trustedNow(),1);
   var genesis=new TriageCase.Change(1,"ARRIVAL",command.actor(),command.commandId(),command.trustedNow(),"0".repeat(64),initialState,genesisDigest);
   c=c.withGenesis(genesis);
   tx.create(c);
   tx.appendAuditAndOutbox(c,genesis);
   tx.saveReceipt(scope,new TriagePersistencePort.Receipt(command.commandId(),fingerprint,1,genesisDigest,command.trustedNow()));
   return new Result(1,genesisDigest,false);
  });
 }
}
