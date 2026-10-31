package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.reliability.OutboxIntent;
import java.util.*;
/** No persistence: prepares an auditable command intent; DB commit must revalidate all guards. */
public final class AppointmentWorkflowPreflight {
 public enum Result { READY_FOR_TRANSACTION, DENIED_NOT_DISCLOSED, STALE_REVISION, INVALID_TRANSITION, RESOURCE_CONFLICT }
 public record Decision(Result result,AppointmentAggregate proposed,OutboxIntent event,String commandDigest) {
  public Decision { if(result!=Result.READY_FOR_TRANSACTION && (proposed!=null || event!=null || commandDigest!=null))throw new IllegalArgumentException("leakage"); }
 }
 private final AppointmentAccessPolicy policy;
 private final ResourceReservationIndex reservations;
 public AppointmentWorkflowPreflight(AppointmentAccessPolicy policy,ResourceReservationIndex reservations){this.policy=Objects.requireNonNull(policy);this.reservations=Objects.requireNonNull(reservations);}
 public Decision prepareConfirm(TrustedAccessContext ctx,AppointmentAggregate current,long expectedRevision){
  if(!policy.permits(ctx,current,AppointmentAccessPolicy.Action.MANAGE))return denied(Result.DENIED_NOT_DISCLOSED);
  if(current.revision()!=expectedRevision)return denied(Result.STALE_REVISION);
  if(current.state()!=AppointmentAggregate.State.REQUESTED)return denied(Result.INVALID_TRANSITION);
  // A non-mutating check is only advisory; authoritative transaction MUST use DB exclusion/locks.
  if(reservations.conflicts(current))
   return denied(Result.RESOURCE_CONFLICT);
  var proposed=current.transition(AppointmentAggregate.State.CONFIRMED,expectedRevision,null);
  var fingerprint=AppointmentCommandFingerprint.sha256(current,"CONFIRM",expectedRevision);
  var event=OutboxIntent.create(current.tenant(),current.id(),proposed.revision(),"appointment.confirmed","1",fingerprint);
  return new Decision(Result.READY_FOR_TRANSACTION,proposed,event,fingerprint);
 }
 private static Decision denied(Result reason){return new Decision(reason,null,null,null);}
}
