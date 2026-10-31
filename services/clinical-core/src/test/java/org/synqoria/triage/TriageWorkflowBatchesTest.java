package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
public final class TriageWorkflowBatchesTest {
 static final UUID T=UUID.fromString("00000000-0000-0000-0000-000000000001"),F=UUID.fromString("00000000-0000-0000-0000-000000000002"),
  P=UUID.fromString("00000000-0000-0000-0000-000000000003"), A=UUID.fromString("00000000-0000-0000-0000-000000000004"),
  ST=UUID.fromString("00000000-0000-0000-0000-000000000005"), CASE=UUID.fromString("00000000-0000-0000-0000-000000000006"),
  OTHER=UUID.fromString("00000000-0000-0000-0000-000000000007");
 static final Instant NOW=Instant.parse("2026-10-30T15:00:00Z");
 static final Set<String> CAPS=Set.of("triage:intake","triage:queue","triage:manage","triage:assess","triage:sign","triage:prioritize","triage:handover","triage:receive","triage:read");
 static TrustedAccessContext a=new TrustedAccessContext(T,F,A,CAPS,Set.of(P));
 static TrustedClinicalAccessEvidence e=new TrustedClinicalAccessEvidence(T,F,A,P,NOW.minusSeconds(60),NOW.plusSeconds(3600),true,true,true,true,true,true,true,CAPS);
 static TriageIntakeService intake=new TriageIntakeService(); static TriageQueueService queue=new TriageQueueService();
 static TriageAssessmentService assessment=new TriageAssessmentService();static TriagePriorityService priority=new TriagePriorityService();
 static TriageHandoverService handover=new TriageHandoverService(); static TriageReadService read=new TriageReadService();
 static TriageCommand cmd(TriageCase c){return new TriageCommand(UUID.randomUUID(),A,NOW,c.revision(),"reviewed reason");}
 static TriageCase base(TriageCase.Stage stage){return special(stage,Map.of(),null,false,false,Map.of());}
 static TriageCase special(TriageCase.Stage stage,Map<String,String> metadata,String escalation,boolean hold,boolean paused,Map<UUID,TriageCase.Observation> observations){
   return new TriageCase(CASE,T,F,P,null,ST,A,stage,1,"clinician-entered-priority",escalation,null,paused,hold,true,NOW.minusSeconds(3600),null,observations,metadata,List.of());
 }
 static void yes(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void denied(Runnable task){try{task.run();throw new AssertionError("denial required");}catch(SecurityException expected){}}
 static void stale(Runnable task){try{task.run();throw new AssertionError("stale revision must fail");}catch(IllegalStateException expected){}}
 static int completed=0;static List<String> results=new ArrayList<>();
 static void run(String id,String description,Runnable scenario){try{scenario.run();completed++;results.add(id+" PASS "+description);System.out.println(id+" PASS "+description);}catch(Throwable failure){results.add(id+" FAIL "+failure.getMessage());System.err.println(id+" FAIL "+failure);throw failure;}}
 static void exercise(String op,TriageCase c,Function<TriageCommand,TriageCase> execute){
   TriageCommand q=cmd(c);TriageCase after=execute.apply(q);
   yes(after.revision()==2,"revision not incremented");
   yes(after.changes().size()==1,"audit event missing");
   yes(op.equals(after.changes().get(0).operation()),"wrong event");
   yes(TriageAudit.verify(after),"hash chain integrity broken");
   assertEffect(op,after);
   // Non-identical expected revision is rejected before domain mutation.
   stale(()->execute.apply(new TriageCommand(UUID.randomUUID(),A,NOW,0,"reviewed reason")));
   // A server-verified access evidence failure denies the same patient without revealing state.
   denied(()->TriageAuthorization.require(new TrustedAccessContext(OTHER,F,A,CAPS,Set.of(P)),e,c,"triage:read",NOW));
 }

 static void assertEffect(String op,TriageCase c){
  boolean effect=switch(op){
   case "ARRIVAL_REASON" -> c.metadata().containsKey("arrivalReason");
   case "LANGUAGE" -> c.metadata().containsKey("preferredLanguage");
   case "INTERPRETER_REQUEST" -> c.metadata().containsKey("interpreterRequested");
   case "ACCESSIBILITY_SUPPORT" -> c.metadata().containsKey("accessibilitySupport");
   case "IDENTITY_VERIFICATION" -> c.metadata().containsKey("identityEvidenceReference");
   case "ARRIVAL_CHANNEL" -> "REFERRAL".equals(c.metadata().get("arrivalChannel"));
   case "REFERRAL_REFERENCE" -> c.metadata().containsKey("referralReference");
   case "ATTENDANT_CONTACT" -> c.metadata().containsKey("attendantContactReference");
   case "CONSENT_EVIDENCE" -> c.metadata().containsKey("consentEvidenceReference");
   case "QUEUE_ENQUEUE" -> c.stage()==TriageCase.Stage.WAITING&&ST.equals(c.station());
   case "QUEUE_CALL" -> c.stage()==TriageCase.Stage.CALLED;
   case "QUEUE_RECALL" -> c.metadata().containsKey("recallAt");
   case "QUEUE_RETURN" -> c.stage()==TriageCase.Stage.WAITING;
   case "QUEUE_PAUSE" -> c.paused();
   case "QUEUE_RESUME" -> !c.paused();
   case "QUEUE_MOVE_STATION" -> OTHER.equals(c.station());
   case "QUEUE_PATIENT_UNAVAILABLE" -> c.stage()==TriageCase.Stage.WAITING&&c.paused();
   case "SAFETY_HOLD" -> c.safetyHold();
   case "SAFETY_HOLD_RELEASE" -> !c.safetyHold();
   case "ASSESSMENT_BEGIN" -> c.stage()==TriageCase.Stage.ASSESSING&&A.equals(c.assignee());
   case "OBSERVATION_RECORD" -> c.observations().size()==1;
   case "OBSERVATION_CORRECTION" -> c.observations().size()==2;
   case "ASSESSMENT_NARRATIVE" -> c.metadata().containsKey("assessmentNarrative");
   case "ASSESSMENT_DEVICE_SOURCE" -> c.metadata().containsKey("sourceDevice");
   case "PATIENT_STATEMENT" -> c.metadata().containsKey("patientStatement");
   case "ALLERGY_REVIEW_REFERENCE" -> c.metadata().containsKey("allergyReviewRef");
   case "ASSESSMENT_ATTEST" -> c.stage()==TriageCase.Stage.ASSESSED&&c.assessedSigned();
   case "ASSESSMENT_ADDENDUM" -> c.metadata().keySet().stream().anyMatch(k->k.startsWith("addendum-"));
   case "MEASUREMENT_DECLINED" -> c.metadata().containsKey("declined-temperature");
   case "CLINICIAN_PRIORITY_SET" -> c.stage()==TriageCase.Stage.PRIORITIZED&&"clinician-level-2".equals(c.clinicianPriority());
   case "CLINICIAN_PRIORITY_REVISED" -> "clinician-level-2".equals(c.clinicianPriority());
   case "REASSESSMENT_REQUESTED" -> c.metadata().containsKey("reassessmentRequestedAt");
   case "REASSESSMENT_STARTED" -> c.metadata().containsKey("reassessmentStartedAt");
   case "REASSESSMENT_DOCUMENTED" -> c.metadata().containsKey("reassessmentNarrative");
   case "REASSESSMENT_COMPLETED" -> NOW.equals(c.lastReassessment())&&!c.metadata().containsKey("reassessmentRequestedAt");
   case "ESCALATION_RAISED" -> c.escalationReason()!=null;
   case "ESCALATION_ACKNOWLEDGED" -> c.metadata().containsKey("escalationAcknowledgedBy");
   case "ESCALATION_RESOLVED" -> c.escalationReason()==null;
   case "PRIORITY_EVIDENCE_LINK" -> c.metadata().containsKey("priorityEvidenceRef");
   case "CLINICIAN_ASSIGNMENT" -> c.stage()==TriageCase.Stage.ASSIGNED&&A.equals(c.assignee());
   case "CLINICIAN_REASSIGNMENT" -> OTHER.equals(c.assignee());
   case "HANDOVER_REQUEST" -> c.metadata().containsKey("handoverRequestedAt");
   case "HANDOVER_ACCEPT" -> c.metadata().containsKey("handoverAcceptedAt");
   case "HANDOVER_REJECT" -> c.metadata().containsKey("handoverRejectReason")&&!c.metadata().containsKey("handoverRequestedAt");
   case "HANDOVER_NOTE" -> c.handoverNote()!=null;
   case "RECEIVER_ACKNOWLEDGEMENT" -> c.metadata().containsKey("receiverAcknowledgedAt");
   case "HANDOVER_FINALIZED" -> c.stage()==TriageCase.Stage.HANDED_OVER;
   case "TRIAGE_CLOSED" -> c.stage()==TriageCase.Stage.CLOSED;
   case "HANDOVER_DESTINATION" -> c.metadata().containsKey("handoverDestination");
   default -> false;
  };
  yes(effect,"operation produced no expected domain effect: "+op);
 }
 static void scenario(String id){switch(id){
case "P06-B0094" -> {TriageCase created=intake.register(a,e,OTHER,P,null,NOW);yes(created.revision()==1&&created.stage()==TriageCase.Stage.ARRIVED,"registration failed");
     denied(()->intake.register(new TrustedAccessContext(OTHER,F,A,CAPS,Set.of(P)),e,OTHER,P,null,NOW));}
case "P06-B0095" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("ARRIVAL_REASON",c,q->intake.recordArrivalReason(a,e,c,q,"Abdominal discomfort"));
     yes(c.metadata().isEmpty(),"original mutated");}
case "P06-B0096" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("LANGUAGE",c,q->intake.recordPreferredLanguage(a,e,c,q,"fr"));
     }
case "P06-B0097" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("INTERPRETER_REQUEST",c,q->intake.requestInterpreter(a,e,c,q,"fr"));
     }
case "P06-B0098" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("ACCESSIBILITY_SUPPORT",c,q->intake.recordAccessibilitySupport(a,e,c,q,"wheelchair accessible route"));
     }
