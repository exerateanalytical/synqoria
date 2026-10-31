package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
/** Security-filtered read projections only; not a cache, HTTP handler or authorized replacement for PostgreSQL RLS. */
public final class TriageReadService {
 public record Summary(UUID id,TriageCase.Stage stage,UUID station,UUID assignee,int revision,Instant arrivedAt,boolean paused,boolean safetyHold,boolean manuallyEscalated){}
 private Summary project(TriageCase c){return new Summary(c.id(),c.stage(),c.station(),c.assignee(),c.revision(),c.arrivedAt(),c.paused(),c.safetyHold(),c.escalationReason()!=null);}
 private boolean allowed(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,Instant now){
  if(c==null)return false;
  return ClinicalAccessPolicy.authorize(a,e,c.tenant(),c.facility(),c.patient(),"triage:read",now)==ClinicalAccessPolicy.Decision.ALLOWED;
 }
 private List<Summary> filter(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now,Predicate<TriageCase> p){
  if(rows==null||now==null||a==null||e==null)throw new SecurityException("triage worklist unavailable");
  return rows.stream().filter(c->allowed(a,e,c,now)).filter(p).map(this::project)
    .sorted(Comparator.comparing(Summary::arrivedAt).thenComparing(Summary::id)).toList();
 }
 public Summary caseSummary(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,Instant now){if(!allowed(a,e,c,now))throw new SecurityException("triage resource unavailable");return project(c);}
 public List<Summary> facilityQueue(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now){return filter(a,e,rows,now,c->c.stage()==TriageCase.Stage.WAITING||c.stage()==TriageCase.Stage.CALLED);}
 public List<Summary> stationQueue(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now,UUID station){if(station==null)throw new IllegalArgumentException("station required");return filter(a,e,rows,now,c->station.equals(c.station())&&(c.stage()==TriageCase.Stage.WAITING||c.stage()==TriageCase.Stage.CALLED));}
 public List<Summary> pendingHandovers(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now){return filter(a,e,rows,now,c->c.metadata().containsKey("handoverRequestedAt")&&!c.metadata().containsKey("receiverAcknowledgedAt")&&c.stage()!=TriageCase.Stage.CLOSED);}
 public List<Summary> manualEscalations(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now){return filter(a,e,rows,now,c->c.escalationReason()!=null);}
 public List<Summary> pendingReassessments(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now){return filter(a,e,rows,now,c->c.metadata().containsKey("reassessmentRequestedAt"));}
 public List<Summary> safetyHolds(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now){return filter(a,e,rows,now,TriageCase::safetyHold);}
 public List<Summary> clinicianWorklist(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now,UUID clinician){if(clinician==null)throw new IllegalArgumentException("clinician required");return filter(a,e,rows,now,c->clinician.equals(c.assignee())&&c.stage()!=TriageCase.Stage.CLOSED);}
 public List<Summary> arrivalsSince(TrustedAccessContext a,TrustedClinicalAccessEvidence e,Collection<TriageCase> rows,Instant now,Instant from){if(from==null||from.isAfter(now))throw new IllegalArgumentException("window invalid");return filter(a,e,rows,now,c->!c.arrivedAt().isBefore(from));}
 public List<TriageCase.Change> caseHistory(TrustedAccessContext a,TrustedClinicalAccessEvidence e,TriageCase c,Instant now){
  if(!allowed(a,e,c,now)||!TriageAudit.verify(c))throw new SecurityException("triage history unavailable");
  if(c.genesis()==null)return c.changes(); // Legacy synthetic aggregates have no arrival proof.
  var history=new ArrayList<TriageCase.Change>();history.add(c.genesis());history.addAll(c.changes());return List.copyOf(history);
 }
}
