package org.synqoria.persistence;
import java.time.Instant;
import java.util.UUID;
/** P01-B0021. Minimal audit envelope: no names or medical details in log records. */
public record ClinicalAuditEntry(UUID eventId, UUID correlationId, UUID actorId, UUID tenantId,
  UUID facilityId, UUID patientId, String operation, Instant occurredAt) {
 public ClinicalAuditEntry {
  if(eventId==null||correlationId==null||actorId==null||tenantId==null||facilityId==null||patientId==null
     ||occurredAt==null||!"patient:read".equals(operation))throw new IllegalArgumentException("Invalid read audit entry");
 }
}
