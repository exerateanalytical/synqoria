package org.synqoria.http;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.UUID;
/** Boundary for audited patient data reads: never exposes an uncommitted result. */
@FunctionalInterface public interface PatientReadPort {
 AuditedPatientAccess.Result read(TrustedAccessContext actor,UUID patientId,UUID correlationId,Instant at);
}
