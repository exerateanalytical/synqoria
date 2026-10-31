package org.synqoria.security;

import java.util.Set;
import java.util.UUID;

public class PatientScopePolicyTest {
    private static int passed = 0;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        passed++;
    }
    public static void main(String[] args) {
        var tenant = UUID.randomUUID();
        var otherTenant = UUID.randomUUID();
        var facility = UUID.randomUUID();
        var otherFacility = UUID.randomUUID();
        var patient = UUID.randomUUID();
        var otherPatient = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var allowed = new TrustedAccessContext(tenant,facility,actor,Set.of("patient:read"),Set.of(patient));
        var noCapability = new TrustedAccessContext(tenant,facility,actor,Set.of(),Set.of(patient));
        var noPatientScope = new TrustedAccessContext(tenant,facility,actor,Set.of("patient:read"),Set.of());
        check(PatientScopePolicy.canReadPatient(allowed,tenant,facility,patient),"valid scoped read");
        check(!PatientScopePolicy.canReadPatient(allowed,otherTenant,facility,patient),"cross-tenant denied");
        check(!PatientScopePolicy.canReadPatient(allowed,tenant,otherFacility,patient),"cross-facility denied");
        check(!PatientScopePolicy.canReadPatient(allowed,tenant,facility,otherPatient),"wrong patient denied");
        check(!PatientScopePolicy.canReadPatient(noCapability,tenant,facility,patient),"missing capability denied");
        check(!PatientScopePolicy.canReadPatient(noPatientScope,tenant,facility,patient),"missing patient scope denied");
        check(!PatientScopePolicy.canReadPatient(null,tenant,facility,patient),"missing context denied");
        check(!PatientScopePolicy.canReadPatient(allowed,null,facility,patient),"missing tenant denied");
        check(!PatientScopePolicy.canReadPatient(allowed,tenant,null,patient),"missing facility denied");
        check(!PatientScopePolicy.canReadPatient(allowed,tenant,facility,null),"missing patient denied");
        boolean rejected = false;
        try {new TrustedAccessContext(null,facility,actor,Set.of(),Set.of());} catch (IllegalArgumentException e) {rejected=true;}
        check(rejected,"invalid context rejected");
        System.out.println("PASS: " + passed + " Java tenant/facility/patient scope assertions");
    }
}