case "P06-B0099" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("IDENTITY_VERIFICATION",c,q->intake.recordIdentityVerification(a,e,c,q,"identity-ref-123"));
     }
case "P06-B0100" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("ARRIVAL_CHANNEL",c,q->intake.recordArrivalChannel(a,e,c,q,"REFERRAL"));
     }
case "P06-B0101" -> {TriageCase c=special(TriageCase.Stage.ARRIVED,Map.of("arrivalChannel","REFERRAL"),null,false,false,Map.of());
     exercise("REFERRAL_REFERENCE",c,q->intake.recordReferralReference(a,e,c,q,"referrer-123"));
     }
case "P06-B0102" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("ATTENDANT_CONTACT",c,q->intake.recordAttendantContact(a,e,c,q,"attendant-ref-123"));
     }
case "P06-B0103" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("CONSENT_EVIDENCE",c,q->intake.recordConsentEvidence(a,e,c,q,"consent-ref-123"));
     }
case "P06-B0104" -> {TriageCase c=base(TriageCase.Stage.ARRIVED);
     exercise("QUEUE_ENQUEUE",c,q->queue.enqueue(a,e,c,q,ST));
     }
case "P06-B0105" -> {TriageCase c=base(TriageCase.Stage.WAITING);
     exercise("QUEUE_CALL",c,q->queue.call(a,e,c,q));
     }
