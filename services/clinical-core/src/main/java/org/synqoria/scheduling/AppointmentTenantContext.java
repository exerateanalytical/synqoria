package org.synqoria.scheduling;
import java.util.UUID;
public record AppointmentTenantContext(UUID tenantId, UUID facilityId, UUID actorId, UUID patientId) {
 public AppointmentTenantContext { if(tenantId==null||facilityId==null||actorId==null||patientId==null) throw new IllegalArgumentException("complete identity scope required"); }
 public void requireSameTenant(UUID tenant) { if(!tenantId.equals(tenant))throw new SecurityException("tenant scope denied"); }
 public void requireSamePatient(UUID patient) { if(!patientId.equals(patient))throw new SecurityException("patient scope denied"); }
}
