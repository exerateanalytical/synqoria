package org.synqoria.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Fail-closed structural authorization gate. Every input must be produced from a
 * trusted backend transaction or verified policy evaluation. Do not treat a
 * pass as consent interpretation, medication safety clearance or release approval.
 */
public final class ClinicalAccessPolicy {
    private ClinicalAccessPolicy() {}
    public enum Decision { ALLOWED, NOT_AUTHORIZED }

    public static Decision authorize(
            TrustedAccessContext caller, TrustedClinicalAccessEvidence proof,
            UUID recordTenant, UUID recordFacility, UUID patient, String operation, Instant serverNow) {
        if (caller == null || proof == null || recordTenant == null || recordFacility == null
                || patient == null || operation == null || operation.isBlank() || serverNow == null) {
            return Decision.NOT_AUTHORIZED;
        }
        if (!caller.tenantId().equals(recordTenant) || !caller.facilityId().equals(recordFacility)
                || !caller.permittedPatientIds().contains(patient)
                || !caller.capabilities().contains(operation)) {
            return Decision.NOT_AUTHORIZED;
        }
        if (!caller.tenantId().equals(proof.tenantId()) || !caller.facilityId().equals(proof.facilityId())
                || !caller.actorId().equals(proof.actorId()) || !patient.equals(proof.patientId())) {
            return Decision.NOT_AUTHORIZED;
        }
        if (serverNow.isBefore(proof.issuedAt()) || !serverNow.isBefore(proof.expiresAt())) {
            return Decision.NOT_AUTHORIZED;
        }
        if (!proof.identityAndMfaVerified() || !proof.facilityPrivilegeActive() || !proof.shiftActive()
                || !proof.purposeApproved() || !proof.consentOrLegalBasisVerified()
                || !proof.revocationChecked() || !proof.patientScopeFresh()
                || !proof.authorizedOperations().contains(operation)) {
            return Decision.NOT_AUTHORIZED;
        }
        return Decision.ALLOWED;
    }
}