case "P06-B0106" -> {TriageCase c=base(TriageCase.Stage.CALLED);
     exercise("QUEUE_RECALL",c,q->queue.recall(a,e,c,q));
     }
case "P06-B0107" -> {TriageCase c=base(TriageCase.Stage.CALLED);
     exercise("QUEUE_RETURN",c,q->queue.returnToWaiting(a,e,c,q));
     }
case "P06-B0108" -> {TriageCase c=base(TriageCase.Stage.WAITING);
     exercise("QUEUE_PAUSE",c,q->queue.pause(a,e,c,q));
     }
case "P06-B0109" -> {TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,false,true,Map.of());
     exercise("QUEUE_RESUME",c,q->queue.resume(a,e,c,q));
     }
case "P06-B0110" -> {TriageCase c=base(TriageCase.Stage.WAITING);
     exercise("QUEUE_MOVE_STATION",c,q->queue.moveStation(a,e,c,q,OTHER));
     }
case "P06-B0111" -> {TriageCase c=base(TriageCase.Stage.CALLED);
     exercise("QUEUE_PATIENT_UNAVAILABLE",c,q->queue.recordPatientUnavailable(a,e,c,q));
     }
case "P06-B0112" -> {TriageCase c=base(TriageCase.Stage.WAITING);
     exercise("SAFETY_HOLD",c,q->queue.placeSafetyHold(a,e,c,q));
     }
