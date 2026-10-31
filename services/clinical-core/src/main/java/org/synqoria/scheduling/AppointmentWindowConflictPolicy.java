package org.synqoria.scheduling;
import java.time.Instant;
/** Half-open intervals: adjacent appointments do not conflict. */
public final class AppointmentWindowConflictPolicy {
 private AppointmentWindowConflictPolicy() {}
 public static boolean overlaps(Instant firstStart,Instant firstEnd,Instant secondStart,Instant secondEnd) {
  validate(firstStart,firstEnd);validate(secondStart,secondEnd);
  return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
 }
 private static void validate(Instant start,Instant end) {
  if(start==null||end==null||!end.isAfter(start)) throw new IllegalArgumentException("invalid appointment interval");
 }
}
