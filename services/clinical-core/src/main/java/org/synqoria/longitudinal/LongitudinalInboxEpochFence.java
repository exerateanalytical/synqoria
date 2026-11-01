package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
/** Reject stale claims after worker generation changes; token is never authoritative on its own. */
public final class LongitudinalInboxEpochFence {
 private LongitudinalInboxEpochFence() {}
 public record Claim(UUID consumer,UUID event,UUID token,long epoch,Instant expiresAt){
  public Claim {Objects.requireNonNull(consumer);Objects.requireNonNull(event);Objects.requireNonNull(token);
   Objects.requireNonNull(expiresAt);if(epoch<1)throw new IllegalArgumentException("invalid claim epoch");}
 }
 public static void require(Claim supplied,UUID expectedConsumer,UUID expectedEvent,UUID expectedToken,
                            long currentEpoch,Instant now){
  Objects.requireNonNull(supplied);Objects.requireNonNull(now);
  if(!supplied.consumer().equals(expectedConsumer)||!supplied.event().equals(expectedEvent)||
     !supplied.token().equals(expectedToken)||supplied.epoch()!=currentEpoch||
     !now.isBefore(supplied.expiresAt()))throw new SecurityException("inbox lease generation unavailable");
 }
}
