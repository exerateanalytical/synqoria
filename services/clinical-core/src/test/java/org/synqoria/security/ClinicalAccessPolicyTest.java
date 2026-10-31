package org.synqoria.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.synqoria.security.ClinicalAccessPolicy.Decision.*;

public class ClinicalAccessPolicyTest {
    private static int count = 0;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); count++;
    }
    private static final UUID tenant = UUID.randomUUID();
    private static final UUID facility = UUID.randomUUID();
    private static final UUID patient = UUID.randomUUID();
    private static final UUID actor = UUID.randomUUID();
    private static final Instant now = Instant.parse("2026-10-29T20:00:00Z");
    private static TrustedClinicalAccessEvidence evidence(boolean mfa,boolean privilege,boolean shift,
            boolean purpose,boolean legal,boolean revocation,boolean fresh) {
        return new TrustedClinicalAccessEvidence(tenant,facility,actor,patient,now.minusSeconds(60),now.plusSeconds(60),
                mfa,privilege,shift,purpose,legal,revocation,fresh,Set.of("patient:read"));
    }
    public static void main(String[] args) {
        var caller = new TrustedAccessContext(tenant,facility,actor,Set.of("patient:read"),Set.of(patient));
        var proof = evidence(true,true,true,true,true,true,true);
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,patient,"patient:read",now)==ALLOWED,"authorized read");
        check(ClinicalAccessPolicy.authorize(null,proof,tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"no context denied");
        check(ClinicalAccessPolicy.authorize(caller,null,tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"no proof denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,UUID.randomUUID(),facility,patient,"patient:read",now)==NOT_AUTHORIZED,"other tenant denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,UUID.randomUUID(),patient,"patient:read",now)==NOT_AUTHORIZED,"other facility denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,UUID.randomUUID(),"patient:read",now)==NOT_AUTHORIZED,"other patient denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,patient,"patient:write",now)==NOT_AUTHORIZED,"ungranted write denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,patient,"patient:read",now.plusSeconds(60))==NOT_AUTHORIZED,"expiry boundary denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,patient,"patient:read",now.minusSeconds(61))==NOT_AUTHORIZED,"not yet issued denied");
        check(ClinicalAccessPolicy.authorize(new TrustedAccessContext(tenant,facility,UUID.randomUUID(),Set.of("patient:read"),Set.of(patient)),proof,tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"different actor denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(false,true,true,true,true,true,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"missing verified MFA denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,false,true,true,true,true,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"inactive facility privilege denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,true,false,true,true,true,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"inactive shift denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,true,true,false,true,true,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"unapproved purpose denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,true,true,true,false,true,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"missing consent/legal basis denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,true,true,true,true,false,true),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"unverified revocation status denied");
        check(ClinicalAccessPolicy.authorize(caller,evidence(true,true,true,true,true,true,false),tenant,facility,patient,"patient:read",now)==NOT_AUTHORIZED,"stale patient scope denied");
        check(ClinicalAccessPolicy.authorize(caller,proof,tenant,facility,patient,"",now)==NOT_AUTHORIZED,"empty operation denied");
        boolean bad = false;
        try {new TrustedClinicalAccessEvidence(tenant,facility,actor,patient,now,now,false,false,false,false,false,false,false,Set.of());}
        catch (IllegalArgumentException ex) {bad=true;}
        check(bad,"invalid evidence interval rejected");
        System.out.println("PASS: " + count + " Java clinical proof assertions; NO OIDC/database clinical authorization integration");
    }
}
