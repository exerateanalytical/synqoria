package org.synqoria.triage;
import org.synqoria.security.*;
import java.util.*;
/** M03 queue operations: administrative ordering only. Clinical priority is set by clinicians elsewhere. */
public final class TriageQueueService {
 public TriageCase enqueue(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,UUID station){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_ENQUEUE",d->{d.require(TriageCase.Stage.ARRIVED);if(station==null)throw new IllegalArgumentException("station required");d.station=station;d.stage=TriageCase.Stage.WAITING;});
 }
 public TriageCase call(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_CALL",d->{d.require(TriageCase.Stage.WAITING);d.requireNotHeld();if(d.paused)throw new IllegalStateException("queue paused");d.stage=TriageCase.Stage.CALLED;});
 }
 public TriageCase recall(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_RECALL",d->{d.require(TriageCase.Stage.CALLED);d.metadata("recallAt",q.trustedNow().toString(),48);});
 }
 public TriageCase returnToWaiting(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_RETURN",d->{d.require(TriageCase.Stage.CALLED);TriageMutation.requireRationale(q);d.stage=TriageCase.Stage.WAITING;d.metadata("returnReason",q.rationale(),500);});
 }
 public TriageCase pause(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_PAUSE",d->{d.require(TriageCase.Stage.WAITING,TriageCase.Stage.CALLED);if(d.paused)throw new IllegalStateException("already paused");TriageMutation.requireRationale(q);d.paused=true;d.metadata("pauseReason",q.rationale(),500);});
 }
 public TriageCase resume(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_RESUME",d->{d.require(TriageCase.Stage.WAITING,TriageCase.Stage.CALLED);if(!d.paused)throw new IllegalStateException("not paused");d.paused=false;});
 }
 public TriageCase moveStation(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q,UUID station){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_MOVE_STATION",d->{d.require(TriageCase.Stage.WAITING,TriageCase.Stage.CALLED);if(station==null||station.equals(d.station))throw new IllegalArgumentException("new station required");TriageMutation.requireRationale(q);d.station=station;d.metadata("stationMoveReason",q.rationale(),500);});
 }
 public TriageCase recordPatientUnavailable(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:queue","QUEUE_PATIENT_UNAVAILABLE",d->{d.require(TriageCase.Stage.CALLED);TriageMutation.requireRationale(q);d.stage=TriageCase.Stage.WAITING;d.paused=true;d.metadata("unavailableReason",q.rationale(),500);});
 }
 public TriageCase placeSafetyHold(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:manage","SAFETY_HOLD",d->{d.requireOpen();if(d.hold)throw new IllegalStateException("already held");TriageMutation.requireRationale(q);d.hold=true;d.metadata("safetyHoldReason",q.rationale(),500);});
 }
 public TriageCase releaseSafetyHold(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,TriageCommand q){
  return TriageMutation.change(a,e,c,q,"triage:manage","SAFETY_HOLD_RELEASE",d->{d.requireOpen();if(!d.hold)throw new IllegalStateException("not held");TriageMutation.requireRationale(q);d.hold=false;d.metadata("safetyReleaseReason",q.rationale(),500);});
 }
}
