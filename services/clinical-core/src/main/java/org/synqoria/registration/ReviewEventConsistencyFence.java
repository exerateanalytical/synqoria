package org.synqoria.registration;
/** P05-B0035: reject review event gaps, resurrection and contradictory snapshots. */
public final class ReviewEventConsistencyFence {
 private ReviewEventConsistencyFence(){}
 public static boolean consistent(ReviewEventTransition.Snapshot before,ReviewEventTransition.Snapshot after,ReviewEventTransition.Kind kind){
  if(before==null||after==null||kind==null)return false;
  try{return ReviewEventTransition.apply(before,after.version(),kind).equals(after);}catch(IllegalArgumentException e){return false;}
 }
}
