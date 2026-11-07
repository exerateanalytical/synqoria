package org.synqoria.operations.mortuary;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;
import static org.junit.jupiter.api.Assertions.*;

final class MortuaryWorkflowTest {
 private static final UUID A=UUID.randomUUID(),B=UUID.randomUUID(),C=UUID.randomUUID();
 private static final UUID CELL=UUID.randomUUID(),FAC=UUID.randomUUID();
 private OperationWorkflow.Actor actor(UUID who,String capability){
  return new OperationWorkflow.Actor(who,CELL,FAC,Set.of(capability));
 }
 private MortuaryWorkflow.Snapshot state(MortuaryWorkflow.State status,long version){
  return new MortuaryWorkflow.Snapshot(UUID.randomUUID(),CELL,FAC,UUID.randomUUID(),
    status,version,status==MortuaryWorkflow.State.STORED?"BAY-01":null,A,B);
 }
 @Test void finiteStateMachineNeverSkipsVerificationOrReleaseReview(){
  var intake=state(MortuaryWorkflow.State.RECEIVED,1);
  assertEquals(MortuaryWorkflow.State.IDENTIFIED,MortuaryWorkflow.decide(
   intake,MortuaryWorkflow.Action.IDENTIFY,1,actor(B,"m27:identify")).next());
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(
   intake,MortuaryWorkflow.Action.REVIEW_RELEASE,1,actor(B,"m27:review")));
  var stored=state(MortuaryWorkflow.State.STORED,3);
  assertEquals(MortuaryWorkflow.State.STORED,MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.TRANSFER,3,actor(B,"m27:transfer")).next());
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.HANDOVER,3,actor(B,"m27:handover")));
 }
 @Test void noCrossCellNoStaleVersionNoMakerCheckerBypass(){
  var stored=state(MortuaryWorkflow.State.STORED,4);
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.REVIEW_RELEASE,3,actor(B,"m27:review")));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.REVIEW_RELEASE,4,actor(A,"m27:review")));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.REVIEW_RELEASE,4,
   new OperationWorkflow.Actor(B,UUID.randomUUID(),FAC,Set.of("m27:review"))));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.decide(stored,
   MortuaryWorkflow.Action.REVIEW_RELEASE,4,actor(B,"m27:handover")));
 }
 @Test void transferRequiresDifferentStorageAndIndependentReceivingStaff(){
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.validateTransfer("BAY-01","BAY-01",A,B));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.validateTransfer("BAY-01","BAY-02",A,A));
  assertDoesNotThrow(()->MortuaryWorkflow.validateTransfer("BAY-01","BAY-02",A,B));
 }
 @Test void releaseRequiresDistinctReviewerAuthorizerAndOriginalMaker(){
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.requireIndependentApprovers(B,B,A));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.requireIndependentApprovers(A,C,A));
  assertThrows(WorkflowViolation.class,()->MortuaryWorkflow.requireIndependentApprovers(B,A,A));
  assertDoesNotThrow(()->MortuaryWorkflow.requireIndependentApprovers(B,C,A));
  assertEquals(10,MortuaryWorkflow.permissions().size());
 }
}
