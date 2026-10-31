package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0025. Independent grant epoch check; must be loaded from authoritative IAM, never a client header. */
public record ReviewerGrantEpoch(UUID sessionId,UUID reviewerId,long currentPolicyRevision,Instant lastVerifiedAt,boolean revoked) {
 public ReviewerGrantEpoch {if(sessionId==null||reviewerId==null||currentPolicyRevision<0||lastVerifiedAt==null)throw new IllegalArgumentException("Invalid grant epoch");}
 public boolean matches(VerifiedReviewerSession session,Instant now){
  return session!=null&&now!=null&&!revoked&&session.sessionId().equals(sessionId)&&session.reviewerId().equals(reviewerId)
   &&session.policyRevision()==currentPolicyRevision&&!now.isBefore(lastVerifiedAt)
   &&!lastVerifiedAt.isBefore(now.minusSeconds(15))&&now.isBefore(session.expiresAt());
 }
}
