package org.synqoria.scheduling;

import java.time.*;
import java.util.List;
import java.util.Objects;

/** M02 technical time-window contract; no clinical duration/priority rules are implied. */
public record AppointmentWindow(Instant start, Instant end, ZoneId zone) {
  public AppointmentWindow {
    Objects.requireNonNull(start); Objects.requireNonNull(end); Objects.requireNonNull(zone);
    if (!end.isAfter(start) || Duration.between(start,end).compareTo(Duration.ofHours(24))>0)
      throw new IllegalArgumentException("Invalid technical appointment time bounds");
  }
  /** Reject nonexistent DST times and require explicit offset when local time is ambiguous. */
  public static AppointmentWindow fromLocal(LocalDateTime local, ZoneId zone, ZoneOffset offset, Duration duration) {
    Objects.requireNonNull(local); Objects.requireNonNull(zone); Objects.requireNonNull(duration);
    List<ZoneOffset> valid = zone.getRules().getValidOffsets(local);
    if (valid.isEmpty() || (valid.size()>1 && offset==null))
      throw new IllegalArgumentException("Nonexistent or ambiguous local time");
    ZoneOffset chosen = offset==null ? valid.get(0) : offset;
    if (!valid.contains(chosen)) throw new IllegalArgumentException("Offset not valid for zone and local time");
    Instant start=local.toInstant(chosen);
    return new AppointmentWindow(start,start.plus(duration),zone);
  }
  public boolean overlaps(AppointmentWindow other) {
    Objects.requireNonNull(other);
    return start.isBefore(other.end) && other.start.isBefore(end);
  }
}
