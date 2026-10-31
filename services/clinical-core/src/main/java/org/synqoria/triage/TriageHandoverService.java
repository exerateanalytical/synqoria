package org.synqoria.triage;
import org.synqoria.security.*;
import java.util.*;
/** Explicit staff assignment and receiving-clinician handover. Human-reviewed, no automatic disposition. */
public final class TriageHandoverService {
 public TriageCase assignClinician(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,UUID clinician){
  return TriageMutation.change(a,e,c,q,"triage:handover","CLINICIAN_ASSIGNMENT",d->{d.require(TriageCase.Stage.PRIORITIZED);d.requireNotHeld();if(clinician==null)throw new IllegalArgumentException("clinician required");d.assignee=clinician;d.stage=TriageCase.Stage.ASSIGNED;});
 }
 public TriageCase reassignClinician(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,UUID clinician){
  return TriageMutation.change(a,e,c,q,"triage:handover","CLINICIAN_REASSIGNMENT",d->{d.require(TriageCase.Stage.ASSIGNED);TriageMutation.requireRationale(q);if(clinician==null||clinician.equals(d.assignee))throw new IllegalArgumentException("new clinician required");d.assignee=clinician;d.metadata("reassignmentReason",q.rationale(),500);});
 }
 public TriageCase requestHandover(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:handover","HANDOVER_REQUEST",d->{d.require(TriageCase.Stage.ASSIGNED);d.requireNotHeld();if(d.escalation!=null)throw new IllegalStateException("unresolved escalation");if(d.metadata.containsKey("handoverRequestedAt"))throw new IllegalStateException("handover already requested");d.metadata("handoverRequestedAt",q.trustedNow().toString(),64);});
 }
 public TriageCase acceptHandover(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:receive","HANDOVER_ACCEPT",d->{d.require(TriageCase.Stage.ASSIGNED);if(!a.actorId().equals(d.assignee)||!d.metadata.containsKey("handoverRequestedAt"))throw new SecurityException("receiving clinician or request missing");d.metadata("handoverAcceptedAt",q.trustedNow().toString(),64);});
 }
 public TriageCase rejectHandover(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:receive","HANDOVER_REJECT",d->{d.require(TriageCase.Stage.ASSIGNED);if(!a.actorId().equals(d.assignee)||!d.metadata.containsKey("handoverRequestedAt"))throw new SecurityException("receiving clinician or request missing");TriageMutation.requireRationale(q);d.metadata.remove("handoverRequestedAt");d.metadata.remove("handoverAcceptedAt");d.metadata("handoverRejectReason",q.rationale(),500);});
 }
 public TriageCase recordHandoverNote(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String note){
  return TriageMutation.change(a,e,c,q,"triage:handover","HANDOVER_NOTE",d->{d.require(TriageCase.Stage.ASSIGNED);if(!d.metadata.containsKey("handoverRequestedAt")||!TriageCase.safe(note,2000))throw new IllegalStateException("handover note requires request");d.handover=note;});
 }
 public TriageCase documentReceiverAcknowledgement(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:receive","RECEIVER_ACKNOWLEDGEMENT",d->{d.require(TriageCase.Stage.ASSIGNED);if(!a.actorId().equals(d.assignee)||!d.metadata.containsKey("handoverAcceptedAt")||d.handover==null)throw new SecurityException("handover evidence incomplete");d.metadata("receiverAcknowledgedAt",q.trustedNow().toString(),64);});
 }
 public TriageCase finalizeHandover(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:handover","HANDOVER_FINALIZED",d->{d.require(TriageCase.Stage.ASSIGNED);d.requireNotHeld();if(!d.metadata.containsKey("receiverAcknowledgedAt")||d.escalation!=null)throw new IllegalStateException("handover or escalation outstanding");d.stage=TriageCase.Stage.HANDED_OVER;});
 }
 public TriageCase closeTriage(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:handover","TRIAGE_CLOSED",d->{d.require(TriageCase.Stage.HANDED_OVER);TriageMutation.requireRationale(q);d.stage=TriageCase.Stage.CLOSED;d.metadata("closureReason",q.rationale(),500);});
 }
 public TriageCase recordDestination(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,String destination){
  return TriageMutation.change(a,e,c,q,"triage:handover","HANDOVER_DESTINATION",d->{d.require(TriageCase.Stage.ASSIGNED);d.metadata("handoverDestination",destination,128);});
 }
}
