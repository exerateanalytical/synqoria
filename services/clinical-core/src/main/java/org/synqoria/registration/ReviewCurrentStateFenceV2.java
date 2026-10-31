package org.synqoria.registration;
import java.util.*;
/** P05-B0046. Readiness predicate; positive result never grants registration authority. */
public final class ReviewCurrentStateFenceV2 {
 private ReviewCurrentStateFenceV2(){}
 public enum Decision { PRECONDITIONS_ONLY, DENIED }
 public static Decision inspect(List<ReviewScopedHistoryV2.Event> rows,UUID tenant,UUID facility,UUID review,
  long observedVersion,long currentVersion,long observedGrantEpoch,long currentGrantEpoch,boolean sessionVerified){
  if(!sessionVerified||observedVersion<1||currentVersion!=observedVersion||observedGrantEpoch<0
   ||currentGrantEpoch!=observedGrantEpoch)return Decision.DENIED;
  var v=ReviewScopedHistoryV2.verify(rows,tenant,facility,review);
  return v.isPresent()&&v.get().state().version()==currentVersion
   &&v.get().state().state()==ReviewEventTransition.State.APPROVED?Decision.PRECONDITIONS_ONLY:Decision.DENIED;
 }
}
