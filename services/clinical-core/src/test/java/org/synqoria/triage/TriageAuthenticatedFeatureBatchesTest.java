package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.*;
import java.util.*;
/** 49 distinct application command integrations, each tested in its own JVM; simulated atomic store only. */
public final class TriageAuthenticatedFeatureBatchesTest {
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,A=TriageWorkflowBatchesTest.A,ST=TriageWorkflowBatchesTest.ST,CASE=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final TrustedAccessContext a=TriageWorkflowBatchesTest.a;
 static final TrustedClinicalAccessEvidence e=TriageWorkflowBatchesTest.e;
 static TriageCase base(TriageCase.Stage stage){return TriageWorkflowBatchesTest.base(stage);}
 static TriageCase special(TriageCase.Stage stage,Map<String,String> metadata,String escalation,boolean hold,boolean paused,Map<UUID,TriageCase.Observation> observations){return TriageWorkflowBatchesTest.special(stage,metadata,escalation,hold,paused,observations);}
 private static int checks=0;
 static void ok(boolean condition){checks++;if(!condition)throw new AssertionError("command integration assertion "+checks);}
 static void reject(Runnable task){checks++;try{task.run();throw new AssertionError("denial expected");}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 static void exercise(TriageCase c,TriageAuthenticatedCommandGateway.Input input){
  var store=new TriageFakeAtomicStore();store.seed(c);
  var gateway=new TriageAuthenticatedCommandGateway(new TriageCommandCoordinator(store));
  var scope=new TriagePersistencePort.Scope(T,F,P,CASE);
  var command=new TriageCommand(UUID.randomUUID(),A,NOW,c.revision(),"reviewed reason");
  var result=gateway.execute(a,e,scope,command,input);
  ok(result.revision()==c.revision()+1&&!result.replay());
  TriageCase after=store.cases.get(scope);
  ok(after!=null&&after.revision()==2&&TriageAudit.verify(after));
  ok(after.changes().size()==1&&input.action().equals(after.changes().get(0).operation()));
  TriageWorkflowBatchesTest.assertEffect(input.action(),after);
  ok(store.audits.size()==1&&store.outbox.size()==1&&store.receipts.size()==1);
  ok(store.outbox.get(command.commandId()).equals(result.auditDigest()));
  byte[] roundtrip=TriageSnapshotCodec.encode(after);ok(TriageAudit.verify(TriageSnapshotCodec.decode(roundtrip)));
  ok(gateway.execute(a,e,scope,command,input).replay());
  ok(store.receipts.size()==1&&store.outbox.size()==1);
  reject(()->gateway.execute(a,e,scope,new TriageCommand(UUID.randomUUID(),A,NOW,1,"reviewed reason"),input));
  var wrongActor=new TrustedAccessContext(T,F,UUID.randomUUID(),TriageWorkflowBatchesTest.CAPS,Set.of(P));
  reject(()->gateway.execute(wrongActor,e,scope,command,input));
  var wrongScope=new TriagePersistencePort.Scope(T,F,UUID.randomUUID(),CASE);
  reject(()->gateway.execute(a,e,wrongScope,command,input));
  // The gateway must reject superfluous parameters that could change the idempotency digest without domain effect.
  var invalid=new TriageAuthenticatedCommandGateway.Input(input.action(),input.text(),input.reference(),input.observation());
  var bad=invalid.text()==null?new TriageAuthenticatedCommandGateway.Input(invalid.action(),"extra",invalid.reference(),invalid.observation()):new TriageAuthenticatedCommandGateway.Input(invalid.action(),invalid.text(),UUID.randomUUID(),invalid.observation());
  reject(()->gateway.execute(a,e,scope,new TriageCommand(UUID.randomUUID(),A,NOW,2,"reviewed reason"),bad));
 }
 static void scenario(String id){switch(id){
case "P06-B0169" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ARRIVAL_REASON","Abdominal discomfort")); } // P06-B0095: ARRIVAL_REASON
case "P06-B0170" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("LANGUAGE","fr")); } // P06-B0096: LANGUAGE
case "P06-B0171" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("INTERPRETER_REQUEST","fr")); } // P06-B0097: INTERPRETER_REQUEST
case "P06-B0172" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ACCESSIBILITY_SUPPORT","wheelchair accessible route")); } // P06-B0098: ACCESSIBILITY_SUPPORT
case "P06-B0173" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("IDENTITY_VERIFICATION","identity-ref-123")); } // P06-B0099: IDENTITY_VERIFICATION
case "P06-B0174" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ARRIVAL_CHANNEL","REFERRAL")); } // P06-B0100: ARRIVAL_CHANNEL
case "P06-B0175" -> { TriageCase c=special(TriageCase.Stage.ARRIVED,Map.of("arrivalChannel","REFERRAL"),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.text("REFERRAL_REFERENCE","referrer-123")); } // P06-B0101: REFERRAL_REFERENCE
case "P06-B0176" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ATTENDANT_CONTACT","attendant-ref-123")); } // P06-B0102: ATTENDANT_CONTACT
case "P06-B0177" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("CONSENT_EVIDENCE","consent-ref-123")); } // P06-B0103: CONSENT_EVIDENCE
case "P06-B0178" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); exercise(c,TriageAuthenticatedCommandGateway.Input.reference("QUEUE_ENQUEUE",ST)); } // P06-B0104: QUEUE_ENQUEUE
case "P06-B0179" -> { TriageCase c=base(TriageCase.Stage.WAITING); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_CALL")); } // P06-B0105: QUEUE_CALL
case "P06-B0180" -> { TriageCase c=base(TriageCase.Stage.CALLED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RECALL")); } // P06-B0106: QUEUE_RECALL
case "P06-B0181" -> { TriageCase c=base(TriageCase.Stage.CALLED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RETURN")); } // P06-B0107: QUEUE_RETURN
case "P06-B0182" -> { TriageCase c=base(TriageCase.Stage.WAITING); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_PAUSE")); } // P06-B0108: QUEUE_PAUSE
case "P06-B0183" -> { TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,false,true,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RESUME")); } // P06-B0109: QUEUE_RESUME
case "P06-B0184" -> { TriageCase c=base(TriageCase.Stage.WAITING); exercise(c,TriageAuthenticatedCommandGateway.Input.reference("QUEUE_MOVE_STATION",OTHER)); } // P06-B0110: QUEUE_MOVE_STATION
case "P06-B0185" -> { TriageCase c=base(TriageCase.Stage.CALLED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("QUEUE_PATIENT_UNAVAILABLE")); } // P06-B0111: QUEUE_PATIENT_UNAVAILABLE
case "P06-B0186" -> { TriageCase c=base(TriageCase.Stage.WAITING); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("SAFETY_HOLD")); } // P06-B0112: SAFETY_HOLD
case "P06-B0187" -> { TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,true,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("SAFETY_HOLD_RELEASE")); } // P06-B0113: SAFETY_HOLD_RELEASE
case "P06-B0188" -> { TriageCase c=base(TriageCase.Stage.CALLED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("ASSESSMENT_BEGIN")); } // P06-B0114: ASSESSMENT_BEGIN
case "P06-B0189" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.observation("OBSERVATION_RECORD",new TriageCase.Observation(UUID.randomUUID(),"temperature","37.2","C",NOW.minusSeconds(60),A,null))); } // P06-B0115: OBSERVATION_RECORD
case "P06-B0190" -> { TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of(),null,false,false,Map.of(OTHER,new TriageCase.Observation(OTHER,"temperature","37.2","C",NOW.minusSeconds(60),A,null))); exercise(c,TriageAuthenticatedCommandGateway.Input.observation("OBSERVATION_CORRECTION",new TriageCase.Observation(UUID.randomUUID(),"temperature","37.3","C",NOW.minusSeconds(5),A,OTHER))); } // P06-B0116: OBSERVATION_CORRECTION
case "P06-B0191" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_NARRATIVE","Patient reports symptoms")); } // P06-B0117: ASSESSMENT_NARRATIVE
case "P06-B0192" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_DEVICE_SOURCE","device-123")); } // P06-B0118: ASSESSMENT_DEVICE_SOURCE
case "P06-B0193" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.text("PATIENT_STATEMENT","Statement as reported")); } // P06-B0119: PATIENT_STATEMENT
case "P06-B0194" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ALLERGY_REVIEW_REFERENCE","review-123")); } // P06-B0120: ALLERGY_REVIEW_REFERENCE
case "P06-B0195" -> { TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of("assessmentNarrative","Documented"),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("ASSESSMENT_ATTEST")); } // P06-B0121: ASSESSMENT_ATTEST
case "P06-B0196" -> { TriageCase c=base(TriageCase.Stage.ASSESSED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_ADDENDUM","Followup addendum")); } // P06-B0122: ASSESSMENT_ADDENDUM
case "P06-B0197" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); exercise(c,TriageAuthenticatedCommandGateway.Input.text("MEASUREMENT_DECLINED","temperature")); } // P06-B0123: MEASUREMENT_DECLINED
case "P06-B0198" -> { TriageCase c=base(TriageCase.Stage.ASSESSED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("CLINICIAN_PRIORITY_SET","clinician-level-2")); } // P06-B0124: CLINICIAN_PRIORITY_SET
case "P06-B0199" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("CLINICIAN_PRIORITY_REVISED","clinician-level-2")); } // P06-B0125: CLINICIAN_PRIORITY_REVISED
case "P06-B0200" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_REQUESTED")); } // P06-B0126: REASSESSMENT_REQUESTED
case "P06-B0201" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_STARTED")); } // P06-B0127: REASSESSMENT_STARTED
case "P06-B0202" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentStartedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.text("REASSESSMENT_DOCUMENTED","Clinician reassessed patient")); } // P06-B0128: REASSESSMENT_DOCUMENTED
case "P06-B0203" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentNarrative","Reassessed"),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_COMPLETED")); } // P06-B0129: REASSESSMENT_COMPLETED
case "P06-B0204" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_RAISED")); } // P06-B0130: ESCALATION_RAISED
case "P06-B0205" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of(),"review required",false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_ACKNOWLEDGED")); } // P06-B0131: ESCALATION_ACKNOWLEDGED
case "P06-B0206" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("escalationAcknowledgedBy",A.toString()),"review required",false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_RESOLVED")); } // P06-B0132: ESCALATION_RESOLVED
case "P06-B0207" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("PRIORITY_EVIDENCE_LINK","priority-source-123")); } // P06-B0133: PRIORITY_EVIDENCE_LINK
case "P06-B0208" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); exercise(c,TriageAuthenticatedCommandGateway.Input.reference("CLINICIAN_ASSIGNMENT",A)); } // P06-B0134: CLINICIAN_ASSIGNMENT
case "P06-B0209" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); exercise(c,TriageAuthenticatedCommandGateway.Input.reference("CLINICIAN_REASSIGNMENT",OTHER)); } // P06-B0135: CLINICIAN_REASSIGNMENT
case "P06-B0210" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_REQUEST")); } // P06-B0136: HANDOVER_REQUEST
case "P06-B0211" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_ACCEPT")); } // P06-B0137: HANDOVER_ACCEPT
case "P06-B0212" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_REJECT")); } // P06-B0138: HANDOVER_REJECT
case "P06-B0213" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.text("HANDOVER_NOTE","Clinical handover description")); } // P06-B0139: HANDOVER_NOTE
case "P06-B0214" -> { TriageCase c=new TriageCase(CASE,T,F,P,null,ST,A,TriageCase.Stage.ASSIGNED,1,"clinician priority",null,"handover note",false,false,true,NOW.minusSeconds(3600),null,Map.of(),Map.of("handoverAcceptedAt",NOW.minusSeconds(50).toString()),List.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("RECEIVER_ACKNOWLEDGEMENT")); } // P06-B0140: RECEIVER_ACKNOWLEDGEMENT
case "P06-B0215" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("receiverAcknowledgedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_FINALIZED")); } // P06-B0141: HANDOVER_FINALIZED
case "P06-B0216" -> { TriageCase c=base(TriageCase.Stage.HANDED_OVER); exercise(c,TriageAuthenticatedCommandGateway.Input.simple("TRIAGE_CLOSED")); } // P06-B0142: TRIAGE_CLOSED
case "P06-B0217" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); exercise(c,TriageAuthenticatedCommandGateway.Input.text("HANDOVER_DESTINATION","consultation-room-3")); } // P06-B0143: HANDOVER_DESTINATION
default -> throw new IllegalArgumentException("unknown batch");
 }}
 public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("one batch ID required");scenario(args[0]);System.out.println(args[0]+" PASS checks="+checks);}
}