case "P06-B0113" -> {TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,true,false,Map.of());
     exercise("SAFETY_HOLD_RELEASE",c,q->queue.releaseSafetyHold(a,e,c,q));
     }
case "P06-B0114" -> {TriageCase c=base(TriageCase.Stage.CALLED);
     exercise("ASSESSMENT_BEGIN",c,q->assessment.begin(a,e,c,q));
     }
case "P06-B0115" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("OBSERVATION_RECORD",c,q->assessment.recordObservation(a,e,c,q,new TriageCase.Observation(UUID.randomUUID(),"temperature","37.2","C",NOW.minusSeconds(60),A,null)));
     }
case "P06-B0116" -> {TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of(),null,false,false,Map.of(OTHER,new TriageCase.Observation(OTHER,"temperature","37.2","C",NOW.minusSeconds(60),A,null)));
     exercise("OBSERVATION_CORRECTION",c,q->assessment.correctObservation(a,e,c,q,new TriageCase.Observation(UUID.randomUUID(),"temperature","37.3","C",NOW.minusSeconds(5),A,OTHER)));
     }
case "P06-B0117" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("ASSESSMENT_NARRATIVE",c,q->assessment.recordFreeText(a,e,c,q,"Patient reports symptoms"));
     }
case "P06-B0118" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("ASSESSMENT_DEVICE_SOURCE",c,q->assessment.recordSourceDevice(a,e,c,q,"device-123"));
     }
case "P06-B0119" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("PATIENT_STATEMENT",c,q->assessment.recordPatientStatement(a,e,c,q,"Statement as reported"));
     }
case "P06-B0120" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("ALLERGY_REVIEW_REFERENCE",c,q->assessment.recordAllergyReviewEvidence(a,e,c,q,"review-123"));
     }
case "P06-B0121" -> {TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of("assessmentNarrative","Documented"),null,false,false,Map.of());
     exercise("ASSESSMENT_ATTEST",c,q->assessment.attestAssessment(a,e,c,q));
     }
case "P06-B0122" -> {TriageCase c=base(TriageCase.Stage.ASSESSED);
     exercise("ASSESSMENT_ADDENDUM",c,q->assessment.recordAssessmentAddendum(a,e,c,q,"Followup addendum"));
     }
case "P06-B0123" -> {TriageCase c=base(TriageCase.Stage.ASSESSING);
     exercise("MEASUREMENT_DECLINED",c,q->assessment.recordDeclinedMeasurement(a,e,c,q,"temperature"));
     }
case "P06-B0124" -> {TriageCase c=base(TriageCase.Stage.ASSESSED);
     exercise("CLINICIAN_PRIORITY_SET",c,q->priority.setClinicianPriority(a,e,c,q,"clinician-level-2"));
     }
case "P06-B0125" -> {TriageCase c=base(TriageCase.Stage.PRIORITIZED);
     exercise("CLINICIAN_PRIORITY_REVISED",c,q->priority.reviseClinicianPriority(a,e,c,q,"clinician-level-2"));
     }
case "P06-B0126" -> {TriageCase c=base(TriageCase.Stage.PRIORITIZED);
     exercise("REASSESSMENT_REQUESTED",c,q->priority.requestReassessment(a,e,c,q));
     }
case "P06-B0127" -> {TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("REASSESSMENT_STARTED",c,q->priority.beginReassessment(a,e,c,q));
     }
case "P06-B0128" -> {TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentStartedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("REASSESSMENT_DOCUMENTED",c,q->priority.recordReassessment(a,e,c,q,"Clinician reassessed patient"));
     }
