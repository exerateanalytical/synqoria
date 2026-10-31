package org.synqoria.scheduling;
import java.time.*;
/** No implicit server clock: caller supplies a trusted request timestamp. */
public final class AppointmentBookingHorizonPolicy {
 private AppointmentBookingHorizonPolicy() {}
 public static void requireWithin(Instant requestedStart,Instant trustedNow,Duration maximumAdvance) {
  if(requestedStart==null||trustedNow==null||maximumAdvance==null||maximumAdvance.isNegative()) throw new IllegalArgumentException("invalid booking horizon");
  if(requestedStart.isBefore(trustedNow)||requestedStart.isAfter(trustedNow.plus(maximumAdvance))) throw new IllegalArgumentException("outside booking horizon");
 }
}
