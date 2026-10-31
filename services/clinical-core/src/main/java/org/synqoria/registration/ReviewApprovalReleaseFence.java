package org.synqoria.registration;
/** P05-B0020. No caller-provided booleans may unfreeze clinical registration submission. */
public final class ReviewApprovalReleaseFence {
 public enum Decision { LOCKED_PENDING_PRODUCTION_IDENTITY,LOCKED_PENDING_DATABASE_AUTHORITY,LOCKED_PENDING_CLINICAL_SIGNOFF }
 private ReviewApprovalReleaseFence(){}
 public static Decision admission(boolean verifiedReviewer,boolean durableDecision,boolean rowLevelSecurityValidated,
 boolean clinicalPolicyApproved){
  if(!verifiedReviewer)return Decision.LOCKED_PENDING_PRODUCTION_IDENTITY;
  if(!durableDecision||!rowLevelSecurityValidated)return Decision.LOCKED_PENDING_DATABASE_AUTHORITY;
  return Decision.LOCKED_PENDING_CLINICAL_SIGNOFF; // actual release requires a separate hospital-signed server configuration
 }
}
