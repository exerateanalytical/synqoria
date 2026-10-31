package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;import java.time.Instant;import java.util.*;
/** P04-B0005 facility role/shift snapshot version gate, independent of database. */
public final class FacilityGrantRevision {
 private FacilityGrantRevision(){}
 public record Snapshot(UUID tenant,UUID facility,UUID actor,long revision,Set<String> capabilities,
                        Instant validFrom,Instant validUntil,boolean facilityActive,boolean shiftActive) {
  public Snapshot{Objects.requireNonNull(capabilities);capabilities=Set.copyOf(capabilities);}
 }
 public static boolean permits(TrustedAccessContext caller,Snapshot snapshot,String capability,long trustedLatestRevision,Instant now){
  if(caller==null||snapshot==null||capability==null||now==null||trustedLatestRevision<1)return false;
  return caller.tenantId().equals(snapshot.tenant()) && caller.facilityId().equals(snapshot.facility())
   && caller.actorId().equals(snapshot.actor()) && snapshot.revision()==trustedLatestRevision
   && snapshot.facilityActive() && snapshot.shiftActive()
   && snapshot.validFrom()!=null && snapshot.validUntil()!=null && !now.isBefore(snapshot.validFrom())
   && now.isBefore(snapshot.validUntil()) && snapshot.validUntil().isAfter(snapshot.validFrom())
   && snapshot.capabilities().contains(capability) && caller.capabilities().contains(capability);
 }
}
