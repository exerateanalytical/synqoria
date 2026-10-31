package org.synqoria.triage;
import org.synqoria.security.*;
import java.util.*;
/** Only an explicitly authorized clinician may assign or change urgency. No algorithmic medical rules. */
public final class TriagePriorityService {
 public TriageCase setClinicianPriority(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String priority){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","CLINICIAN_PRIORITY_SET",d->{d.require(TriageCase.Stage.ASSESSED);if(!d.signed||!TriageCase.safe(priority,64))throw new IllegalStateException("signed assessment and explicit priority required");d.priority=priority;d.stage=TriageCase.Stage.PRIORITIZED;d.metadata("prioritySetBy",a.actorId().toString(),64);});
 }
 public TriageCase reviseClinicianPriority(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String newPriority){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","CLINICIAN_PRIORITY_REVISED",d->{d.require(TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);TriageMutation.requireRationale(q);if(!TriageCase.safe(newPriority,64)||newPriority.equals(d.priority))throw new IllegalArgumentException("new priority required");d.priority=newPriority;d.metadata("priorityRevisionReason",q.rationale(),500);});
 }
 public TriageCase requestReassessment(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","REASSESSMENT_REQUESTED",d->{d.require(TriageCase.Stage.ASSESSED,TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);TriageMutation.requireRationale(q);if(d.metadata.containsKey("reassessmentRequestedAt"))throw new IllegalStateException("request pending");d.metadata("reassessmentRequestedAt",q.trustedNow().toString(),64);d.metadata("reassessmentRequestReason",q.rationale(),500);});
 }
 public TriageCase beginReassessment(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","REASSESSMENT_STARTED",d->{d.require(TriageCase.Stage.ASSESSED,TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);if(!d.metadata.containsKey("reassessmentRequestedAt")||d.metadata.containsKey("reassessmentStartedAt"))throw new IllegalStateException("request required");d.metadata("reassessmentStartedAt",q.trustedNow().toString(),64);});
 }
 public TriageCase recordReassessment(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String clinicianNarrative){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","REASSESSMENT_DOCUMENTED",d->{d.require(TriageCase.Stage.ASSESSED,TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);if(!d.metadata.containsKey("reassessmentStartedAt"))throw new IllegalStateException("reassessment not started");d.metadata("reassessmentNarrative",clinicianNarrative,2000);});
 }
 public TriageCase completeReassessment(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","REASSESSMENT_COMPLETED",d->{d.require(TriageCase.Stage.ASSESSED,TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);if(!d.metadata.containsKey("reassessmentNarrative"))throw new IllegalStateException("reassessment documentation required");d.reassessment=q.trustedNow();d.metadata.remove("reassessmentStartedAt");d.metadata.remove("reassessmentRequestedAt");d.metadata.remove("reassessmentNarrative");});
 }
 public TriageCase raiseManualEscalation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","ESCALATION_RAISED",d->{d.requireOpen();TriageMutation.requireRationale(q);if(d.escalation!=null)throw new IllegalStateException("escalation already active");d.escalation=q.rationale();d.metadata("escalationRaisedBy",a.actorId().toString(),64);});
 }
 public TriageCase acknowledgeEscalation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","ESCALATION_ACKNOWLEDGED",d->{d.requireOpen();if(d.escalation==null||d.metadata.containsKey("escalationAcknowledgedBy"))throw new IllegalStateException("no active unacknowledged escalation");d.metadata("escalationAcknowledgedBy",a.actorId().toString(),64);});
 }
 public TriageCase resolveEscalation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","ESCALATION_RESOLVED",d->{d.requireOpen();if(d.escalation==null||!d.metadata.containsKey("escalationAcknowledgedBy"))throw new IllegalStateException("escalation acknowledgment required");TriageMutation.requireRationale(q);d.escalation=null;d.metadata.remove("escalationAcknowledgedBy");d.metadata.remove("escalationRaisedBy");d.metadata("escalationResolutionReason",q.rationale(),500);});
 }
 public TriageCase recordPriorityEvidence(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String evidenceRef){
  return TriageMutation.change(a,e,c,q,"triage:prioritize","PRIORITY_EVIDENCE_LINK",d->{d.require(TriageCase.Stage.PRIORITIZED,TriageCase.Stage.ASSIGNED);d.metadata("priorityEvidenceRef",evidenceRef,128);});
 }
}
