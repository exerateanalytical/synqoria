package org.synqoria.operations.mortuary;

import java.time.Instant;
import java.util.UUID;

/** Strict DTOs. No name, free-text medical data, or client-provided approval verdict. */
public final class MortuaryModels {
 private MortuaryModels(){}
 public record Intake(UUID decedentRef,String intakeRef,String deathRef,
  String originatingUnitRef,String authorizationRef,String belongingsSealRef,
  UUID receivingStaffId,UUID commandId){}
 public record Change(String action,long expectedVersion,UUID commandId,String evidenceRef,
  String storageCode,UUID receivingStaffId,String recipientRef,String authorityRef,
  String sealRef,String conditionCode,UUID witnessStaffId,String belongingsReleaseRef){
   public Change(String action,long expectedVersion,UUID commandId,String evidenceRef,
    String storageCode,UUID receivingStaffId,String recipientRef,String authorityRef){
    this(action,expectedVersion,commandId,evidenceRef,storageCode,receivingStaffId,
     recipientRef,authorityRef,null,null,null,null);
   }
  }
 public record CaseView(UUID caseId,UUID decedentRef,String originatingUnitRef,
  String belongingsSealRef,UUID intakeReceivingStaff,String state,long version,
                        String currentStorage,Instant receivedAt,Instant releasedAt){}
 public record CustodyView(long version,String eventType,String fromStorage,String toStorage,
                           UUID actorId,UUID receivingStaffId,String evidenceRef,Instant occurredAt,
  String sealRef,String conditionCode,UUID witnessStaffId,String belongingsRef){}
 public record ApprovalView(String stage,UUID actorId,long caseVersion,Instant createdAt){}
 public record AuditView(long version,String action,String fromState,String toState,
                         UUID actorId,Instant occurredAt){}
}
