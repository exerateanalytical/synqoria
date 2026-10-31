package org.synqoria.security;

import java.util.Set;
import java.util.UUID;

/**
 * Authenticated scope is built only by the backend from a validated identity/session.
 * Do not deserialize this class from a caller-supplied request body or tenant header.
 * Foundation domain model; not an OIDC token verifier or a complete authorization service.
 */
public record TrustedAccessContext(
        UUID tenantId,
        UUID facilityId,
        UUID actorId,
        Set<String> capabilities,
        Set<UUID> permittedPatientIds) {
    public TrustedAccessContext {
        if (tenantId == null || facilityId == null || actorId == null) throw new IllegalArgumentException("Missing authenticated scope");
        capabilities = Set.copyOf(capabilities);
        permittedPatientIds = Set.copyOf(permittedPatientIds);
    }
}
