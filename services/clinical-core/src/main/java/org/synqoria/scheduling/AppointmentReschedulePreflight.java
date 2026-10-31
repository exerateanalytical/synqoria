package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;import org.synqoria.reliability.OutboxIntent;
import java.time.Instant;import java.util.*;
/** Revision-checked reschedule; must recheck exclusion constraints inside a DB transaction. */
public final class AppointmentReschedulePreflight {
 public enum Outcome { READY_FOR_RESTRICTED_TRANSACTION, DENIED_NOT_DISCLOSED, STALE, INVALID_STATE, INVALID_WINDOW, RESOURCE_CONFLICT }
 public record Decision(Outcome outcome,AppointmentAggregate proposed,OutboxIntent event){public Decision{if(outcome!=Outcome.READY_FOR_RESTRICTED_TRANSACTION&&(proposed!=null||event!=null))throw new IllegalArgumentException("leak");}}
 public Decision prepare(TrustedAccessContext ctx,AppointmentAggregate current,AppointmentWindow target,long expected,Instant now,ResourceReservationIndex occupied){
  if(!new AppointmentAccessPolicy().permits(ctx,current,AppointmentAccessPolicy.Action.MANAGE))return deny(Outcome.DENIED_NOT_DISCLOSED);
  if(current.revision()!=expected)return deny(Outcome.STALE);
  if(current.state()!=AppointmentAggregate.State.CONFIRMED)return deny(Outcome.INVALID_STATE);
  if(target==null||now==null||!target.start().isAfter(now)||target.equals(current.window()))return deny(Outcome.INVALID_WINDOW);
  var next=new AppointmentAggregate(current.id(),current.tenant(),current.facility(),current.patient(),current.resource(),target,current.state(),expected+1,null);
  if(occupied==null||occupied.conflictsExcludingSelf(next))return deny(Outcome.RESOURCE_CONFLICT);
  var digest=AppointmentCommandFingerprint.sha256(next,"RESCHEDULE",expected);
  return new Decision(Outcome.READY_FOR_RESTRICTED_TRANSACTION,next,OutboxIntent.create(current.tenant(),current.id(),next.revision(),"appointment.rescheduled","1",digest));
 }
 private static Decision deny(Outcome o){return new Decision(o,null,null);}
}
