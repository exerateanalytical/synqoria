package org.synqoria.registration;
import java.time.Instant;
import java.util.UUID;
/** P05-B0017. Approval requires distinct independent reviewer and explicitly fresh grant. */
public final class ReviewerSeparationFence {
 private ReviewerSeparationFence(){}
 public static boolean allow(VerifiedReviewerSession session,UUID tenant,UUID facility,UUID registrar,UUID candidate,
  UUID command,UUID patient,long candidateRevision,long observedRevision,long requiredPolicyRevision,Instant now){
  return session!=null&&candidate!=null&&command!=null&&patient!=null&&candidateRevision>=0
   &&candidateRevision==observedRevision&&session.authorizes(tenant,facility,registrar,requiredPolicyRevision,now);
 }
}
