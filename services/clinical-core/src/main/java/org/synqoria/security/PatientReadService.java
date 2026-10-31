package org.synqoria.security;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Dependency-injected patient read application service with non-disclosure.
 * Ports MUST be implemented within a trusted server backend, with verified session,
 * cell-local tenant-scoped repository and accountable policy/consent evidence source.
 * No HTTP adapter, OIDC verification or persistent storage is shipped here.
 */
public final class PatientReadService {
    @FunctionalInterface public interface PatientRepository {
        Optional<PatientRecordEnvelope> getForTenant(UUID tenantId, UUID patientId);
    }
    @FunctionalInterface public interface ProofResolver {
        TrustedClinicalAccessEvidence resolve(TrustedAccessContext caller, PatientRecordEnvelope record, String operation);
    }
    public enum Status { OK, NOT_DISCLOSED, SERVICE_UNAVAILABLE }
    public record Result(Status status, PatientRecordEnvelope record) {
        public Result {
            Objects.requireNonNull(status);
            if ((status == Status.OK) != (record != null)) throw new IllegalArgumentException("Result integrity violation");
        }
    }
    private final PatientRepository repository;
    private final ProofResolver proofResolver;
    public PatientReadService(PatientRepository repository, ProofResolver proofResolver) {
        this.repository = Objects.requireNonNull(repository);
        this.proofResolver = Objects.requireNonNull(proofResolver);
    }
    public Result read(TrustedAccessContext caller, UUID requestedPatientId, Instant trustedServerNow) {
        if (caller == null || requestedPatientId == null || trustedServerNow == null
                || !caller.capabilities().contains("patient:read")
                || !caller.permittedPatientIds().contains(requestedPatientId)) return denied();
        try {
            var record = repository.getForTenant(caller.tenantId(), requestedPatientId);
            if (record == null || record.isEmpty()) return denied();
            var patient = record.get();
            if (!patient.tenantId().equals(caller.tenantId())
                    || !patient.facilityId().equals(caller.facilityId())
                    || !patient.patientId().equals(requestedPatientId)) return denied();
            var proof = proofResolver.resolve(caller, patient, "patient:read");
            return ClinicalAccessPolicy.authorize(caller,proof,patient.tenantId(),patient.facilityId(),
                    patient.patientId(),"patient:read",trustedServerNow)==ClinicalAccessPolicy.Decision.ALLOWED
                    ? new Result(Status.OK,patient) : denied();
        } catch (RuntimeException exception) {
            // Never return raw store/policy failures, SQL messages or patient details to clients.
            return new Result(Status.SERVICE_UNAVAILABLE,null);
        }
    }
    private static Result denied() { return new Result(Status.NOT_DISCLOSED,null); }
}
