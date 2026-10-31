package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;
import java.time.*;import java.util.*;
public class AppointmentWorkflowPreflightTest {
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var tenant=UUID.randomUUID();var facility=UUID.randomUUID();var patient=UUID.randomUUID();var actor=UUID.randomUUID();var resource=UUID.randomUUID();
  var window=new AppointmentWindow(Instant.parse("2026-11-02T09:00:00Z"),Instant.parse("2026-11-02T09:30:00Z"),ZoneId.of("Africa/Douala"));
  var booking=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,window,AppointmentAggregate.State.REQUESTED,1,null);
  var ctx=new TrustedAccessContext(tenant,facility,actor,Set.of("appointment:manage"),Set.of(patient));
  var reservations=new ResourceReservationIndex();var service=new AppointmentWorkflowPreflight(new AppointmentAccessPolicy(),reservations);
  var decision=service.prepareConfirm(ctx,booking,1);
  ok(decision.result()==AppointmentWorkflowPreflight.Result.READY_FOR_TRANSACTION);
  ok(decision.proposed().state()==AppointmentAggregate.State.CONFIRMED);
  ok(decision.event().aggregateVersion()==2);
  ok(decision.event().tenantId().equals(tenant));
  ok(reservations.activeCount(tenant,facility,resource)==0); // preflight MUST NOT mutate
  ok(service.prepareConfirm(ctx,booking,9).result()==AppointmentWorkflowPreflight.Result.STALE_REVISION);
  var foreign=new TrustedAccessContext(UUID.randomUUID(),facility,actor,ctx.capabilities(),ctx.permittedPatientIds());
  var hidden=service.prepareConfirm(foreign,booking,1);
  ok(hidden.result()==AppointmentWorkflowPreflight.Result.DENIED_NOT_DISCLOSED && hidden.proposed()==null && hidden.event()==null);
  var confirmed=booking.transition(AppointmentAggregate.State.CONFIRMED,1,null);
  ok(reservations.reserve(confirmed));
  var conflict=service.prepareConfirm(ctx,booking,1);
  ok(conflict.result()==AppointmentWorkflowPreflight.Result.RESOURCE_CONFLICT && conflict.commandDigest()==null);
  ok(service.prepareConfirm(ctx,confirmed,2).result()==AppointmentWorkflowPreflight.Result.INVALID_TRANSITION);
  var later=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,resource,new AppointmentWindow(window.end(),window.end().plusSeconds(600),window.zone()),AppointmentAggregate.State.REQUESTED,1,null);
  ok(service.prepareConfirm(ctx,later,1).result()==AppointmentWorkflowPreflight.Result.READY_FOR_TRANSACTION);
  System.out.println("PASS P06-B0006 "+n+" preflight authorization/transition/conflict/outbox assertions (no DB write)");
 }
}
