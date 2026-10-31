package org.synqoria.scheduling;
import java.util.*;
/** M02 lifecycle. All transitions are uncommitted domain operations, not patient writes. */
public record AppointmentAggregate(UUID id, UUID tenant, UUID facility, UUID patient, UUID resource,
                                   AppointmentWindow window, State state, long revision, String reason) {
 public enum State { REQUESTED, CONFIRMED, ARRIVED, FULFILLED, CANCELLED, NO_SHOW }
 public AppointmentAggregate {
  Objects.requireNonNull(id);Objects.requireNonNull(tenant);Objects.requireNonNull(facility);
  Objects.requireNonNull(patient);Objects.requireNonNull(resource);Objects.requireNonNull(window);Objects.requireNonNull(state);
  if(revision<1)throw new IllegalArgumentException("revision");
  if(reason!=null && (reason.isBlank() || reason.length()>500))throw new IllegalArgumentException("invalid reason");
 }
 public AppointmentAggregate transition(State next,long expectedRevision,String rationale){
  Objects.requireNonNull(next);
  if(revision!=expectedRevision)throw new IllegalStateException("stale revision");
  boolean permitted=switch(state){
   case REQUESTED -> next==State.CONFIRMED || next==State.CANCELLED;
   case CONFIRMED -> next==State.ARRIVED || next==State.CANCELLED || next==State.NO_SHOW;
   case ARRIVED -> next==State.FULFILLED;
   default -> false;
  };
  if(!permitted)throw new IllegalStateException("invalid transition");
  if((next==State.CANCELLED || next==State.NO_SHOW) && (rationale==null || rationale.isBlank()))
   throw new IllegalArgumentException("reason required");
  return new AppointmentAggregate(id,tenant,facility,patient,resource,window,next,revision+1,rationale);
 }
}
