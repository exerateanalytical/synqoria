package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;import org.synqoria.reliability.OutboxIntent;import java.util.*;
/** Auditable cancellation, no mutation; release of resources is DB-atomic responsibility. */
public final class AppointmentCancellationPreflight {
 public enum Outcome { READY_FOR_RESTRICTED_TRANSACTION, DENIED_NOT_DISCLOSED, STALE, INVALID_STATE, INVALID_REASON }
 public record Decision(Outcome outcome,AppointmentAggregate proposed,OutboxIntent event){public Decision{if(outcome!=Outcome.READY_FOR_RESTRICTED_TRANSACTION&&(proposed!=null||event!=null))throw new IllegalArgumentException("leak");}}
 public Decision prepare(TrustedAccessContext ctx,AppointmentAggregate current,long expected,String reason){
  if(!new AppointmentAccessPolicy().permits(ctx,current,AppointmentAccessPolicy.Action.MANAGE))return deny(Outcome.DENIED_NOT_DISCLOSED);
  if(current.revision()!=expected)return deny(Outcome.STALE);
  if(current.state()!=AppointmentAggregate.State.REQUESTED&&current.state()!=AppointmentAggregate.State.CONFIRMED)return deny(Outcome.INVALID_STATE);
  if(reason==null||reason.isBlank()||reason.length()>500)return deny(Outcome.INVALID_REASON);
  var next=current.transition(AppointmentAggregate.State.CANCELLED,expected,reason);
  var digest=AppointmentReasonDigest.bind(AppointmentCommandFingerprint.sha256(current,"CANCEL",expected),reason);
  return new Decision(Outcome.READY_FOR_RESTRICTED_TRANSACTION,next,OutboxIntent.create(current.tenant(),current.id(),next.revision(),"appointment.cancelled","1",digest));
 }
 private static Decision deny(Outcome o){return new Decision(o,null,null);}
}
