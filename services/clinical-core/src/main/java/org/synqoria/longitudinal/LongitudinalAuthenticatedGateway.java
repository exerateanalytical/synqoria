package org.synqoria.longitudinal;

import org.synqoria.security.ClinicalAccessPolicy;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.time.Instant;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** M04-M06 application boundary. Caller identity/proof are trusted server-side dependencies only.
 * All data is read via the caller's scoped transactional adapter, never caller-supplied row IDs.
 * This class deliberately does not create a production database, OIDC verifier, or clinical policy.
 */
public final class LongitudinalAuthenticatedGateway {
  public record CaseSummary(UUID encounterId,long revision,Stage stage,boolean onLeave,
                            boolean admissionRequested,boolean dischargeAuthorized,int notes,int observations,String auditHead) {}
  public record AuditPage(List<AuditRow> rows,Long nextRevision) {}
  public record AuditRow(long revision,String operation,Instant at,String digest) {}
  public record NoteView(UUID id,Note.Status status,UUID author,String title,String body,boolean confidential,UUID parent) {}
  public record ObservationView(UUID id,String code,String value,String unit,Instant measuredAt,Observation.Status status,UUID replaces) {}
  public record NoteIndexRow(UUID id,Note.Status status,String title,boolean confidential) {}
  public record NoteIndexPage(List<NoteIndexRow> rows,UUID nextId) {}
  private final Persistence persistence;
  private final ClinicalCaseCore core;
  public LongitudinalAuthenticatedGateway(Persistence persistence){
    this.persistence=Objects.requireNonNull(persistence);this.core=new ClinicalCaseCore(persistence);
  }
  private Actor authorize(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,Scope scope,
                          String capability,Instant now){
    if(identity==null||proof==null||scope==null||now==null||
       ClinicalAccessPolicy.authorize(identity,proof,scope.tenant(),scope.facility(),scope.patient(),capability,now)
         !=ClinicalAccessPolicy.Decision.ALLOWED)throw new SecurityException("clinical resource unavailable");
    // Additional action-specific grants must be authorized in BOTH current session and signed-off policy evidence.
    var intersection=new HashSet<>(identity.capabilities());intersection.retainAll(proof.authorizedOperations());
    return new Actor(identity.actorId(),intersection);
  }
  private static String capability(String op){
    if(EncounterActions.OPERATIONS.contains(op))return "encounter.write";
    if(NoteActions.OPERATIONS.contains(op))return "note.write";
    if(ObservationActions.OPERATIONS.contains(op) &&
       !Set.of("observation.timeline","observation.correction_lineage").contains(op))return "observation.write";
    throw new IllegalArgumentException("unsupported clinical action");
  }
  public Result command(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,Scope scope,Command command,
                        String operation,Map<String,String> inputs,Instant serverNow){
    Objects.requireNonNull(command);Objects.requireNonNull(inputs);
    String cap=capability(operation);
    Actor actor=authorize(identity,proof,scope,cap,serverNow);
    // The action timestamp must be supplied by the trusted server clock, not from a client request.
    if(!serverNow.equals(command.at()))throw new SecurityException("untrusted command timestamp");
    if(operation.startsWith("encounter."))return EncounterActions.run(core,scope,actor,command,operation,inputs);
    if(operation.startsWith("note."))return NoteActions.run(core,scope,actor,command,operation,inputs);
    return ObservationActions.run(core,scope,actor,command,operation,inputs);
  }
  private State read(TrustedAccessContext identity,TrustedClinicalAccessEvidence proof,Scope scope,String cap,Instant now){
    authorize(identity,proof,scope,cap,now);
    return persistence.transaction(scope,tx->{
      State s=tx.loadLocked();
      if(s==null||!scope.equals(s.scope)||!ClinicalCaseCore.auditValid(s))
        throw new SecurityException("clinical resource unavailable");
      return new State(s);
    });
  }
  public CaseSummary summary(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,Scope scope,Instant now){
    State s=read(caller,proof,scope,"encounter.read",now);
    return new CaseSummary(scope.encounter(),s.revision,s.stage,s.onLeave,s.admissionRequested,s.dischargeAuthorized,
      s.notes.size(),s.observations.size(),s.events.isEmpty()?"0".repeat(64):s.events.get(s.events.size()-1).hash());
  }
  public AuditPage history(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,Scope scope,
                           long afterRevision,int limit,Instant now){
    if(afterRevision<0||limit<1||limit>100)throw new IllegalArgumentException("invalid history page");
    State s=read(caller,proof,scope,"encounter.audit.read",now);
    List<Event> matches=s.events.stream().filter(e->e.revision()>afterRevision).limit(limit+1L).toList();
    boolean more=matches.size()>limit;
    var shown=more?matches.subList(0,limit):matches;
    return new AuditPage(shown.stream().map(e->new AuditRow(e.revision(),e.action(),e.at(),e.hash())).toList(),
      more?shown.get(shown.size()-1).revision():null);
  }
  public NoteView note(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,Scope scope,UUID noteId,Instant now){
    Objects.requireNonNull(noteId);State s=read(caller,proof,scope,"note.read",now);
    Note n=ClinicalCaseCore.note(s,noteId);
    if(n.confidential)authorize(caller,proof,scope,"note.read.confidential",now);
    return new NoteView(n.id,n.status,n.author,n.title,n.body,n.confidential,n.parent);
  }
  /** Bounded note metadata listing; confidential notes must be omitted without explicit privilege. */
  public NoteIndexPage noteIndex(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,Scope scope,
                                 UUID afterId,int limit,Instant now){
    if(limit<1||limit>100)throw new IllegalArgumentException("invalid page");
    State s=read(caller,proof,scope,"note.read",now);
    boolean confidentialAllowed=ClinicalAccessPolicy.authorize(caller,proof,scope.tenant(),scope.facility(),
       scope.patient(),"note.read.confidential",now)==ClinicalAccessPolicy.Decision.ALLOWED;
    List<Note> candidates=s.notes.values().stream().filter(n->!n.confidential||confidentialAllowed)
      .filter(n->afterId==null||n.id.compareTo(afterId)>0).sorted(Comparator.comparing(n->n.id))
      .limit(limit+1L).toList();boolean more=candidates.size()>limit;
    List<Note> shown=more?candidates.subList(0,limit):candidates;
    return new NoteIndexPage(shown.stream().map(n->new NoteIndexRow(n.id,n.status,n.title,n.confidential)).toList(),
      more?shown.get(shown.size()-1).id:null);
  }
  /** Patient-scoped correction history, including ancestors and descendants. No automatic interpretations. */
  public List<ObservationView> observationLineage(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,
                                                   Scope scope,UUID selected,Instant now){
    Objects.requireNonNull(selected);State s=read(caller,proof,scope,"observation.read",now);
    Actor a=authorize(caller,proof,scope,"observation.read",now);
    return ObservationActions.correctionLineage(s,scope,a,selected).stream().map(o->
      new ObservationView(o.id,o.code,o.value,o.unit,o.measuredAt,o.status,o.replaces)).toList();
  }
  public List<ObservationView> observationTimeline(TrustedAccessContext caller,TrustedClinicalAccessEvidence proof,Scope scope,
                                                   String code,int limit,Instant now){
    State s=read(caller,proof,scope,"observation.read",now);
    Actor actor=authorize(caller,proof,scope,"observation.read",now);
    return ObservationActions.timeline(s,scope,actor,code,limit).stream().map(o->
      new ObservationView(o.id,o.code,o.value,o.unit,o.measuredAt,o.status,o.replaces)).toList();
  }
}