case "P06-B0129" -> {TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentNarrative","Reassessed"),null,false,false,Map.of());
     exercise("REASSESSMENT_COMPLETED",c,q->priority.completeReassessment(a,e,c,q));
     }
case "P06-B0130" -> {TriageCase c=base(TriageCase.Stage.PRIORITIZED);
     exercise("ESCALATION_RAISED",c,q->priority.raiseManualEscalation(a,e,c,q));
     }
case "P06-B0131" -> {TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of(),"review required",false,false,Map.of());
     exercise("ESCALATION_ACKNOWLEDGED",c,q->priority.acknowledgeEscalation(a,e,c,q));
     }
case "P06-B0132" -> {TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("escalationAcknowledgedBy",A.toString()),"review required",false,false,Map.of());
     exercise("ESCALATION_RESOLVED",c,q->priority.resolveEscalation(a,e,c,q));
     }
case "P06-B0133" -> {TriageCase c=base(TriageCase.Stage.PRIORITIZED);
     exercise("PRIORITY_EVIDENCE_LINK",c,q->priority.recordPriorityEvidence(a,e,c,q,"priority-source-123"));
     }
case "P06-B0134" -> {TriageCase c=base(TriageCase.Stage.PRIORITIZED);
     exercise("CLINICIAN_ASSIGNMENT",c,q->handover.assignClinician(a,e,c,q,A));
     }
case "P06-B0135" -> {TriageCase c=base(TriageCase.Stage.ASSIGNED);
     exercise("CLINICIAN_REASSIGNMENT",c,q->handover.reassignClinician(a,e,c,q,OTHER));
     }
case "P06-B0136" -> {TriageCase c=base(TriageCase.Stage.ASSIGNED);
     exercise("HANDOVER_REQUEST",c,q->handover.requestHandover(a,e,c,q));
     }
case "P06-B0137" -> {TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("HANDOVER_ACCEPT",c,q->handover.acceptHandover(a,e,c,q));
     }
case "P06-B0138" -> {TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("HANDOVER_REJECT",c,q->handover.rejectHandover(a,e,c,q));
     }
case "P06-B0139" -> {TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("HANDOVER_NOTE",c,q->handover.recordHandoverNote(a,e,c,q,"Clinical handover description"));
     }
case "P06-B0140" -> {TriageCase c=new TriageCase(CASE,T,F,P,null,ST,A,TriageCase.Stage.ASSIGNED,1,"clinician priority",null,"handover note",false,false,true,NOW.minusSeconds(3600),null,Map.of(),Map.of("handoverAcceptedAt",NOW.minusSeconds(50).toString()),List.of());
     exercise("RECEIVER_ACKNOWLEDGEMENT",c,q->handover.documentReceiverAcknowledgement(a,e,c,q));
     }
case "P06-B0141" -> {TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("receiverAcknowledgedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of());
     exercise("HANDOVER_FINALIZED",c,q->handover.finalizeHandover(a,e,c,q));
     }
case "P06-B0142" -> {TriageCase c=base(TriageCase.Stage.HANDED_OVER);
     exercise("TRIAGE_CLOSED",c,q->handover.closeTriage(a,e,c,q));
     }
case "P06-B0143" -> {TriageCase c=base(TriageCase.Stage.ASSIGNED);
     exercise("HANDOVER_DESTINATION",c,q->handover.recordDestination(a,e,c,q,"consultation-room-3"));
     }
case "P06-B0144" -> {var c=base(TriageCase.Stage.ASSESSED);var view=read.caseSummary(a,e,c,NOW);
    yes(view.id().equals(CASE)&&view.revision()==1,"case summary");
    denied(()->read.caseSummary(new TrustedAccessContext(OTHER,F,A,CAPS,Set.of(P)),e,c,NOW));}
case "P06-B0145" -> {var c=base(TriageCase.Stage.WAITING);var out=read.facilityQueue(a,e,List.of(c,base(TriageCase.Stage.CLOSED),base(TriageCase.Stage.ASSESSING)),NOW);
    yes(out.size()==1&&out.get(0).stage()==TriageCase.Stage.WAITING,"queue stage filtering");}
case "P06-B0146" -> {var out=read.stationQueue(a,e,List.of(base(TriageCase.Stage.WAITING)),NOW,ST);
    yes(out.size()==1&&read.stationQueue(a,e,List.of(base(TriageCase.Stage.WAITING)),NOW,OTHER).isEmpty(),"station filtering");}
case "P06-B0147" -> {var c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.toString()),null,false,false,Map.of());
    yes(read.pendingHandovers(a,e,List.of(c,base(TriageCase.Stage.WAITING)),NOW).size()==1,"handover queue");}
