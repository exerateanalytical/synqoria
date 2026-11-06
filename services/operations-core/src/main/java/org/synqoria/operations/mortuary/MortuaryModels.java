package org.synqoria.operations.mortuary;

import java.time.Instant;
import java.util.UUID;

/** Strict DTOs. No name, free-text medical data, or client-provided approval verdict. */
public final class MortuaryModels {
 private MortuaryModels(){}
 public record Intake(UUID decedentRef,String intakeRef,String deathRef,UUID commandId){}
 public record Change(String action,long expectedVersion,UUID commandId,String evidenceRef,
  String storageCode,UUID receivingStaffId,String recipientRef,String authorityRef){}
 public record CaseView(UUID caseId,UUID decedentRef,String state,long version,
                        String currentStorage,Instant receivedAt,Instant releasedAt){}
 public record CustodyView(long version,String eventType,String fromStorage,String toStorage,
                           UUID actorId,UUID receivingStaffId,String evidenceRef,Instant occurredAt){}
 public record ApprovalView(String stage,UUID actorId,long caseVersion,Instant createdAt){}
 public record AuditView(long version,String action,String fromState,String toState,
                         UUID actorId,Instant occurredAt){}
}
