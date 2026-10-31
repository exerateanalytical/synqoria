package org.synqoria.registration;
/** P05-B0034: idempotency conflict never silently overwrites earlier command. */
public final class ReviewCommandReplayFence {
 public enum Decision { FIRST, SAME, CONFLICT, CROSS_SCOPE }
 private ReviewCommandReplayFence(){}
 public static Decision classify(ReviewCommandEnvelope incoming,ReviewCommandEnvelope stored){
  if(incoming==null)throw new IllegalArgumentException("Missing command");
  if(stored==null)return Decision.FIRST;
  if(!incoming.tenant().equals(stored.tenant())||!incoming.facility().equals(stored.facility()))return Decision.CROSS_SCOPE;
  if(!incoming.command().equals(stored.command()))return Decision.FIRST;
  return incoming.equals(stored)?Decision.SAME:Decision.CONFLICT;
 }
}
