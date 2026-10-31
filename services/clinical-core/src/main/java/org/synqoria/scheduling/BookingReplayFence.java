package org.synqoria.scheduling;
import java.util.*;import java.security.MessageDigest;
/** Idempotency preflight: DB uniqueness and serializable transaction still mandatory. */
public final class BookingReplayFence {
 public enum Outcome {NEW,SAFE_REPLAY,CONFLICT}
 public record Claim(UUID commandId,SchedulingScope scope,byte[] digest){
 public Claim{if(commandId==null||scope==null||digest==null||digest.length!=32)throw new IllegalArgumentException("Invalid claim");digest=digest.clone();}
 @Override public byte[] digest(){return digest.clone();}
 }
 public static Outcome evaluate(Claim incoming,Claim persisted){
 if(incoming==null)throw new IllegalArgumentException("Missing incoming claim");
 if(persisted==null)return Outcome.NEW;
 if(!incoming.commandId().equals(persisted.commandId())||!incoming.scope().equals(persisted.scope()))return Outcome.CONFLICT;
 return MessageDigest.isEqual(incoming.digest(),persisted.digest())?Outcome.SAFE_REPLAY:Outcome.CONFLICT;
 }
 private BookingReplayFence(){}
}
