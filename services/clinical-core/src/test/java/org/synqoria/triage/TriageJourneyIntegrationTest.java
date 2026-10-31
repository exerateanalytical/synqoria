package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.*;
import java.util.*;

/** Synthetic, in-memory domain execution only. No PostgreSQL/HTTP or approved clinical priority semantics. */
public final class TriageJourneyIntegrationTest {
 private static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
 private static final UUID TENANT=UUID.randomUUID(), FACILITY=UUID.randomUUID(), PATIENT=UUID.randomUUID(), ACTOR=UUID.randomUUID(), STATION=UUID.randomUUID();
 private static final Set<String> CAPS=Set.of("triage:intake","triage:queue","triage:manage","triage:assess","triage:sign","triage:prioritize","triage:handover","triage:receive","triage:read");
 private static final TrustedAccessContext CTX=new TrustedAccessContext(TENANT,FACILITY,ACTOR,CAPS,Set.of(PATIENT));
 private static final TrustedClinicalAccessEvidence PROOF=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(3600),NOW.plusSeconds(3600),true,true,true,true,true,true,true,CAPS);
 private static final TriageIntakeService INTAKE=new TriageIntakeService();private static final TriageQueueService QUEUE=new TriageQueueService();
 private static final TriageAssessmentService ASSESSMENT=new TriageAssessmentService();private static final TriagePriorityService PRIORITY=new TriagePriorityService();private static final TriageHandoverService HANDOVER=new TriageHandoverService();
 private static int checks=0;
 private static void ok(boolean value){checks++;if(!value)throw new AssertionError("assertion "+checks);}
 private static TriageCommand q(TriageCase c){return new TriageCommand(UUID.randomUUID(),ACTOR,NOW,c.revision(),"Reviewed rationale");}
 private static void reject(Runnable op){checks++;try{op.run();throw new AssertionError("unsafe change accepted");}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 public static void main(String[] ignored){
   TriageCase c=INTAKE.register(CTX,PROOF,UUID.randomUUID(),PATIENT,UUID.randomUUID(),NOW.minusSeconds(900));
   TriageCase original=c;
   c=INTAKE.recordArrivalChannel(CTX,PROOF,c,q(c),"APPOINTMENT");
   c=QUEUE.enqueue(CTX,PROOF,c,q(c),STATION);
   c=QUEUE.call(CTX,PROOF,c,q(c));
   c=ASSESSMENT.begin(CTX,PROOF,c,q(c));
   UUID observation=UUID.randomUUID();
   c=ASSESSMENT.recordObservation(CTX,PROOF,c,q(c),new TriageCase.Observation(observation,"pulse","72","/min",NOW.minusSeconds(100),ACTOR,null));
   c=ASSESSMENT.attestAssessment(CTX,PROOF,c,q(c));
   c=PRIORITY.setClinicianPriority(CTX,PROOF,c,q(c),"recorded-by-clinician");
   c=HANDOVER.assignClinician(CTX,PROOF,c,q(c),ACTOR);
   c=HANDOVER.requestHandover(CTX,PROOF,c,q(c));
   c=HANDOVER.acceptHandover(CTX,PROOF,c,q(c));
   c=HANDOVER.recordHandoverNote(CTX,PROOF,c,q(c),"Receiving team aware of current observations");
   c=HANDOVER.documentReceiverAcknowledgement(CTX,PROOF,c,q(c));
   c=HANDOVER.finalizeHandover(CTX,PROOF,c,q(c));
   c=HANDOVER.closeTriage(CTX,PROOF,c,q(c));
   ok(original.revision()==1 && original.stage()==TriageCase.Stage.ARRIVED);
   ok(c.revision()==15 && c.stage()==TriageCase.Stage.CLOSED);
   ok(c.observations().size()==1 && c.clinicianPriority().equals("recorded-by-clinician"));
   ok(TriageAudit.verify(c)&&c.changes().size()==14);
   ok(new TriageReadService().caseHistory(CTX,PROOF,c,NOW).size()==14);
   TriageCase closed=c;reject(()->HANDOVER.closeTriage(CTX,PROOF,closed,q(closed)));
   TriageCase tampered=new TriageCase(c.id(),c.tenant(),c.facility(),c.patient(),c.appointment(),c.station(),c.assignee(),c.stage(),c.revision(),"tampered",c.escalationReason(),c.handoverNote(),c.paused(),c.safetyHold(),c.assessedSigned(),c.arrivedAt(),c.lastReassessment(),c.observations(),c.metadata(),c.changes());
   ok(!TriageAudit.verify(tampered));
   reject(()->new TriageReadService().caseHistory(CTX,PROOF,tampered,NOW));
   var deniedCtx=new TrustedAccessContext(UUID.randomUUID(),FACILITY,ACTOR,CAPS,Set.of(PATIENT));
   reject(()->new TriageReadService().caseSummary(deniedCtx,PROOF,closed,NOW));
   var expired=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(3600),NOW.minusSeconds(1),true,true,true,true,true,true,true,CAPS);
   reject(()->new TriageReadService().caseSummary(CTX,expired,closed,NOW));
   var revoked=new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(3600),NOW.plusSeconds(3600),true,true,true,true,true,false,true,CAPS);
   reject(()->new TriageReadService().caseSummary(CTX,revoked,closed,NOW));
   var unrelated=new TrustedAccessContext(TENANT,FACILITY,ACTOR,CAPS,Set.of(UUID.randomUUID()));
   reject(()->new TriageReadService().caseSummary(unrelated,PROOF,closed,NOW));
   System.out.println("TRIAGE_SYNTHETIC_JOURNEY_PASS checks="+checks+" events="+c.changes().size()+" terminal="+c.stage());
 }
}
