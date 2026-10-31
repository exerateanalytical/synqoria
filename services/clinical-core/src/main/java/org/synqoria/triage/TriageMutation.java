package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Domain mutation boundary. A persistence adapter must do compare-and-swap and outbox atomically. */
public final class TriageMutation {
 private TriageMutation(){}
 public static final class Draft {
  TriageCase.Stage stage; UUID station,assignee;String priority,escalation,handover;boolean paused,hold,signed;
  Instant reassessment; Map<UUID,TriageCase.Observation> observations; Map<String,String> metadata;
  Draft(TriageCase c){stage=c.stage();station=c.station();assignee=c.assignee();priority=c.clinicianPriority();escalation=c.escalationReason();handover=c.handoverNote();paused=c.paused();hold=c.safetyHold();signed=c.assessedSigned();reassessment=c.lastReassessment();observations=new HashMap<>(c.observations());metadata=new HashMap<>(c.metadata());}
  public void require(TriageCase.Stage... allowed){if(Arrays.stream(allowed).noneMatch(s->s==stage))throw new IllegalStateException("triage action invalid in current stage");}
  public void requireOpen(){if(stage==TriageCase.Stage.CLOSED||stage==TriageCase.Stage.HANDED_OVER)throw new IllegalStateException("triage case cannot be edited");}
  public void requireNotHeld(){if(hold)throw new IllegalStateException("safety hold blocks progression");}
  public void metadata(String name,String value,int max){if(!TriageCase.safe(name,64)||!TriageCase.safe(value,max))throw new IllegalArgumentException("invalid triage field");metadata.put(name,value);}
 }
 public static TriageCase change(TrustedAccessContext identity,TrustedClinicalAccessEvidence evidence,TriageCase c,TriageCommand cmd,
     String capability,String operation,Consumer<Draft> action){
  if(c==null||cmd==null||action==null||identity==null)throw new SecurityException("triage resource unavailable");
  TriageAuthorization.require(identity,evidence,c,capability,cmd.trustedNow());
  if(!identity.actorId().equals(cmd.actor()))throw new SecurityException("triage actor mismatch");
  if(cmd.expectedRevision()!=c.revision())throw new IllegalStateException("stale triage revision");
  if(!TriageAudit.verify(c))throw new SecurityException("triage audit integrity unavailable");
  if(cmd.trustedNow().isBefore(c.arrivedAt()))throw new IllegalArgumentException("command time predates intake");
  for(var event:c.changes())if(event.commandId().equals(cmd.commandId()))throw new IllegalStateException("duplicate triage command");
  Draft d=new Draft(c);action.accept(d);
  if(c.revision()==Integer.MAX_VALUE)throw new IllegalStateException("triage revision exhausted");
  TriageCase candidate=new TriageCase(c.id(),c.tenant(),c.facility(),c.patient(),c.appointment(),d.station,d.assignee,
      d.stage,c.revision()+1,d.priority,d.escalation,d.handover,d.paused,d.hold,d.signed,c.arrivedAt(),d.reassessment,d.observations,d.metadata,c.changes());
  var event=TriageAudit.next(c,candidate,operation,cmd);
  return c.next(d.stage,d.station,d.assignee,d.priority,d.escalation,d.handover,d.paused,d.hold,d.signed,d.reassessment,d.observations,d.metadata,event);
 }
 public static void requireRationale(TriageCommand cmd){if(cmd==null||!TriageCase.safe(cmd.rationale(),500))throw new IllegalArgumentException("recorded reason required");}
}
