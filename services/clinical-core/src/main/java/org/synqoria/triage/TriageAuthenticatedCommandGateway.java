package org.synqoria.triage;

import org.synqoria.security.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Authenticated application command gateway. Requires server-verified actor/evidence; not an HTTP endpoint. */
public final class TriageAuthenticatedCommandGateway {
 public record Input(String action,String text,UUID reference,TriageCase.Observation observation) {
  public Input {if(action==null||!action.matches("[A-Z_]{2,80}")||text!=null&&text.length()>4000)throw new IllegalArgumentException("invalid triage action");}
  public static Input simple(String action){return new Input(action,null,null,null);}
  public static Input text(String action,String value){return new Input(action,value,null,null);}
  public static Input reference(String action,UUID value){return new Input(action,null,value,null);}
  public static Input observation(String action,TriageCase.Observation value){return new Input(action,null,null,value);}
  String canonical(){return "action="+action+"|text="+(text==null?"null":text.length()+":"+text)+"|ref="+reference+"|observation="+observation;}
 }
 private final TriageCommandCoordinator coordinator;
 private final TriageIntakeService intake=new TriageIntakeService();
 private final TriageQueueService queue=new TriageQueueService();
 private final TriageAssessmentService assessment=new TriageAssessmentService();
 private final TriagePriorityService priority=new TriagePriorityService();
 private final TriageHandoverService handover=new TriageHandoverService();
 public TriageAuthenticatedCommandGateway(TriageCommandCoordinator coordinator){this.coordinator=Objects.requireNonNull(coordinator);}
 public TriageCommandCoordinator.Result execute(TrustedAccessContext actor,TrustedClinicalAccessEvidence proof,TriagePersistencePort.Scope scope,TriageCommand command,Input input){
  if(input==null)throw new IllegalArgumentException("triage input required");
  validateInput(input);
  String cap=capability(input.action());
  UnaryOperator<TriageCase> action=switch(input.action()) {
   case "ARRIVAL_REASON" -> c->intake.recordArrivalReason(actor,proof,c,command,input.text());
   case "LANGUAGE" -> c->intake.recordPreferredLanguage(actor,proof,c,command,input.text());
   case "INTERPRETER_REQUEST" -> c->intake.requestInterpreter(actor,proof,c,command,input.text());
   case "ACCESSIBILITY_SUPPORT" -> c->intake.recordAccessibilitySupport(actor,proof,c,command,input.text());
   case "IDENTITY_VERIFICATION" -> c->intake.recordIdentityVerification(actor,proof,c,command,input.text());
   case "ARRIVAL_CHANNEL" -> c->intake.recordArrivalChannel(actor,proof,c,command,input.text());
   case "REFERRAL_REFERENCE" -> c->intake.recordReferralReference(actor,proof,c,command,input.text());
   case "ATTENDANT_CONTACT" -> c->intake.recordAttendantContact(actor,proof,c,command,input.text());
   case "CONSENT_EVIDENCE" -> c->intake.recordConsentEvidence(actor,proof,c,command,input.text());
   case "QUEUE_ENQUEUE" -> c->queue.enqueue(actor,proof,c,command,input.reference());
   case "QUEUE_CALL" -> c->queue.call(actor,proof,c,command);
   case "QUEUE_RECALL" -> c->queue.recall(actor,proof,c,command);
   case "QUEUE_RETURN" -> c->queue.returnToWaiting(actor,proof,c,command);
   case "QUEUE_PAUSE" -> c->queue.pause(actor,proof,c,command);
   case "QUEUE_RESUME" -> c->queue.resume(actor,proof,c,command);
   case "QUEUE_MOVE_STATION" -> c->queue.moveStation(actor,proof,c,command,input.reference());
   case "QUEUE_PATIENT_UNAVAILABLE" -> c->queue.recordPatientUnavailable(actor,proof,c,command);
   case "SAFETY_HOLD" -> c->queue.placeSafetyHold(actor,proof,c,command);
   case "SAFETY_HOLD_RELEASE" -> c->queue.releaseSafetyHold(actor,proof,c,command);
   case "ASSESSMENT_BEGIN" -> c->assessment.begin(actor,proof,c,command);
   case "OBSERVATION_RECORD" -> c->assessment.recordObservation(actor,proof,c,command,input.observation());
   case "OBSERVATION_CORRECTION" -> c->assessment.correctObservation(actor,proof,c,command,input.observation());
   case "ASSESSMENT_NARRATIVE" -> c->assessment.recordFreeText(actor,proof,c,command,input.text());
   case "ASSESSMENT_DEVICE_SOURCE" -> c->assessment.recordSourceDevice(actor,proof,c,command,input.text());
   case "PATIENT_STATEMENT" -> c->assessment.recordPatientStatement(actor,proof,c,command,input.text());
   case "ALLERGY_REVIEW_REFERENCE" -> c->assessment.recordAllergyReviewEvidence(actor,proof,c,command,input.text());
   case "ASSESSMENT_ATTEST" -> c->assessment.attestAssessment(actor,proof,c,command);
   case "ASSESSMENT_ADDENDUM" -> c->assessment.recordAssessmentAddendum(actor,proof,c,command,input.text());
   case "MEASUREMENT_DECLINED" -> c->assessment.recordDeclinedMeasurement(actor,proof,c,command,input.text());
   case "CLINICIAN_PRIORITY_SET" -> c->priority.setClinicianPriority(actor,proof,c,command,input.text());
   case "CLINICIAN_PRIORITY_REVISED" -> c->priority.reviseClinicianPriority(actor,proof,c,command,input.text());
   case "REASSESSMENT_REQUESTED" -> c->priority.requestReassessment(actor,proof,c,command);
   case "REASSESSMENT_STARTED" -> c->priority.beginReassessment(actor,proof,c,command);
   case "REASSESSMENT_DOCUMENTED" -> c->priority.recordReassessment(actor,proof,c,command,input.text());
   case "REASSESSMENT_COMPLETED" -> c->priority.completeReassessment(actor,proof,c,command);
   case "ESCALATION_RAISED" -> c->priority.raiseManualEscalation(actor,proof,c,command);
   case "ESCALATION_ACKNOWLEDGED" -> c->priority.acknowledgeEscalation(actor,proof,c,command);
   case "ESCALATION_RESOLVED" -> c->priority.resolveEscalation(actor,proof,c,command);
   case "PRIORITY_EVIDENCE_LINK" -> c->priority.recordPriorityEvidence(actor,proof,c,command,input.text());
   case "CLINICIAN_ASSIGNMENT" -> c->handover.assignClinician(actor,proof,c,command,input.reference());
   case "CLINICIAN_REASSIGNMENT" -> c->handover.reassignClinician(actor,proof,c,command,input.reference());
   case "HANDOVER_REQUEST" -> c->handover.requestHandover(actor,proof,c,command);
   case "HANDOVER_ACCEPT" -> c->handover.acceptHandover(actor,proof,c,command);
   case "HANDOVER_REJECT" -> c->handover.rejectHandover(actor,proof,c,command);
   case "HANDOVER_NOTE" -> c->handover.recordHandoverNote(actor,proof,c,command,input.text());
   case "RECEIVER_ACKNOWLEDGEMENT" -> c->handover.documentReceiverAcknowledgement(actor,proof,c,command);
   case "HANDOVER_FINALIZED" -> c->handover.finalizeHandover(actor,proof,c,command);
   case "TRIAGE_CLOSED" -> c->handover.closeTriage(actor,proof,c,command);
   case "HANDOVER_DESTINATION" -> c->handover.recordDestination(actor,proof,c,command,input.text());
   default -> throw new IllegalArgumentException("unsupported triage operation");
  };
  return coordinator.mutate(actor,proof,scope,command,cap,input.action(),input.canonical(),action);
 }
 private static void validateInput(Input input){
  boolean needsRef=switch(input.action()){
   case "QUEUE_ENQUEUE","QUEUE_MOVE_STATION","CLINICIAN_ASSIGNMENT","CLINICIAN_REASSIGNMENT" -> true;
   default -> false;
  };
  boolean needsObs=input.action().equals("OBSERVATION_RECORD")||input.action().equals("OBSERVATION_CORRECTION");
  boolean needsText=switch(input.action()){
   case "ARRIVAL_REASON","LANGUAGE","INTERPRETER_REQUEST","ACCESSIBILITY_SUPPORT","IDENTITY_VERIFICATION","ARRIVAL_CHANNEL","REFERRAL_REFERENCE","ATTENDANT_CONTACT","CONSENT_EVIDENCE", "ASSESSMENT_NARRATIVE","ASSESSMENT_DEVICE_SOURCE","PATIENT_STATEMENT","ALLERGY_REVIEW_REFERENCE","ASSESSMENT_ADDENDUM","MEASUREMENT_DECLINED","CLINICIAN_PRIORITY_SET","CLINICIAN_PRIORITY_REVISED","REASSESSMENT_DOCUMENTED","PRIORITY_EVIDENCE_LINK","HANDOVER_NOTE","HANDOVER_DESTINATION" -> true;
   default -> false;
  };
  if((input.reference()!=null)!=needsRef||(input.observation()!=null)!=needsObs||(input.text()!=null)!=needsText||needsText&&!TriageCase.safe(input.text(),4000))throw new IllegalArgumentException("triage command argument shape invalid");
 }
 public static String capability(String action){return switch(action){
  case "ARRIVAL_REASON","LANGUAGE","INTERPRETER_REQUEST","ACCESSIBILITY_SUPPORT","IDENTITY_VERIFICATION","ARRIVAL_CHANNEL","REFERRAL_REFERENCE","ATTENDANT_CONTACT","CONSENT_EVIDENCE" -> "triage:intake";
  case "QUEUE_ENQUEUE","QUEUE_CALL","QUEUE_RECALL","QUEUE_RETURN","QUEUE_PAUSE","QUEUE_RESUME","QUEUE_MOVE_STATION","QUEUE_PATIENT_UNAVAILABLE" -> "triage:queue";
  case "SAFETY_HOLD","SAFETY_HOLD_RELEASE" -> "triage:manage";
  case "ASSESSMENT_BEGIN","OBSERVATION_RECORD","OBSERVATION_CORRECTION","ASSESSMENT_NARRATIVE","ASSESSMENT_DEVICE_SOURCE","PATIENT_STATEMENT","ALLERGY_REVIEW_REFERENCE","MEASUREMENT_DECLINED" -> "triage:assess";
  case "ASSESSMENT_ATTEST","ASSESSMENT_ADDENDUM" -> "triage:sign";
  case "CLINICIAN_PRIORITY_SET","CLINICIAN_PRIORITY_REVISED","REASSESSMENT_REQUESTED","REASSESSMENT_STARTED","REASSESSMENT_DOCUMENTED","REASSESSMENT_COMPLETED","ESCALATION_RAISED","ESCALATION_ACKNOWLEDGED","ESCALATION_RESOLVED","PRIORITY_EVIDENCE_LINK" -> "triage:prioritize";
  case "CLINICIAN_ASSIGNMENT","CLINICIAN_REASSIGNMENT","HANDOVER_REQUEST","HANDOVER_NOTE","HANDOVER_FINALIZED","TRIAGE_CLOSED","HANDOVER_DESTINATION" -> "triage:handover";
  case "HANDOVER_ACCEPT","HANDOVER_REJECT","RECEIVER_ACKNOWLEDGEMENT" -> "triage:receive";
  default -> throw new IllegalArgumentException("unsupported triage action");
 };}
}
