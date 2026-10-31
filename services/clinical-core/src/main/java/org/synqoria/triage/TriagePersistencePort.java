package org.synqoria.triage;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/** Transactional port: implementor MUST atomically commit case, audit, outbox and command receipt. */
public interface TriagePersistencePort {
 record Scope(UUID tenant, UUID facility, UUID patient, UUID caseId){
  public Scope {if(tenant==null||facility==null||patient==null||caseId==null)throw new IllegalArgumentException("incomplete triage scope");}
 }
 record Receipt(UUID commandId,String fingerprint,int revision,String eventDigest,Instant committedAt){
  public Receipt {if(commandId==null||!TriageCase.hex(fingerprint)||revision<1||!TriageCase.hex(eventDigest)||committedAt==null)throw new IllegalArgumentException("invalid triage receipt");}
 }
 interface Unit {
  TriageCase loadLocked(Scope scope);
  Receipt receipt(Scope scope,UUID commandId);
  void create(TriageCase c);
  void update(TriageCase before,TriageCase after);
  void appendAuditAndOutbox(TriageCase updated,TriageCase.Change change);
  void saveReceipt(Scope scope,Receipt receipt);
 }
 <T> T transaction(Function<Unit,T> work);
}
