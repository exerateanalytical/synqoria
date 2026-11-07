package org.synqoria.operations.mortuary;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/** M27 canonical finite-state safety invariant. No client-claimed approvals. */
public final class MortuaryWorkflow {
 private MortuaryWorkflow(){}
 public enum State { RECEIVED, IDENTIFIED, STORED, RELEASE_REVIEWED, RELEASE_AUTHORIZED, RELEASED }
 public enum Action { IDENTIFY, STORE, TRANSFER, REVIEW_RELEASE, AUTHORIZE_RELEASE, HANDOVER }
 public record Snapshot(UUID caseId, UUID cellId, UUID facilityId, UUID decedentRef,
   State state,long version,String currentStorage,UUID intakeActor,UUID lastActor) {}
 public record Decision(State next,String permission,boolean needsStorage,boolean needsReceiver,
                        boolean releaseSafetyCritical) {}
 public static Decision decide(Snapshot current,Action action,long version,
                               OperationWorkflow.Actor actor){
  Objects.requireNonNull(current);Objects.requireNonNull(action);Objects.requireNonNull(actor);
  if(!current.cellId().equals(actor.cell())||!current.facilityId().equals(actor.facility()))
   throw new WorkflowViolation("scope_denied");
  if(current.version()!=version)throw new WorkflowViolation("stale_version");
  final Decision next=switch(action){
   case IDENTIFY -> requireState(current,State.RECEIVED,
    new Decision(State.IDENTIFIED,"m27:identify",false,false,false));
   case STORE -> requireState(current,State.IDENTIFIED,
    new Decision(State.STORED,"m27:store",true,true,false));
   case TRANSFER -> requireState(current,State.STORED,
    new Decision(State.STORED,"m27:transfer",true,true,false));
   case REVIEW_RELEASE -> requireState(current,State.STORED,
    new Decision(State.RELEASE_REVIEWED,"m27:review",false,false,true));
   case AUTHORIZE_RELEASE -> requireState(current,State.RELEASE_REVIEWED,
    new Decision(State.RELEASE_AUTHORIZED,"m27:authorize",false,false,true));
   case HANDOVER -> requireState(current,State.RELEASE_AUTHORIZED,
    new Decision(State.RELEASED,"m27:handover",false,false,true));
  };
  actor.require(next.permission());
  if(next.releaseSafetyCritical()&&actor.actor().equals(current.intakeActor()))
   throw new WorkflowViolation("intake_maker_cannot_authorize_release");
  return next;
 }
 private static Decision requireState(Snapshot current,State required,Decision result){
  if(current.state()!=required)throw new WorkflowViolation("invalid_state_transition");
  return result;
 }
 public static void validateTransfer(String prior,String destination,UUID actor,UUID recipient){
  if(destination==null||destination.equals(prior) || recipient==null||recipient.equals(actor))
   throw new WorkflowViolation("custody_transfer_requires_independent_receiver");
 }
 public static void requireIndependentApprovers(UUID reviewer,UUID authorizer,UUID intakeActor){
  if(reviewer==null||authorizer==null||reviewer.equals(authorizer)||
     reviewer.equals(intakeActor)||authorizer.equals(intakeActor))
   throw new WorkflowViolation("independent_release_authorization_required");
 }
 public static Set<String> permissions(){
  return Set.of("m27:intake","m27:read","m27:identify","m27:store",
   "m27:transfer","m27:custody_receive","m27:review","m27:authorize",
   "m27:handover","m27:audit");
 }
}
