package org.synqoria.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Input from a trusted, server-side policy evaluation only. NEVER bind this record
 * from a client request, JWT payload without verification, or HTTP headers.
 * This is a structural foundation, not an OIDC verifier, a consent rules engine,
 * or a substitute for actual clinical safety and privilege approval.
 */
public record TrustedClinicalAccessEvidence(
        UUID tenantId,
        UUID facilityId,
        UUID actorId,
        UUID patientId,
        Instant issuedAt,
        Instant expiresAt,
        boolean identityAndMfaVerified,
        boolean facilityPrivilegeActive,
        boolean shiftActive,
        boolean purposeApproved,
        boolean consentOrLegalBasisVerified,
        boolean revocationChecked,
        boolean patientScopeFresh,
        Set<String> authorizedOperations) {
    public TrustedClinicalAccessEvidence {
        if (tenantId == null || facilityId == null || actorId == null || patientId == null
                || issuedAt == null || expiresAt == null || authorizedOperations == null
                || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Invalid trusted authorization evidence");
        }
        authorizedOperations = Set.copyOf(authorizedOperations);
    }
}
