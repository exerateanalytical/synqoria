package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.Instant;
import java.util.*;

/** Patient-scoped bounded projections from protected aggregate store; NOT a broad facility-wide PHI search. */
public final class TriageAuthenticatedReadGateway {
 public enum View {CASE_SUMMARY,FACILITY_QUEUE,STATION_QUEUE,PENDING_HANDOVERS,MANUAL_ESCALATIONS,PENDING_REASSESSMENTS,SAFETY_HOLDS,CLINICIAN_WORKLIST,ARRIVALS_SINCE,CASE_HISTORY}
 public record Response(List<TriageReadService.Summary> summaries,List<TriageCase.Change> history){
  public Response{summaries=List.copyOf(summaries);history=List.copyOf(history);}
 }
 private final TriagePersistencePort port;
 private final TriageReadService reader=new TriageReadService();
 public TriageAuthenticatedReadGateway(TriagePersistencePort port){this.port=Objects.requireNonNull(port);}
 public Response read(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,
  UUID patient,List<UUID> caseIds,View view,Instant trustedNow,UUID stationOrClinician,Instant after){
  if(identity==null||patient==null||view==null||trustedNow==null||caseIds==null||caseIds.isEmpty()||caseIds.size()>50||caseIds.stream().anyMatch(Objects::isNull)||new HashSet<>(caseIds).size()!=caseIds.size())throw new SecurityException("triage projection unavailable");
  TriageAuthorization.require(identity,proof,identity.tenantId(),identity.facilityId(),patient,"triage:read",trustedNow);
  return port.transaction(tx->{
   List<TriageCase> rows=new ArrayList<>();for(var caseId:caseIds){var c=tx.loadLocked(new TriagePersistencePort.Scope(identity.tenantId(),identity.facilityId(),patient,caseId));if(c!=null){if(!TriageAudit.verify(c))throw new SecurityException("triage audit unavailable");rows.add(c);}}
   if(view==View.CASE_SUMMARY||view==View.CASE_HISTORY){if(caseIds.size()!=1||rows.size()!=1)throw new SecurityException("triage resource unavailable");var c=rows.get(0);return view==View.CASE_SUMMARY?new Response(List.of(reader.caseSummary(identity,proof,c,trustedNow)),List.of()):new Response(List.of(),reader.caseHistory(identity,proof,c,trustedNow));}
   List<TriageReadService.Summary> summaries=switch(view){
    case FACILITY_QUEUE->reader.facilityQueue(identity,proof,rows,trustedNow);
    case STATION_QUEUE->reader.stationQueue(identity,proof,rows,trustedNow,stationOrClinician);
    case PENDING_HANDOVERS->reader.pendingHandovers(identity,proof,rows,trustedNow);
    case MANUAL_ESCALATIONS->reader.manualEscalations(identity,proof,rows,trustedNow);
    case PENDING_REASSESSMENTS->reader.pendingReassessments(identity,proof,rows,trustedNow);
    case SAFETY_HOLDS->reader.safetyHolds(identity,proof,rows,trustedNow);
    case CLINICIAN_WORKLIST->reader.clinicianWorklist(identity,proof,rows,trustedNow,stationOrClinician);
    case ARRIVALS_SINCE->reader.arrivalsSince(identity,proof,rows,trustedNow,after);
    default->throw new SecurityException("triage query unavailable");
   };
   return new Response(summaries,List.of());
  });
 }
}
