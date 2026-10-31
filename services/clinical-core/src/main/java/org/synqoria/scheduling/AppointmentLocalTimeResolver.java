package org.synqoria.scheduling;
import java.time.*;
import java.util.List;
/** Resolves wall-clock appointments only with an explicit, valid zone offset. */
public final class AppointmentLocalTimeResolver {
 private AppointmentLocalTimeResolver() {}
 public static Instant resolve(LocalDateTime local, ZoneId zone, ZoneOffset selectedOffset) {
  if(local==null || zone==null || selectedOffset==null) throw new IllegalArgumentException("missing local time context");
  List<ZoneOffset> offsets=zone.getRules().getValidOffsets(local);
  if(!offsets.contains(selectedOffset)) throw new IllegalArgumentException("nonexistent or mismatched local time offset");
  return local.toInstant(selectedOffset);
 }
}
