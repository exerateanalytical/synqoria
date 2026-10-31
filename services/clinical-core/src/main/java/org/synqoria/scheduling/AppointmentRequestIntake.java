package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;
import java.time.Instant;
import java.util.*;
/** Internal request intake only; does not create a database row or grant clinical access. */
public final class AppointmentRequestIntake {
 public enum Outcome { READY_FOR_RESTRICTED_TRANSACTION, DENIED_NOT_DISCLOSED, INVALID_REQUEST }
 public record Decision(Outcome outcome, AppointmentAggregate draft) {
  public Decision { if(outcome!=Outcome.READY_FOR_RESTRICTED_TRANSACTION && draft!=null)throw new IllegalArgumentException("data leak"); }
 }
 public Decision prepare(TrustedAccessContext ctx, UUID id, UUID patient, UUID resource, AppointmentWindow window, Instant now){
  if(ctx==null || !ctx.capabilities().contains("appointment:request") || patient==null || !ctx.permittedPatientIds().contains(patient))
   return new Decision(Outcome.DENIED_NOT_DISCLOSED,null);
  if(id==null || resource==null || window==null || now==null || !window.start().isAfter(now))return new Decision(Outcome.INVALID_REQUEST,null);
  return new Decision(Outcome.READY_FOR_RESTRICTED_TRANSACTION,new AppointmentAggregate(id,ctx.tenantId(),ctx.facilityId(),patient,resource,window,AppointmentAggregate.State.REQUESTED,1,null));
 }
}
