package org.synqoria.security;

import java.util.UUID;

/**
 * Deny-by-default scope predicate, to be invoked by authoritative services after
 * OIDC/MFA validation and trusted patient-scope resolution. This alone is not sufficient
 * to authorize clinical use: purpose, consent, shift, privilege and break-glass rules
 * must be evaluated by the full future policy engine.
 */
public final class PatientScopePolicy {
    private PatientScopePolicy() {}
    public static boolean canReadPatient(
            TrustedAccessContext caller,
            UUID recordTenantId,
            UUID recordFacilityId,
            UUID patientId) {
        if (caller == null || recordTenantId == null || recordFacilityId == null || patientId == null) return false;
        return caller.tenantId().equals(recordTenantId)
                && caller.facilityId().equals(recordFacilityId)
                && caller.capabilities().contains("patient:read")
                && caller.permittedPatientIds().contains(patientId);
    }
}
