package org.synqoria.scheduling;
import java.util.*;
/** Thread-safe in-memory conflict reference, NOT a distributed reservation authority.
 * Production requires database exclusion constraint + serializable/locking transactions. */
public final class ResourceReservationIndex {
 private record Key(UUID tenant,UUID facility,UUID resource){}
 private final Map<Key,Map<UUID,AppointmentWindow>> occupied=new HashMap<>();
 public synchronized boolean conflicts(AppointmentAggregate booking){
  Objects.requireNonNull(booking);
  var map=occupied.get(new Key(booking.tenant(),booking.facility(),booking.resource()));
  if(map==null)return false;
  for(var entry:map.entrySet())if(entry.getKey().equals(booking.id()) || entry.getValue().overlaps(booking.window()))return true;
  return false;
 }
 /** Rescheduling advisory: ignore the appointment's own existing reservation, never others. */
 public synchronized boolean conflictsExcludingSelf(AppointmentAggregate booking){
  Objects.requireNonNull(booking);
  var map=occupied.get(new Key(booking.tenant(),booking.facility(),booking.resource()));
  if(map==null)return false;
  for(var entry:map.entrySet())if(!entry.getKey().equals(booking.id()) && entry.getValue().overlaps(booking.window()))return true;
  return false;
 }
 public synchronized boolean reserve(AppointmentAggregate booking){
  Objects.requireNonNull(booking);
  if(booking.state()!=AppointmentAggregate.State.CONFIRMED)return false;
  Key key=new Key(booking.tenant(),booking.facility(),booking.resource());
  var map=occupied.computeIfAbsent(key,k->new HashMap<>());
  for(var e:map.entrySet())if(!e.getKey().equals(booking.id()) && e.getValue().overlaps(booking.window()))return false;
  map.put(booking.id(),booking.window());return true;
 }
 public synchronized boolean release(AppointmentAggregate booking){
  Objects.requireNonNull(booking);
  var map=occupied.get(new Key(booking.tenant(),booking.facility(),booking.resource()));
  if(map==null)return false;
  return map.remove(booking.id(),booking.window());
 }
 public synchronized int activeCount(UUID tenant,UUID facility,UUID resource){
  return occupied.getOrDefault(new Key(tenant,facility,resource),Map.of()).size();
 }
}
