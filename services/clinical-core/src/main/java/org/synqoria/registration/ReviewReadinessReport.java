package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0026. Reports evidence state; NEVER yields an approval to mutate a patient. */
public final class ReviewReadinessReport {
 public enum Status { IAM_NOT_VERIFIED, REVIEW_REVOKED_OR_STALE, DATABASE_NOT_VERIFIED, REVIEWED_NOT_RELEASED }
 private ReviewReadinessReport(){}
 public static Status evaluate(VerifiedReviewerSession session,ReviewerGrantEpoch grant,ReviewRevocationFence revocation,
 UUID tenant,UUID facility,UUID registrar,UUID reviewId,long eventVersion,long candidateRevision,long requiredPolicyRevision,
 boolean committedReviewRow,boolean rlsIsolationTested,Instant now){
  if(session==null||grant==null||!grant.matches(session,now)||!session.authorizes(tenant,facility,registrar,requiredPolicyRevision,now))return Status.IAM_NOT_VERIFIED;
  if(revocation==null||!revocation.freshAndUnrevoked(reviewId,eventVersion,candidateRevision,now))return Status.REVIEW_REVOKED_OR_STALE;
  if(!committedReviewRow||!rlsIsolationTested)return Status.DATABASE_NOT_VERIFIED;
  return Status.REVIEWED_NOT_RELEASED;
 }
}
