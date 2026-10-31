package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** Immutable cell-local location hierarchy; no identity, privilege or database authority by itself. */
public final class LocationScopePolicy {
 public record Department(UUID tenant,UUID facility,UUID id,boolean active) {
  public Department {Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(id);}
 }
 public record Location(UUID tenant,UUID facility,UUID department,UUID id,boolean active) {
  public Location {Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(department);Objects.requireNonNull(id);}
 }
 public static boolean permits(TrustedAccessContext caller,FacilityDirectory directory,UUID expectedCell,
                 Department department,Location location){
  if(caller==null||directory==null||expectedCell==null||department==null||location==null)return false;
  return directory.permitsActiveFacility(caller,expectedCell) && department.active() && location.active()
   && caller.tenantId().equals(department.tenant()) && caller.tenantId().equals(location.tenant())
   && caller.facilityId().equals(department.facility()) && caller.facilityId().equals(location.facility())
   && department.id().equals(location.department());
 }
}
