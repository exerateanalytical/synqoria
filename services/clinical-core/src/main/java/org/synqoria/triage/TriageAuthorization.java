package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.UUID;
/** Only server-verified session context/evidence; no user supplied tenant, patient or capability headers. */
public final class TriageAuthorization {
 private TriageAuthorization(){}
 public static void require(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,UUID tenant,UUID facility,UUID patient,String capability,Instant now){
   if(ClinicalAccessPolicy.authorize(identity,proof,tenant,facility,patient,capability,now)!=ClinicalAccessPolicy.Decision.ALLOWED)
       throw new SecurityException("triage resource unavailable");
 }
 public static void require(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,TriageCase c,String capability,Instant now){
   if(c==null)throw new SecurityException("triage resource unavailable");
   require(identity,proof,c.tenant(),c.facility(),c.patient(),capability,now);
 }
}