case "P06-B0148" -> {var c=special(TriageCase.Stage.ASSIGNED,Map.of(),"documented escalation",false,false,Map.of());
    var out=read.manualEscalations(a,e,List.of(c,base(TriageCase.Stage.ASSIGNED)),NOW);yes(out.size()==1&&out.get(0).manuallyEscalated(),"escalation list");}
case "P06-B0149" -> {var c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.toString()),null,false,false,Map.of());
    yes(read.pendingReassessments(a,e,List.of(c,base(TriageCase.Stage.PRIORITIZED)),NOW).size()==1,"reassessment filter");}
case "P06-B0150" -> {var c=special(TriageCase.Stage.WAITING,Map.of(),null,true,false,Map.of());
    yes(read.safetyHolds(a,e,List.of(c,base(TriageCase.Stage.WAITING)),NOW).size()==1,"safety hold filter");}
case "P06-B0151" -> {var out=read.clinicianWorklist(a,e,List.of(base(TriageCase.Stage.ASSIGNED),base(TriageCase.Stage.CLOSED)),NOW,A);
    yes(out.size()==1&&read.clinicianWorklist(a,e,List.of(base(TriageCase.Stage.ASSIGNED)),NOW,OTHER).isEmpty(),"clinician assignment isolation");}
case "P06-B0152" -> {var out=read.arrivalsSince(a,e,List.of(base(TriageCase.Stage.ASSESSED)),NOW,NOW.minusSeconds(7200));
    yes(out.size()==1&&read.arrivalsSince(a,e,List.of(base(TriageCase.Stage.ASSESSED)),NOW,NOW.minusSeconds(5)).isEmpty(),"arrival window");}
case "P06-B0153" -> {var c=base(TriageCase.Stage.WAITING);var next=queue.call(a,e,c,cmd(c));
    yes(read.caseHistory(a,e,next,NOW).size()==1&&TriageAudit.verify(next),"history integrity");
    denied(()->read.caseHistory(new TrustedAccessContext(OTHER,F,A,CAPS,Set.of(P)),e,next,NOW));}
default -> throw new IllegalArgumentException("unknown batch "+id);
 }}
 public static void main(String[] args){
  String[] all={"P06-B0094","P06-B0095","P06-B0096","P06-B0097","P06-B0098","P06-B0099","P06-B0100","P06-B0101","P06-B0102","P06-B0103","P06-B0104","P06-B0105","P06-B0106","P06-B0107","P06-B0108","P06-B0109","P06-B0110","P06-B0111","P06-B0112","P06-B0113","P06-B0114","P06-B0115","P06-B0116","P06-B0117","P06-B0118","P06-B0119","P06-B0120","P06-B0121","P06-B0122","P06-B0123","P06-B0124","P06-B0125","P06-B0126","P06-B0127","P06-B0128","P06-B0129","P06-B0130","P06-B0131","P06-B0132","P06-B0133","P06-B0134","P06-B0135","P06-B0136","P06-B0137","P06-B0138","P06-B0139","P06-B0140","P06-B0141","P06-B0142","P06-B0143","P06-B0144","P06-B0145","P06-B0146","P06-B0147","P06-B0148","P06-B0149","P06-B0150","P06-B0151","P06-B0152","P06-B0153"};
  if(args.length==1){run(args[0],"focused verified workflow",()->scenario(args[0]));}
  else for(int i=0;i<all.length;i++){String id=all[i];run(id,i<50?"scoped domain command + optimistic revision + tamper-evident audit":"authorized patient-scoped read projection",()->scenario(id));}
  System.out.println("VERIFIED_BATCHES="+completed);
 }
}
