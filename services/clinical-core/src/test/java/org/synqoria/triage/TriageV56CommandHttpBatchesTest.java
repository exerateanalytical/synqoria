package org.synqoria.triage;
import java.util.*;
/** One independently runnable real-loopback HTTP feature per canonical M03 mutation. */
public final class TriageV56CommandHttpBatchesTest {
 private static int assertions;
 private static void ok(boolean v){assertions++;if(!v)throw new AssertionError("M03 HTTP feature assertion "+assertions);}
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,A=TriageWorkflowBatchesTest.A,
 ST=TriageWorkflowBatchesTest.ST,CASE=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final java.time.Instant NOW=TriageWorkflowBatchesTest.NOW;
 static TriageCase base(TriageCase.Stage s){return TriageWorkflowBatchesTest.base(s);}
 static TriageCase special(TriageCase.Stage s,Map<String,String> m,String e,boolean h,boolean p,Map<UUID,TriageCase.Observation> o){return TriageWorkflowBatchesTest.special(s,m,e,h,p,o);}
 private static String q(String x){return TriageReadHttpHandler.quoteJson(x);}
 private static String body(TriageAuthenticatedCommandGateway.Input x){
  StringBuilder sb=new StringBuilder("{\"commandId\":\"11111111-1111-1111-1111-111111111111\",\"expectedRevision\":1,\"action\":");
  sb.append(q(x.action())).append(",\"rationale\":\"reviewed reason\"");
  if(x.text()!=null)sb.append(",\"text\":").append(q(x.text()));
  if(x.reference()!=null)sb.append(",\"reference\":").append(q(x.reference().toString()));
  if(x.observation()!=null){var o=x.observation();sb.append(",\"observation\":{\"id\":").append(q(o.id().toString()));
   sb.append(",\"code\":").append(q(o.code())).append(",\"value\":").append(q(o.value()));
   sb.append(",\"unit\":").append(q(o.unit())).append(",\"takenAt\":").append(q(o.takenAt().toString()));
   if(o.replaces()!=null)sb.append(",\"replaces\":").append(q(o.replaces().toString()));
   sb.append('}');
  }
  return sb.append('}').toString();
 }
 static void exercise(TriageCase c,TriageAuthenticatedCommandGateway.Input input)throws Exception{
  try(var f=new TriageV56HttpIntegrationTest.Fixture(c)){
   String payload=body(input);
   var success=f.post("/commands",payload);
   ok(success.statusCode()==200);
   ok(success.body().contains("\"revision\":2")&&!success.body().contains(input.text()==null?"SYNQORIA-NEVER-IN-RESPONSE":input.text()));
   var after=f.current();ok(after!=null&&after.revision()==2&&TriageAudit.verify(after));
   ok(after.changes().size()==1&&after.changes().get(0).operation().equals(input.action()));
   TriageWorkflowBatchesTest.assertEffect(input.action(),after);
   ok(f.store.audits.size()==1&&f.store.receipts.size()==1&&f.store.outbox.size()==1);
   var replay=f.post("/commands",payload);
   ok(replay.statusCode()==200&&replay.body().contains("\"replay\":true"));
   ok(f.store.audits.size()==1&&f.store.outbox.size()==1&&f.store.receipts.size()==1);
   var mismatched=f.post("/commands",payload.replace("11111111-1111-1111-1111-111111111111","22222222-2222-2222-2222-222222222222"));
   ok(mismatched.statusCode()==412);
   ok(f.current().revision()==2);
  }
 }
 static void run(String id)throws Exception{switch(id){
 case "P06-B0303" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ARRIVAL_REASON","Abdominal discomfort"); exercise(c,input); } // newly HTTP-validated P06-B0169
 case "P06-B0304" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("LANGUAGE","fr"); exercise(c,input); } // newly HTTP-validated P06-B0170
 case "P06-B0305" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("INTERPRETER_REQUEST","fr"); exercise(c,input); } // newly HTTP-validated P06-B0171
 case "P06-B0306" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ACCESSIBILITY_SUPPORT","wheelchair accessible route"); exercise(c,input); } // newly HTTP-validated P06-B0172
 case "P06-B0307" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("IDENTITY_VERIFICATION","identity-ref-123"); exercise(c,input); } // newly HTTP-validated P06-B0173
 case "P06-B0308" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ARRIVAL_CHANNEL","REFERRAL"); exercise(c,input); } // newly HTTP-validated P06-B0174
 case "P06-B0309" -> { TriageCase c=special(TriageCase.Stage.ARRIVED,Map.of("arrivalChannel","REFERRAL"),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("REFERRAL_REFERENCE","referrer-123"); exercise(c,input); } // newly HTTP-validated P06-B0175
 case "P06-B0310" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ATTENDANT_CONTACT","attendant-ref-123"); exercise(c,input); } // newly HTTP-validated P06-B0176
 case "P06-B0311" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("CONSENT_EVIDENCE","consent-ref-123"); exercise(c,input); } // newly HTTP-validated P06-B0177
 case "P06-B0312" -> { TriageCase c=base(TriageCase.Stage.ARRIVED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.reference("QUEUE_ENQUEUE",ST); exercise(c,input); } // newly HTTP-validated P06-B0178
 case "P06-B0313" -> { TriageCase c=base(TriageCase.Stage.WAITING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_CALL"); exercise(c,input); } // newly HTTP-validated P06-B0179
 case "P06-B0314" -> { TriageCase c=base(TriageCase.Stage.CALLED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RECALL"); exercise(c,input); } // newly HTTP-validated P06-B0180
 case "P06-B0315" -> { TriageCase c=base(TriageCase.Stage.CALLED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RETURN"); exercise(c,input); } // newly HTTP-validated P06-B0181
 case "P06-B0316" -> { TriageCase c=base(TriageCase.Stage.WAITING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_PAUSE"); exercise(c,input); } // newly HTTP-validated P06-B0182
 case "P06-B0317" -> { TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,false,true,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_RESUME"); exercise(c,input); } // newly HTTP-validated P06-B0183
 case "P06-B0318" -> { TriageCase c=base(TriageCase.Stage.WAITING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.reference("QUEUE_MOVE_STATION",OTHER); exercise(c,input); } // newly HTTP-validated P06-B0184
 case "P06-B0319" -> { TriageCase c=base(TriageCase.Stage.CALLED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("QUEUE_PATIENT_UNAVAILABLE"); exercise(c,input); } // newly HTTP-validated P06-B0185
 case "P06-B0320" -> { TriageCase c=base(TriageCase.Stage.WAITING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("SAFETY_HOLD"); exercise(c,input); } // newly HTTP-validated P06-B0186
 case "P06-B0321" -> { TriageCase c=special(TriageCase.Stage.WAITING,Map.of(),null,true,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("SAFETY_HOLD_RELEASE"); exercise(c,input); } // newly HTTP-validated P06-B0187
 case "P06-B0322" -> { TriageCase c=base(TriageCase.Stage.CALLED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("ASSESSMENT_BEGIN"); exercise(c,input); } // newly HTTP-validated P06-B0188
 case "P06-B0323" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.observation("OBSERVATION_RECORD",new TriageCase.Observation(UUID.randomUUID(),"temperature","37.2","C",NOW.minusSeconds(60),A,null)); exercise(c,input); } // newly HTTP-validated P06-B0189
 case "P06-B0324" -> { TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of(),null,false,false,Map.of(OTHER,new TriageCase.Observation(OTHER,"temperature","37.2","C",NOW.minusSeconds(60),A,null))); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.observation("OBSERVATION_CORRECTION",new TriageCase.Observation(UUID.randomUUID(),"temperature","37.3","C",NOW.minusSeconds(5),A,OTHER)); exercise(c,input); } // newly HTTP-validated P06-B0190
 case "P06-B0325" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_NARRATIVE","Patient reports symptoms"); exercise(c,input); } // newly HTTP-validated P06-B0191
 case "P06-B0326" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_DEVICE_SOURCE","device-123"); exercise(c,input); } // newly HTTP-validated P06-B0192
 case "P06-B0327" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("PATIENT_STATEMENT","Statement as reported"); exercise(c,input); } // newly HTTP-validated P06-B0193
 case "P06-B0328" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ALLERGY_REVIEW_REFERENCE","review-123"); exercise(c,input); } // newly HTTP-validated P06-B0194
 case "P06-B0329" -> { TriageCase c=special(TriageCase.Stage.ASSESSING,Map.of("assessmentNarrative","Documented"),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("ASSESSMENT_ATTEST"); exercise(c,input); } // newly HTTP-validated P06-B0195
 case "P06-B0330" -> { TriageCase c=base(TriageCase.Stage.ASSESSED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("ASSESSMENT_ADDENDUM","Followup addendum"); exercise(c,input); } // newly HTTP-validated P06-B0196
 case "P06-B0331" -> { TriageCase c=base(TriageCase.Stage.ASSESSING); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("MEASUREMENT_DECLINED","temperature"); exercise(c,input); } // newly HTTP-validated P06-B0197
 case "P06-B0332" -> { TriageCase c=base(TriageCase.Stage.ASSESSED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("CLINICIAN_PRIORITY_SET","clinician-level-2"); exercise(c,input); } // newly HTTP-validated P06-B0198
 case "P06-B0333" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("CLINICIAN_PRIORITY_REVISED","clinician-level-2"); exercise(c,input); } // newly HTTP-validated P06-B0199
 case "P06-B0334" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_REQUESTED"); exercise(c,input); } // newly HTTP-validated P06-B0200
 case "P06-B0335" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_STARTED"); exercise(c,input); } // newly HTTP-validated P06-B0201
 case "P06-B0336" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentStartedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("REASSESSMENT_DOCUMENTED","Clinician reassessed patient"); exercise(c,input); } // newly HTTP-validated P06-B0202
 case "P06-B0337" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentNarrative","Reassessed"),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("REASSESSMENT_COMPLETED"); exercise(c,input); } // newly HTTP-validated P06-B0203
 case "P06-B0338" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_RAISED"); exercise(c,input); } // newly HTTP-validated P06-B0204
 case "P06-B0339" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of(),"review required",false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_ACKNOWLEDGED"); exercise(c,input); } // newly HTTP-validated P06-B0205
 case "P06-B0340" -> { TriageCase c=special(TriageCase.Stage.PRIORITIZED,Map.of("escalationAcknowledgedBy",A.toString()),"review required",false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("ESCALATION_RESOLVED"); exercise(c,input); } // newly HTTP-validated P06-B0206
 case "P06-B0341" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("PRIORITY_EVIDENCE_LINK","priority-source-123"); exercise(c,input); } // newly HTTP-validated P06-B0207
 case "P06-B0342" -> { TriageCase c=base(TriageCase.Stage.PRIORITIZED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.reference("CLINICIAN_ASSIGNMENT",A); exercise(c,input); } // newly HTTP-validated P06-B0208
 case "P06-B0343" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.reference("CLINICIAN_REASSIGNMENT",OTHER); exercise(c,input); } // newly HTTP-validated P06-B0209
 case "P06-B0344" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_REQUEST"); exercise(c,input); } // newly HTTP-validated P06-B0210
 case "P06-B0345" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_ACCEPT"); exercise(c,input); } // newly HTTP-validated P06-B0211
 case "P06-B0346" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_REJECT"); exercise(c,input); } // newly HTTP-validated P06-B0212
 case "P06-B0347" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("HANDOVER_NOTE","Clinical handover description"); exercise(c,input); } // newly HTTP-validated P06-B0213
 case "P06-B0348" -> { TriageCase c=new TriageCase(CASE,T,F,P,null,ST,A,TriageCase.Stage.ASSIGNED,1,"clinician priority",null,"handover note",false,false,true,NOW.minusSeconds(3600),null,Map.of(),Map.of("handoverAcceptedAt",NOW.minusSeconds(50).toString()),List.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("RECEIVER_ACKNOWLEDGEMENT"); exercise(c,input); } // newly HTTP-validated P06-B0214
 case "P06-B0349" -> { TriageCase c=special(TriageCase.Stage.ASSIGNED,Map.of("receiverAcknowledgedAt",NOW.minusSeconds(50).toString()),null,false,false,Map.of()); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("HANDOVER_FINALIZED"); exercise(c,input); } // newly HTTP-validated P06-B0215
 case "P06-B0350" -> { TriageCase c=base(TriageCase.Stage.HANDED_OVER); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.simple("TRIAGE_CLOSED"); exercise(c,input); } // newly HTTP-validated P06-B0216
 case "P06-B0351" -> { TriageCase c=base(TriageCase.Stage.ASSIGNED); TriageAuthenticatedCommandGateway.Input input=TriageAuthenticatedCommandGateway.Input.text("HANDOVER_DESTINATION","consultation-room-3"); exercise(c,input); } // newly HTTP-validated P06-B0217
 default -> throw new IllegalArgumentException("unknown M03 HTTP batch ID "+id);
 }}
 public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("one batch ID required");run(args[0]);System.out.println(args[0]+" PASS assertions="+assertions);}
}
