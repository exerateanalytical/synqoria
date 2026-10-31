package org.synqoria.registration;
/** P05-B0022. Public API cannot invoke the package-private JDBC test seam. No write release exists. */
public final class RegistrationProductionBoundary {
 private RegistrationProductionBoundary(){}
 public enum State { LOCKED_PENDING_TRUSTED_IAM, LOCKED_PENDING_DATABASE_AND_CLINICAL_APPROVAL }
 public static State admission(boolean identityVerified,boolean rlsTested,boolean clinicalApproved){
  if(!identityVerified)return State.LOCKED_PENDING_TRUSTED_IAM;
  return State.LOCKED_PENDING_DATABASE_AND_CLINICAL_APPROVAL;
 }
}
