package org.synqoria.registration;
/** P05-B0023. State machine for append-only reviewer decisions; never accepts resurrection or gaps. */
public final class ReviewEventTransition {
 public enum State { NONE, APPROVED, REVOKED }
 public enum Kind { REVIEW_APPROVED, REVIEW_REVOKED }
 public record Snapshot(State state,long version){public Snapshot{if(state==null||version<0||(state==State.NONE)!=(version==0))throw new IllegalArgumentException("Invalid snapshot");}}
 private ReviewEventTransition(){}
 public static Snapshot apply(Snapshot current,long nextVersion,Kind event){
  if(current==null||event==null||nextVersion!=current.version()+1||nextVersion<1)throw new IllegalArgumentException("Nonsequential review event");
  if(current.state()==State.NONE&&event==Kind.REVIEW_APPROVED)return new Snapshot(State.APPROVED,nextVersion);
  if(current.state()==State.APPROVED&&event==Kind.REVIEW_REVOKED)return new Snapshot(State.REVOKED,nextVersion);
  throw new IllegalArgumentException("Invalid review state transition");
 }
}
