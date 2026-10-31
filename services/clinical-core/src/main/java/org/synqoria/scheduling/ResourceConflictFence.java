package org.synqoria.scheduling;
import java.util.*;
/** Scoped resource exclusivity; advisory until enforced by a DB exclusion constraint. */
public final class ResourceConflictFence {
 public record Reservation(SchedulingScope scope,UUID resourceId,AppointmentWindow window,boolean active){
  public Reservation{if(scope==null||resourceId==null||window==null)throw new IllegalArgumentException("Invalid reservation");}
 }
 public static boolean conflicts(Reservation candidate,Collection<Reservation> existing){
  if(candidate==null||existing==null)throw new IllegalArgumentException("Missing reservations");
  for(var e:existing){if(e==null)throw new IllegalArgumentException("Null reservation"); if(e.active()&&candidate.active()&&e.scope().equals(candidate.scope())&&e.resourceId().equals(candidate.resourceId())&&e.window().overlaps(candidate.window()))return true;}return false;
 }
 private ResourceConflictFence(){}
}
