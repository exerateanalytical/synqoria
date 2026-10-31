package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.*;
/** M03 clinician documentation. No automatically-derived priority, threshold or treatment advice. */
public final class TriageAssessmentService {
 public TriageCase begin(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:assess","ASSESSMENT_BEGIN",d->{d.require(TriageCase.Stage.CALLED);d.requireNotHeld();if(d.paused)throw new IllegalStateException("queue paused");d.stage=TriageCase.Stage.ASSESSING;d.assignee=a.actorId();});
 }
 public TriageCase recordObservation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,TriageCase.Observation obs){
  return TriageMutation.change(a,e,c,q,"triage:assess","OBSERVATION_RECORD",d->{d.require(TriageCase.Stage.ASSESSING);if(obs==null||!obs.recordedBy().equals(a.actorId())||obs.takenAt().isAfter(q.trustedNow())||obs.takenAt().isBefore(c.arrivedAt())||obs.replaces()!=null||d.observations.containsKey(obs.id()))throw new IllegalArgumentException("observation rejected");d.observations.put(obs.id(),obs);});
 }
 public TriageCase correctObservation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,TriageCase.Observation correction){
  return TriageMutation.change(a,e,c,q,"triage:assess","OBSERVATION_CORRECTION",d->{d.require(TriageCase.Stage.ASSESSING);if(correction==null||correction.replaces()==null||!d.observations.containsKey(correction.replaces())||d.observations.containsKey(correction.id())||!correction.recordedBy().equals(a.actorId())||correction.takenAt().isAfter(q.trustedNow()))throw new IllegalArgumentException("invalid correction lineage");var original=d.observations.get(correction.replaces());if(!original.code().equals(correction.code())||!original.unit().equals(correction.unit()))throw new IllegalArgumentException("correction changes measurement type");d.observations.put(correction.id(),correction);});
 }
 public TriageCase recordFreeText(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String narrative){
  return TriageMutation.change(a,e,c,q,"triage:assess","ASSESSMENT_NARRATIVE",d->{d.require(TriageCase.Stage.ASSESSING);d.metadata("assessmentNarrative",narrative,4000);});
 }
 public TriageCase recordSourceDevice(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String deviceRef){
  return TriageMutation.change(a,e,c,q,"triage:assess","ASSESSMENT_DEVICE_SOURCE",d->{d.require(TriageCase.Stage.ASSESSING);d.metadata("sourceDevice",deviceRef,128);});
 }
 public TriageCase recordPatientStatement(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String statement){
  return TriageMutation.change(a,e,c,q,"triage:assess","PATIENT_STATEMENT",d->{d.require(TriageCase.Stage.ASSESSING);d.metadata("patientStatement",statement,2000);});
 }
 public TriageCase recordAllergyReviewEvidence(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String reviewRef){
  return TriageMutation.change(a,e,c,q,"triage:assess","ALLERGY_REVIEW_REFERENCE",d->{d.require(TriageCase.Stage.ASSESSING);d.metadata("allergyReviewRef",reviewRef,128);});
 }
 public TriageCase attestAssessment(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:sign","ASSESSMENT_ATTEST",d->{d.require(TriageCase.Stage.ASSESSING);d.requireNotHeld();if(d.observations.isEmpty()&&!d.metadata.containsKey("assessmentNarrative"))throw new IllegalStateException("nothing documented");d.signed=true;d.metadata("attestedBy",a.actorId().toString(),64);d.metadata("attestedAt",q.trustedNow().toString(),64);d.stage=TriageCase.Stage.ASSESSED;});
 }
 public TriageCase recordAssessmentAddendum(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String addendum){
  return TriageMutation.change(a,e,c,q,"triage:sign","ASSESSMENT_ADDENDUM",d->{d.require(TriageCase.Stage.ASSESSED,TriageCase.Stage.PRIORITIZED);if(!d.signed)throw new IllegalStateException("unsigned assessment");TriageMutation.requireRationale(q);d.metadata("addendum-"+q.commandId(),addendum,2000);});
 }
 public TriageCase recordDeclinedMeasurement(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String measurementCode){
  return TriageMutation.change(a,e,c,q,"triage:assess","MEASUREMENT_DECLINED",d->{d.require(TriageCase.Stage.ASSESSING);TriageMutation.requireRationale(q);if(!TriageCase.safe(measurementCode,64))throw new IllegalArgumentException("code required");d.metadata("declined-"+measurementCode,q.rationale(),500);});
 }
}
