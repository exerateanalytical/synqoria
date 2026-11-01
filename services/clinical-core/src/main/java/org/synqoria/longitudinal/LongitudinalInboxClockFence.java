package org.synqoria.longitudinal;
import java.time.*;import java.util.Objects;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;
/** Validate timestamp metadata without confusing wall clock with causal event revision. */
public final class LongitudinalInboxClockFence {
 private LongitudinalInboxClockFence() {}
 public static final Duration MAX_FUTURE_SKEW=Duration.ofMinutes(2);
 public static final Duration MAX_AGE=Duration.ofDays(7);
 public static void check(Envelope event,Instant now,Instant tenantEpoch){
   Objects.requireNonNull(event);Objects.requireNonNull(now);Objects.requireNonNull(tenantEpoch);
   if(event.occurredAt().isBefore(tenantEpoch)||event.occurredAt().isBefore(now.minus(MAX_AGE))||
      event.occurredAt().isAfter(now.plus(MAX_FUTURE_SKEW)))
     throw new SecurityException("inbox event clock unavailable");
 }
}
