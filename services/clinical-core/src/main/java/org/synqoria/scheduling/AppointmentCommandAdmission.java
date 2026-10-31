package org.synqoria.scheduling;

import org.synqoria.security.ClinicalAccessPolicy;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.time.Instant;
import java.util.UUID;

/** Backend-only authorization bridge; no raw HTTP header, unsigned JWT or patient-supplied scope. */
public final class AppointmentCommandAdmission {
    private AppointmentCommandAdmission() {}
    public static AppointmentTenantContext require(TrustedAccessContext identity,
                                                    TrustedClinicalAccessEvidence evidence,
                                                    UUID patient, Instant serverNow) {
        if (identity == null || patient == null || serverNow == null ||
            ClinicalAccessPolicy.authorize(identity,evidence,identity.tenantId(),identity.facilityId(),
                patient,"appointment:manage",serverNow) != ClinicalAccessPolicy.Decision.ALLOWED)
            throw new SecurityException("appointment operation denied");
        return new AppointmentTenantContext(identity.tenantId(),identity.facilityId(),identity.actorId(),patient);
    }
}
