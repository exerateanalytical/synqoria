package org.synqoria.registration;
import java.util.UUID;
/** P05-B0029: compare immutable idempotency fingerprints; persistence and locking still required. */
public final class ReviewIdempotencyFence {
 public record Key(UUID tenant,UUID facility,UUID command,UUID candidate,long candidateRevision,String requestDigest){
  public Key{if(tenant==null||facility==null||command==null||candidate==null||candidateRevision<0||requestDigest==null||!requestDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid request fingerprint");}
 }
 public enum Outcome { NEW, REPLAY_SAME_REQUEST, CONFLICT, WRONG_SCOPE }
 private ReviewIdempotencyFence(){}
 public static Outcome compare(Key incoming,Key existing){
  if(incoming==null)return Outcome.CONFLICT;
  if(existing==null)return Outcome.NEW;
  if(!incoming.tenant().equals(existing.tenant())||!incoming.facility().equals(existing.facility()))return Outcome.WRONG_SCOPE;
  if(!incoming.command().equals(existing.command()))return Outcome.NEW;
  return incoming.equals(existing)?Outcome.REPLAY_SAME_REQUEST:Outcome.CONFLICT;
 }
}
