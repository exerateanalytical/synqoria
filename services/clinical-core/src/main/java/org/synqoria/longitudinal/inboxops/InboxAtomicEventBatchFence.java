package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0159: Bounded contiguous same-patient event batch validation before DB transaction. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxAtomicEventBatchFence {
 private InboxAtomicEventBatchFence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Event(UUID tenant,UUID facility,UUID patient,UUID encounter,long revision,String digest){
  public Event{Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(patient);Objects.requireNonNull(encounter);require(revision>=1&&digest!=null&&digest.matches("[0-9a-f]{64}"));}
 }
 public static long verify(List<Event> events,long committedRevision){
  Objects.requireNonNull(events);require(!events.isEmpty()&&events.size()<=32&&committedRevision>=0);
  Event first=events.get(0);long next=committedRevision;
  for(Event e:events){require(e!=null&&e.tenant().equals(first.tenant())&&e.facility().equals(first.facility())&&e.patient().equals(first.patient())&&e.encounter().equals(first.encounter())&&e.revision()==next+1);next=e.revision();}
  return next;
 }

}
