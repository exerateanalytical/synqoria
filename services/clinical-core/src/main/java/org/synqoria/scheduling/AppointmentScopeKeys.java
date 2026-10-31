package org.synqoria.scheduling;
import java.util.UUID;
public final class AppointmentScopeKeys {
 private AppointmentScopeKeys(){}
 public static void requireMatch(AppointmentTenantContext context,UUID tenant,UUID facility,UUID patient){
  if(context==null||tenant==null||facility==null||patient==null)throw new SecurityException("scope required");
  if(!context.tenantId().equals(tenant)||!context.facilityId().equals(facility)||!context.patientId().equals(patient))throw new SecurityException("scope denied");
 }
}
