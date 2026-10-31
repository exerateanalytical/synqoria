package org.synqoria.facility;

import java.util.UUID;

/** Authoritative relationship snapshot only; never construct from caller-controlled tenant headers. */
public record FacilityBinding(UUID cellId, UUID tenantId, UUID facilityId, String status, long revision) {
    public FacilityBinding {
        if (cellId == null || tenantId == null || facilityId == null || revision < 1
                || !("ACTIVE".equals(status) || "SUSPENDED".equals(status) || "RETIRED".equals(status))) {
            throw new IllegalArgumentException("Invalid authoritative facility binding");
        }
    }
}
