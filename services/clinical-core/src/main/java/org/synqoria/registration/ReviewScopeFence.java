package org.synqoria.registration;
import java.util.UUID;
/** P05-B0028: scope/reviewer separation precondition only; no authorization authority. */
public final class ReviewScopeFence {
 public record Scope(UUID tenant,UUID facility,UUID candidate,UUID command,UUID registrar,UUID reviewer,long candidateRevision,long expectedRevision){
  public Scope{if(tenant==null||facility==null||candidate==null||command==null||registrar==null||reviewer==null||candidateRevision<0||expectedRevision<0)throw new IllegalArgumentException("Incomplete review scope");}
 }
 private ReviewScopeFence(){}
 public static boolean permits(Scope scope,UUID activeTenant,UUID activeFacility,UUID activeCommand,long currentRevision){
  return scope!=null&&activeTenant!=null&&activeFacility!=null&&activeCommand!=null&&scope.tenant().equals(activeTenant)&&scope.facility().equals(activeFacility)
   &&scope.command().equals(activeCommand)&&scope.candidateRevision()==scope.expectedRevision()&&currentRevision==scope.expectedRevision()
   &&!scope.reviewer().equals(scope.registrar());
 }
}
