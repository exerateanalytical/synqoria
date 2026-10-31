package org.synqoria.registration;
import java.time.*;import java.util.*;
import org.synqoria.security.TrustedAccessContext;
/** INTERNAL reviewed duplicate-decision authority; cannot be derived from client 'cleared' flag. */
public record RegistrationReviewPermit(UUID tenantId,UUID facilityId,UUID reviewerId,UUID registrarId,
 UUID reviewId,byte[] fingerprint,Instant expiresAt,long candidateRevision,boolean unresolvedCandidate) {
 public RegistrationReviewPermit{
  if(tenantId==null||facilityId==null||reviewerId==null||registrarId==null||reviewId==null||expiresAt==null||fingerprint==null||fingerprint.length!=32||candidateRevision<0)throw new IllegalArgumentException("Invalid review permit");
  fingerprint=fingerprint.clone();
 }
 @Override public byte[] fingerprint(){return fingerprint.clone();}
 public boolean permits(TrustedAccessContext actor,RegistrationCommand cmd,Instant now,long currentRevision){
  return actor!=null&&cmd!=null&&now!=null&&actor.tenantId().equals(tenantId)&&actor.facilityId().equals(facilityId)
   &&actor.actorId().equals(registrarId)&&!reviewerId.equals(registrarId)&&actor.capabilities().contains("patient:register")
   &&!now.isAfter(expiresAt)&&!now.isBefore(cmd.requestedAt().minusSeconds(300))&&currentRevision==candidateRevision
   &&!unresolvedCandidate&&RegistrationFingerprint.same(fingerprint,cmd.fingerprintHmac());
 }
}
