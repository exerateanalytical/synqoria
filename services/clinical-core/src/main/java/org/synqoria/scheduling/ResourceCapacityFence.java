package org.synqoria.scheduling;
import java.util.*;
/** Scoped concurrent-capacity preflight; DB lock/exclusion required to prevent races. */
public final class ResourceCapacityFence {
 public static boolean hasCapacity(SchedulingScope target,UUID resourceId,AppointmentWindow candidate,int capacity,Collection<ResourceConflictFence.Reservation> bookings){
 if(target==null||resourceId==null||candidate==null||bookings==null||capacity<1||capacity>10000)throw new IllegalArgumentException("Invalid capacity");
 int occupied=0;for(var b:bookings){if(b==null)throw new IllegalArgumentException("Null booking");
 if(b.active()&&b.scope().equals(target)&&b.resourceId().equals(resourceId)&&b.window().overlaps(candidate)){if(++occupied>=capacity)return false;}}
 return true;
 }
 private ResourceCapacityFence(){}
}
