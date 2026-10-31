package org.synqoria.security;

import java.util.UUID;

/** Patient existence and facility association from authoritative storage (not caller data). */
public record PatientRecordEnvelope(UUID tenantId, UUID facilityId, UUID patientId, String identityStatus, long revision) {
    public PatientRecordEnvelope {
        if (tenantId == null || facilityId == null || patientId == null || revision < 1
                || !("provisional".equals(identityStatus) || "verified".equals(identityStatus) || "merged".equals(identityStatus))) {
            throw new IllegalArgumentException("Invalid patient record envelope");
        }
    }
}
