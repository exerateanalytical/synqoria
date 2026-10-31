package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;import org.synqoria.reliability.OutboxIntent;import java.util.*;
/** Arrival, no-show and fulfillment require trusted facility operator and current revision. */
public final class AppointmentAttendancePreflight {
 public enum Outcome { READY_FOR_RESTRICTED_TRANSACTION, DENIED_NOT_DISCLOSED, STALE, INVALID_TRANSITION, INVALID_REASON }
 public record Decision(Outcome outcome,AppointmentAggregate proposed,OutboxIntent event){public Decision{if(outcome!=Outcome.READY_FOR_RESTRICTED_TRANSACTION&&(proposed!=null||event!=null))throw new IllegalArgumentException("leak");}}
 public Decision prepare(TrustedAccessContext ctx,AppointmentAggregate current,long expected,AppointmentAggregate.State next,String reason){
  if(!new AppointmentAccessPolicy().permits(ctx,current,AppointmentAccessPolicy.Action.MANAGE))return deny(Outcome.DENIED_NOT_DISCLOSED);
  if(current.revision()!=expected)return deny(Outcome.STALE);
  if(next!=AppointmentAggregate.State.ARRIVED&&next!=AppointmentAggregate.State.NO_SHOW&&next!=AppointmentAggregate.State.FULFILLED)return deny(Outcome.INVALID_TRANSITION);
  if(next==AppointmentAggregate.State.NO_SHOW&&(reason==null||reason.isBlank()||reason.length()>500))return deny(Outcome.INVALID_REASON);
  AppointmentAggregate proposed;
  try{proposed=current.transition(next,expected,reason);}catch(IllegalStateException|IllegalArgumentException e){return deny(Outcome.INVALID_TRANSITION);}
  var digest=AppointmentCommandFingerprint.sha256(current,next.name(),expected);
  if(next==AppointmentAggregate.State.NO_SHOW)digest=AppointmentReasonDigest.bind(digest,reason);
  return new Decision(Outcome.READY_FOR_RESTRICTED_TRANSACTION,proposed,OutboxIntent.create(current.tenant(),current.id(),proposed.revision(),"appointment."+next.name().toLowerCase(Locale.ROOT),"1",digest));
 }
 private static Decision deny(Outcome o){return new Decision(o,null,null);}
}
