package org.synqoria.scheduling;
import java.util.UUID;
/** Deny cross-cell/tenant/facility scheduling access; evidence is provided by trusted server only. */
public record SchedulingScope(UUID cellId, UUID tenantId, UUID facilityId) {
 public SchedulingScope { if(cellId==null||tenantId==null||facilityId==null)throw new IllegalArgumentException("Missing scope"); }
 public void requireSame(SchedulingScope other){if(other==null||!this.equals(other))throw new SecurityException("Scoped resource unavailable");}
}
