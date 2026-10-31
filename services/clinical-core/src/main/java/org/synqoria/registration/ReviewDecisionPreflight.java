package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0041. Conservative domain preflight; MUST NOT be treated as production IAM. */
public final class ReviewDecisionPreflight {
 public enum Result { PRECONDITIONS_MET_NOT_AUTHORIZED, DENIED }
 private ReviewDecisionPreflight(){}
 public static Result inspect(VerifiedReviewerSession session,ReviewCommandEnvelope command,
  UUID activeTenant,UUID activeFacility,long currentCandidateRevision,long minimumPolicyRevision,Instant now){
  if(session==null||command==null||activeTenant==null||activeFacility==null||now==null||currentCandidateRevision<0
   ||minimumPolicyRevision<0)return Result.DENIED;
  if(!activeTenant.equals(command.tenant())||!activeFacility.equals(command.facility())
   ||!session.reviewerId().equals(command.reviewer())||command.revision()!=currentCandidateRevision
   ||!session.authorizes(command.tenant(),command.facility(),command.registrar(),minimumPolicyRevision,now))return Result.DENIED;
  return Result.PRECONDITIONS_MET_NOT_AUTHORIZED;
 }
}
