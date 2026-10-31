package org.synqoria.security;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.synqoria.security.PatientReadService.Status.*;

public class PatientReadServiceTest {
    private static int passed=0;
    private static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);passed++;}
    private static final UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),actor=UUID.randomUUID(),patient=UUID.randomUUID();
    private static final Instant now=Instant.parse("2026-10-29T20:00:00Z");
    private static final PatientRecordEnvelope found=new PatientRecordEnvelope(tenant,facility,patient,"provisional",1);
    private static TrustedClinicalAccessEvidence proof(TrustedAccessContext context,PatientRecordEnvelope record,String operation) {
        return new TrustedClinicalAccessEvidence(tenant,facility,actor,patient,now.minusSeconds(3),now.plusSeconds(30),
                true,true,true,true,true,true,true,Set.of("patient:read"));
    }
    public static void main(String[] args){
        var caller=new TrustedAccessContext(tenant,facility,actor,Set.of("patient:read"),Set.of(patient));
        var storeCalls=new AtomicInteger();var policyCalls=new AtomicInteger();
        PatientReadService.PatientRepository store=(t,p)->{storeCalls.incrementAndGet();return Optional.of(found);};
        PatientReadService.ProofResolver resolver=(c,r,o)->{policyCalls.incrementAndGet();return proof(c,r,o);};
        var service=new PatientReadService(store,resolver);
        check(service.read(caller,patient,now).status()==OK,"authorized read");
        check(service.read(caller,patient,now).record().revision()==1,"authoritative version preserved");
        check(service.read(null,patient,now).status()==NOT_DISCLOSED,"no context denied");
        check(service.read(caller,null,now).status()==NOT_DISCLOSED,"null patient denied");
        check(service.read(caller,patient,null).status()==NOT_DISCLOSED,"missing clock denied");
        check(service.read(new TrustedAccessContext(tenant,facility,actor,Set.of(),Set.of(patient)),patient,now).status()==NOT_DISCLOSED,"capability absent denied");
        check(service.read(new TrustedAccessContext(tenant,facility,actor,Set.of("patient:read"),Set.of()),patient,now).status()==NOT_DISCLOSED,"patient scope absent denied");
        check(storeCalls.get()==2,"invalid requests never query patient store");
        check(policyCalls.get()==2,"invalid requests never invoke consent resolver");
        check(new PatientReadService((t,p)->Optional.empty(),resolver).read(caller,patient,now).status()==NOT_DISCLOSED,"missing patient hidden");
        check(new PatientReadService((t,p)->Optional.of(new PatientRecordEnvelope(tenant,UUID.randomUUID(),patient,"provisional",1)),resolver).read(caller,patient,now).status()==NOT_DISCLOSED,"cross-facility hidden");
        check(new PatientReadService((t,p)->Optional.of(new PatientRecordEnvelope(UUID.randomUUID(),facility,patient,"provisional",1)),resolver).read(caller,patient,now).status()==NOT_DISCLOSED,"cross-tenant hidden");
        check(new PatientReadService((t,p)->Optional.of(new PatientRecordEnvelope(tenant,facility,UUID.randomUUID(),"provisional",1)),resolver).read(caller,patient,now).status()==NOT_DISCLOSED,"record mismatched patient hidden");
        check(new PatientReadService(store,(c,r,o)->null).read(caller,patient,now).status()==NOT_DISCLOSED,"no proof denied");
        check(new PatientReadService(store,(c,r,o)->new TrustedClinicalAccessEvidence(tenant,facility,actor,patient,now.minusSeconds(10),now.plusSeconds(10),true,true,true,true,false,true,true,Set.of("patient:read"))).read(caller,patient,now).status()==NOT_DISCLOSED,"no legal basis denied");
        check(new PatientReadService(store,(c,r,o)->proof(c,r,o)).read(caller,patient,now.plusSeconds(90)).status()==NOT_DISCLOSED,"expired proof denied");
        check(new PatientReadService((t,p)->{throw new IllegalStateException("secret SQL error");},resolver).read(caller,patient,now).status()==SERVICE_UNAVAILABLE,"storage errors mapped");
        check(new PatientReadService(store,(c,r,o)->{throw new IllegalStateException("secret evidence error");}).read(caller,patient,now).status()==SERVICE_UNAVAILABLE,"policy errors mapped");
        boolean rejected=false;try{new PatientReadService.Result(OK,null);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"no success without record");
        rejected=false;try{new PatientReadService.Result(NOT_DISCLOSED,found);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"no unauthorized response with record");
        System.out.println("PASS: "+passed+" patient read non-disclosure assertions");
    }
}
