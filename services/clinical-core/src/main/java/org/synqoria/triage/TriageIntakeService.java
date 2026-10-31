package org.synqoria.triage;
import org.synqoria.security.*;
import java.util.*;
import java.time.*;
/** M03 patient arrival intake. No medical priority or medical rules are inferred. */
public final class TriageIntakeService {
 public TriageCase register(TrustedAccessContext actor,TrustedClinicalAccessEvidence proof,UUID caseId,UUID patient,UUID appointment,Instant now){
  if(actor==null||caseId==null||patient==null||now==null)throw new SecurityException("intake unavailable");
  TriageAuthorization.require(actor,proof,actor.tenantId(),actor.facilityId(),patient,"triage:intake",now);
  return new TriageCase(caseId,actor.tenantId(),actor.facilityId(),patient,appointment,null,null,TriageCase.Stage.ARRIVED,1,null,null,null,false,false,false,now,null,Map.of(),Map.of(),List.of());
 }
 public TriageCase recordArrivalReason(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String reason){
  return TriageMutation.change(a,e,c,q,"triage:intake","ARRIVAL_REASON",d->{d.require(TriageCase.Stage.ARRIVED);d.metadata("arrivalReason",reason,500);});
 }
 public TriageCase recordPreferredLanguage(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String language){
  return TriageMutation.change(a,e,c,q,"triage:intake","LANGUAGE",d->{d.requireOpen();d.metadata("preferredLanguage",language,64);});
 }
 public TriageCase requestInterpreter(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String language){
  return TriageMutation.change(a,e,c,q,"triage:intake","INTERPRETER_REQUEST",d->{d.requireOpen();d.metadata("interpreterRequested",language,64);});
 }
 public TriageCase recordAccessibilitySupport(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String support){
  return TriageMutation.change(a,e,c,q,"triage:intake","ACCESSIBILITY_SUPPORT",d->{d.requireOpen();d.metadata("accessibilitySupport",support,200);});
 }
 public TriageCase recordIdentityVerification(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String evidenceReference){
  return TriageMutation.change(a,e,c,q,"triage:intake","IDENTITY_VERIFICATION",d->{d.require(TriageCase.Stage.ARRIVED);d.metadata("identityEvidenceReference",evidenceReference,128);});
 }
 public TriageCase recordArrivalChannel(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String channel){
  return TriageMutation.change(a,e,c,q,"triage:intake","ARRIVAL_CHANNEL",d->{d.require(TriageCase.Stage.ARRIVED);if(!Set.of("WALK_IN","APPOINTMENT","REFERRAL","TRANSFER").contains(channel))throw new IllegalArgumentException("invalid channel");d.metadata("arrivalChannel",channel,16);});
 }
 public TriageCase recordReferralReference(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String reference){
  return TriageMutation.change(a,e,c,q,"triage:intake","REFERRAL_REFERENCE",d->{d.require(TriageCase.Stage.ARRIVED);if(!"REFERRAL".equals(d.metadata.get("arrivalChannel")))throw new IllegalStateException("referral channel required");d.metadata("referralReference",reference,128);});
 }
 public TriageCase recordAttendantContact(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String reference){
  return TriageMutation.change(a,e,c,q,"triage:intake","ATTENDANT_CONTACT",d->{d.requireOpen();d.metadata("attendantContactReference",reference,128);});
 }
 public TriageCase recordConsentEvidence(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String reference){
  return TriageMutation.change(a,e,c,q,"triage:intake","CONSENT_EVIDENCE",d->{d.requireOpen();d.metadata("consentEvidenceReference",reference,128);});
 }
}
