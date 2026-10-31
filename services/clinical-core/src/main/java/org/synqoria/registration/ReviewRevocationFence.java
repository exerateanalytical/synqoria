package org.synqoria.registration;
import java.time.Instant;
import java.util.UUID;
/** P05-B0018. A revoked or superseded decision can never be reused for a new registration. */
public record ReviewRevocationFence(UUID reviewId,long storedRevision,long candidateRevision,boolean revoked,
 Instant expiresAt,Instant lastCheckedAt) {
 public ReviewRevocationFence {
  if(reviewId==null||storedRevision<1||candidateRevision<0||expiresAt==null||lastCheckedAt==null)
   throw new IllegalArgumentException("Invalid review revision");
 }
 public boolean freshAndUnrevoked(UUID requestedReview,long expectedStoredRevision,long expectedCandidateRevision,Instant now){
  return now!=null&&reviewId.equals(requestedReview)&&!revoked&&storedRevision==expectedStoredRevision
   &&candidateRevision==expectedCandidateRevision&&!now.isBefore(lastCheckedAt)
   &&now.isBefore(expiresAt)&&!lastCheckedAt.isBefore(now.minusSeconds(15));
 }
}
