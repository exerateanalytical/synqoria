package org.synqoria.scheduling;
import java.util.UUID;
/** Stable cursor is bound to the same tenant, facility and patient scope. */
public record AppointmentScopedCursor(UUID tenantId,UUID facilityId,UUID patientId,long revision,UUID appointmentId){
 public AppointmentScopedCursor{
  if(tenantId==null||facilityId==null||patientId==null||appointmentId==null||revision<=0)throw new IllegalArgumentException("invalid scoped cursor");
 }
 public void requireScope(UUID tenant,UUID facility,UUID patient){
  if(!tenantId.equals(tenant)||!facilityId.equals(facility)||!patientId.equals(patient))throw new SecurityException("scope mismatch");
 }
}
