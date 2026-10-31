package org.synqoria.registration;
import java.time.Instant;
import java.util.UUID;
/** P05-B0021. Pure internal preflight, deliberately not a public registration POST or proof issuer. */
public final class RegistrationReviewPreflight {
 public enum Result { DENIED_UNVERIFIED_REVIEWER,DENIED_STALE_DECISION,REVIEWED_NOT_RELEASED }
 private RegistrationReviewPreflight(){}
 public static Result evaluate(VerifiedReviewerSession reviewer,UUID tenant,UUID facility,UUID registrar,
  UUID candidate,UUID command,UUID patient,long candidateRevision,long currentRevision,long policyRevision,
  ReviewRevocationFence decision,UUID reviewId,long decisionRevision,Instant now){
  if(!ReviewerSeparationFence.allow(reviewer,tenant,facility,registrar,candidate,command,patient,
      candidateRevision,currentRevision,policyRevision,now))return Result.DENIED_UNVERIFIED_REVIEWER;
  if(decision==null||!decision.freshAndUnrevoked(reviewId,decisionRevision,candidateRevision,now))return Result.DENIED_STALE_DECISION;
  return Result.REVIEWED_NOT_RELEASED;
 }
}
