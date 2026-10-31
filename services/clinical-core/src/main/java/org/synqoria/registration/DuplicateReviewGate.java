package org.synqoria.registration;
/** P05-B0002. Unknown identities may proceed only after accountable duplicate review. Never auto-merge. */
public final class DuplicateReviewGate {
 public enum Decision { ALLOW_PROVISIONAL, REVIEW_REQUIRED, BLOCK_AUTOMERGE }
 public static Decision decide(boolean approvedReview, boolean unresolvedCandidate, boolean requestedAutoMerge){
  if(requestedAutoMerge)return Decision.BLOCK_AUTOMERGE;
  if(!approvedReview||unresolvedCandidate)return Decision.REVIEW_REQUIRED;
  return Decision.ALLOW_PROVISIONAL;
 }
 private DuplicateReviewGate(){}
}
