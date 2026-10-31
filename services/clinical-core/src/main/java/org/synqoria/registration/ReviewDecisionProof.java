package org.synqoria.registration;
import java.time.Instant;
import java.util.UUID;
/** Immutable review evidence descriptor; proof bytes do not themselves authorize a write. */
public record ReviewDecisionProof(UUID tenantId, UUID facilityId, UUID reviewId, UUID reviewerId,
 UUID registrarId, UUID candidateId, UUID commandId, UUID patientId, long candidateRevision, byte[] fingerprint, Instant decidedAt,
 Instant expiresAt, String outcome, String keyId, byte[] mac) {
 public ReviewDecisionProof {
  if(tenantId==null||facilityId==null||reviewId==null||reviewerId==null||registrarId==null||candidateId==null||commandId==null||patientId==null||decidedAt==null||expiresAt==null
    ||fingerprint==null||fingerprint.length!=32||mac==null||mac.length!=32||candidateRevision<0||!expiresAt.isAfter(decidedAt)
    ||!"NO_UNRESOLVED_MATCH".equals(outcome)||keyId==null||!keyId.matches("[A-Za-z0-9._-]{3,64}")||reviewerId.equals(registrarId))
      throw new IllegalArgumentException("Invalid review evidence");
  fingerprint=fingerprint.clone();mac=mac.clone();
 }
 @Override public byte[] fingerprint(){return fingerprint.clone();}
 @Override public byte[] mac(){return mac.clone();}
}
