package org.synqoria.registration;
import java.util.*;
/** P05-B0050: bounded replay/conflict classifier; even exact replay needs durable DB proof. */
public final class ReviewReplayGuardV2 {
 public enum Result { NEW_CANDIDATE_NOT_AUTHORIZED, REPLAY_REQUIRES_DB_PROOF, CONFLICT, DENIED }
 private ReviewReplayGuardV2(){}
 public static Result inspect(List<ReviewScopedHistoryV2.Event> history,UUID tenant,UUID facility,UUID review,
  UUID command,ReviewEventTransition.Kind kind,String binding){
  if(history==null||tenant==null||facility==null||review==null||command==null||kind==null||binding==null||!binding.matches("[0-9a-f]{64}"))return Result.DENIED;
  if(history.isEmpty())return kind==ReviewEventTransition.Kind.REVIEW_APPROVED?Result.NEW_CANDIDATE_NOT_AUTHORIZED:Result.DENIED;
  if(ReviewScopedHistoryV2.verify(history,tenant,facility,review).isEmpty())return Result.DENIED;
  for(var e:history)if(e.command().equals(command))return e.kind()==kind&&e.binding().equals(binding)?Result.REPLAY_REQUIRES_DB_PROOF:Result.CONFLICT;
  var state=ReviewScopedHistoryV2.verify(history,tenant,facility,review).orElseThrow().state();
  if(state.state()==ReviewEventTransition.State.APPROVED&&kind==ReviewEventTransition.Kind.REVIEW_REVOKED)return Result.NEW_CANDIDATE_NOT_AUTHORIZED;
  return Result.DENIED;
 }
}
