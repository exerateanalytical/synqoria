package org.synqoria.registration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
/** P05-B0016. Construct only after a trusted server-side identity verifier, never from a client JSON body. */
public record VerifiedReviewerSession(UUID tenantId, UUID facilityId, UUID reviewerId, UUID sessionId,
 Set<String> capabilities, long policyRevision, Instant authenticatedAt, Instant expiresAt, boolean revoked) {
 public VerifiedReviewerSession {
  if (tenantId==null||facilityId==null||reviewerId==null||sessionId==null||capabilities==null||authenticatedAt==null||expiresAt==null
      ||!expiresAt.isAfter(authenticatedAt)||policyRevision<0) throw new IllegalArgumentException("Invalid reviewer session");
  capabilities=Set.copyOf(capabilities);
 }
 public boolean authorizes(UUID tenant,UUID facility,UUID registrar,long minimumPolicyRevision,Instant now){
  return now!=null&&!revoked&&tenantId.equals(tenant)&&facilityId.equals(facility)&&!reviewerId.equals(registrar)
   &&capabilities.contains("patient:identity-review:approve")&&policyRevision>=minimumPolicyRevision
   &&!now.isBefore(authenticatedAt)&&now.isBefore(expiresAt)
   &&!authenticatedAt.isBefore(now.minusSeconds(300));
 }
}
