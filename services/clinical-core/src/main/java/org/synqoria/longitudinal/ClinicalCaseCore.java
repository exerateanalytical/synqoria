package org.synqoria.longitudinal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * M04-M06 deterministic clinical case domain. This is a pure domain boundary. A caller MUST use a
 * server-verified actor context and a transactional implementation of Persistence in production;
 * no HTTP payload is a trusted identity. No medical decisions are computed by this class.
 */
public final class ClinicalCaseCore {
    public record Scope(UUID tenant, UUID facility, UUID patient, UUID encounter) {
        public Scope { Objects.requireNonNull(tenant); Objects.requireNonNull(facility);
            Objects.requireNonNull(patient); Objects.requireNonNull(encounter); }
    }
    public record Actor(UUID id, Set<String> capabilities) {
        public Actor { Objects.requireNonNull(id); capabilities=Set.copyOf(capabilities); }
        public void require(String capability) { if (!capabilities.contains(capability))
            throw new SecurityException("clinical resource unavailable"); }
    }
    public record Command(UUID id, long expectedRevision, Instant at) {
        public Command { Objects.requireNonNull(id); Objects.requireNonNull(at);
            if(expectedRevision < 0) throw new IllegalArgumentException("revision"); }
    }
    public record Event(long revision, String action, UUID actor, UUID commandId, Instant at,
                        String previousHash, String stateDigest, String hash) {}
    public record Receipt(UUID commandId, String fingerprint, long revision, String eventHash) {}
    public record Result(long revision, String auditHash, boolean replay) {}
    public enum Stage { PLANNED, ACTIVE, DISCHARGE_PLANNED, DISCHARGED }

    public static String text(String raw,int max) {
        if(raw == null || raw.isBlank() || raw.length()>max || raw.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))
            throw new IllegalArgumentException("invalid clinical text");
        return raw.trim();
    }
    public static UUID uuid(String raw) { try { return UUID.fromString(text(raw,40)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("invalid identifier"); } }
    private static String hash(String input) {
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static final class Note {
        public enum Status { DRAFT, REVIEW, READY, SIGNED, AMENDED, ENTERED_IN_ERROR }
        public final UUID id, author, parent;
        public Status status=Status.DRAFT;
        public String title="Untitled draft", body="", reviewComment="", errorReason="";
        public final Map<String,String> sections=new LinkedHashMap<>();
        public final Set<String> diagnosisCodes=new LinkedHashSet<>();
        public final Map<String,String> problemLabels=new LinkedHashMap<>();
        public final List<String> references=new ArrayList<>();
        public UUID reviewer, signer, cosigner;
        public Instant signedAt, cosignedAt;
        public boolean requiresCosign=false, confidential=false;
        public Note(UUID id,UUID author,UUID parent){this.id=id;this.author=author;this.parent=parent;}
        public Note(Note n) {this(n.id,n.author,n.parent);status=n.status;title=n.title;body=n.body;
            reviewComment=n.reviewComment;errorReason=n.errorReason;sections.putAll(n.sections);
            diagnosisCodes.addAll(n.diagnosisCodes);problemLabels.putAll(n.problemLabels);
            references.addAll(n.references);reviewer=n.reviewer;signer=n.signer;cosigner=n.cosigner;
            signedAt=n.signedAt;cosignedAt=n.cosignedAt;requiresCosign=n.requiresCosign;
            confidential=n.confidential;}
        public boolean isImmutable(){return status==Status.SIGNED||status==Status.AMENDED||status==Status.ENTERED_IN_ERROR;}
    }
    public static final class Observation {
        public enum Status { CAPTURED, PENDING, VERIFIED, CORRECTED, INVALIDATED, REJECTED }
        public final UUID id, author, replaces;
        public final String code, unit, value, kind;
        public final Instant measuredAt;
        public Status status=Status.CAPTURED;
        public String device="",context="", verificationReason="", invalidationReason="";
        public UUID verifier, setId;
        public final Map<String,String> metadata=new LinkedHashMap<>();
        public Observation(UUID id,UUID author,UUID replaces,String code,String unit,String value,String kind,Instant measuredAt){
            this.id=id;this.author=author;this.replaces=replaces;this.code=code;this.unit=unit;
            this.value=value;this.kind=kind;this.measuredAt=measuredAt;}
        public Observation(Observation o){this(o.id,o.author,o.replaces,o.code,o.unit,o.value,o.kind,o.measuredAt);
            status=o.status;device=o.device;context=o.context;verificationReason=o.verificationReason;
            invalidationReason=o.invalidationReason;verifier=o.verifier;setId=o.setId;metadata.putAll(o.metadata);}
    }
    public static final class State {
        public final Scope scope;
        public long revision=0;
        public Stage stage=Stage.PLANNED;
        public Instant checkedInAt,startedAt,dischargeAt;
        public UUID responsible,bed,bedReservation,transferDestination,admissionRequester,transferRequester;
        public String admissionReason="", dischargePlan="", dischargeAuthority="", room="", location="";
        public boolean admissionRequested=false,admissionApproved=false,transferRequested=false,transferAccepted=false;
        public boolean onLeave=false,dischargeAuthorized=false;
        public final Set<UUID> careTeam=new LinkedHashSet<>();
        public final Map<UUID,Note> notes=new LinkedHashMap<>();
        public final Map<UUID,Observation> observations=new LinkedHashMap<>();
        public final Map<UUID,List<UUID>> observationSets=new LinkedHashMap<>();
        public final Set<UUID> verifiedSets=new HashSet<>();
        public final List<Event> events=new ArrayList<>();
        public final List<Event> outbox=new ArrayList<>();
        public final Map<UUID,Receipt> receipts=new HashMap<>();
        public State(Scope scope){this.scope=Objects.requireNonNull(scope);}
        public State(State s){this(s.scope);revision=s.revision;stage=s.stage;checkedInAt=s.checkedInAt;
            startedAt=s.startedAt;dischargeAt=s.dischargeAt;responsible=s.responsible;bed=s.bed;
            bedReservation=s.bedReservation;transferDestination=s.transferDestination;
            admissionRequester=s.admissionRequester;transferRequester=s.transferRequester;
            admissionReason=s.admissionReason;dischargePlan=s.dischargePlan;dischargeAuthority=s.dischargeAuthority;
            room=s.room;location=s.location;admissionRequested=s.admissionRequested;
            admissionApproved=s.admissionApproved;transferRequested=s.transferRequested;
            transferAccepted=s.transferAccepted;onLeave=s.onLeave;dischargeAuthorized=s.dischargeAuthorized;
            careTeam.addAll(s.careTeam);s.notes.forEach((k,v)->notes.put(k,new Note(v)));
            s.observations.forEach((k,v)->observations.put(k,new Observation(v)));
            s.observationSets.forEach((k,v)->observationSets.put(k,new ArrayList<>(v)));
            verifiedSets.addAll(s.verifiedSets);events.addAll(s.events);outbox.addAll(s.outbox);
            receipts.putAll(s.receipts);}
    }
    /** Caller must provide atomic, scope-locked storage in production (row locks + RLS + outbox). */
    public interface Persistence {
        /** The adapter must start a scoped DB transaction, lock the row, verify RLS and commit all writes together. */
        <T> T transaction(Scope scope, Function<Tx,T> action);
        interface Tx {
            State loadLocked();
            /** Save aggregate, linked audit, outbox and idempotency receipt in the SAME database transaction. */
            void saveAtomically(State next,Event event,Receipt receipt);
        }
    }
    private final Persistence persistence;
    public ClinicalCaseCore(Persistence persistence){this.persistence=Objects.requireNonNull(persistence);}
    public Result execute(Scope scope,Actor actor,Command command,String capability,
                          String action,String canonicalPayload,Consumer<State> mutation){
        Objects.requireNonNull(scope);Objects.requireNonNull(actor);Objects.requireNonNull(command);
        Objects.requireNonNull(mutation);actor.require(capability);
        text(action,96);if(canonicalPayload==null||canonicalPayload.length()>32768)throw new IllegalArgumentException("payload");
        return persistence.transaction(scope,tx->{
        State source=tx.loadLocked();
        if(source==null||!scope.equals(source.scope))throw new SecurityException("clinical resource unavailable");
        if(!auditValid(source))throw new SecurityException("invalid audit chain");
        String fingerprint=hash("clinical-v1|"+scope.tenant()+"|"+scope.facility()+"|"+scope.patient()+"|"+
            scope.encounter()+"|"+actor.id()+"|"+command.expectedRevision()+"|"+action+"|"+canonicalPayload.length()+":"+canonicalPayload);
        Receipt previous=source.receipts.get(command.id());
        if(previous!=null){
            if(!previous.fingerprint().equals(fingerprint)||previous.revision()>source.revision||
               source.events.stream().noneMatch(e->e.revision()==previous.revision()&&e.hash().equals(previous.eventHash())))
                throw new SecurityException("idempotency evidence invalid");
            return new Result(previous.revision(),previous.eventHash(),true);
        }
        if(source.revision!=command.expectedRevision())throw new IllegalStateException("stale revision");
        if(source.stage==Stage.DISCHARGED && !action.startsWith("note.") && !action.startsWith("observation."))
            throw new IllegalStateException("closed encounter");
        State next=new State(source);mutation.accept(next);
        if(!scope.equals(next.scope)||next.revision!=source.revision||next.events.size()!=source.events.size()||
           next.outbox.size()!=source.outbox.size()||next.receipts.size()!=source.receipts.size())
            throw new SecurityException("unauthorized aggregate mutation");
        long revision=source.revision+1;
        String prior=source.events.isEmpty()?"0".repeat(64):source.events.get(source.events.size()-1).hash();
        String stateDigest=stateDigest(next);
        String digest=hash("clinical-event-v1|"+scope.tenant()+"|"+scope.facility()+"|"+scope.patient()+"|"+
            scope.encounter()+"|"+revision+"|"+prior+"|"+actor.id()+"|"+command.id()+"|"+
            command.at()+"|"+action+"|"+fingerprint+"|"+stateDigest);
        Event event=new Event(revision,action,actor.id(),command.id(),command.at(),prior,stateDigest,digest);
        Receipt receipt=new Receipt(command.id(),fingerprint,revision,digest);
        next.revision=revision;next.events.add(event);next.outbox.add(event);next.receipts.put(command.id(),receipt);
        tx.saveAtomically(next,event,receipt);
        return new Result(revision,digest,false);
        });
    }
    public static boolean auditValid(State state){
        if(state.events.size()!=state.revision||state.outbox.size()!=state.revision||state.receipts.size()!=state.revision)return false;
        String prev="0".repeat(64);long i=0;
        for(Event event:state.events){i++;Receipt receipt=state.receipts.get(event.commandId());
            if(event.revision()!=i||!event.previousHash().equals(prev)||!state.outbox.contains(event)||
              receipt==null||receipt.revision()!=i||!receipt.eventHash().equals(event.hash())||
              !event.hash().equals(hash("clinical-event-v1|"+state.scope.tenant()+"|"+state.scope.facility()+"|"+
                 state.scope.patient()+"|"+state.scope.encounter()+"|"+i+"|"+prev+"|"+
                 event.actor()+"|"+event.commandId()+"|"+event.at()+"|"+event.action()+"|"+
                 receipt.fingerprint()+"|"+event.stateDigest())))return false;
            prev=event.hash();}
        return state.events.isEmpty() || state.events.get(state.events.size()-1).stateDigest().equals(stateDigest(state));
    }
    /** Length-prefixed deterministic serialization prevents field-delimiter collisions in audit state digests. */
    private static void encode(StringBuilder b,Object... values){
        for(Object raw:values){if(raw==null){b.append("-1:");continue;}
            String value=raw.toString();b.append(value.length()).append(':').append(value);}
    }
    private static String stateDigest(State s){
        StringBuilder b=new StringBuilder();
        encode(b,"clinical-state-v2",s.scope.tenant(),s.scope.facility(),s.scope.patient(),s.scope.encounter(),
          s.stage,s.checkedInAt,s.startedAt,s.dischargeAt,s.responsible,s.bed,s.bedReservation,
          s.transferDestination,s.admissionRequester,s.transferRequester,s.admissionReason,s.dischargePlan,
          s.dischargeAuthority,s.room,s.location,s.admissionRequested,s.admissionApproved,
          s.transferRequested,s.transferAccepted,s.onLeave,s.dischargeAuthorized);
        s.careTeam.stream().sorted().forEach(x->encode(b,"team",x));
        s.notes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{Note n=e.getValue();
            encode(b,"note",n.id,n.author,n.parent,n.status,n.title,n.body,n.reviewComment,n.errorReason,
              n.reviewer,n.signer,n.cosigner,n.signedAt,n.cosignedAt,n.requiresCosign,n.confidential);
            new TreeMap<>(n.sections).forEach((k,v)->encode(b,"section",k,v));
            n.diagnosisCodes.stream().sorted().forEach(x->encode(b,"diagnosis",x));
            new TreeMap<>(n.problemLabels).forEach((k,v)->encode(b,"problem",k,v));
            n.references.forEach(x->encode(b,"reference",x));
        });
        s.observations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{Observation o=e.getValue();
            encode(b,"observation",o.id,o.author,o.replaces,o.code,o.unit,o.value,o.kind,o.measuredAt,
               o.status,o.device,o.context,o.verificationReason,o.invalidationReason,o.verifier,o.setId);
            new TreeMap<>(o.metadata).forEach((k,v)->encode(b,"metadata",k,v));
        });
        s.observationSets.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            encode(b,"set",e.getKey());e.getValue().forEach(id->encode(b,"member",id));
        });
        s.verifiedSets.stream().sorted().forEach(x->encode(b,"verifiedset",x));
        return hash(b.toString());
    }
    public static Note note(State s,UUID id) {Note n=s.notes.get(id);
        if(n==null)throw new SecurityException("clinical resource unavailable");return n;}
    public static Observation observation(State s,UUID id) {Observation o=s.observations.get(id);
        if(o==null)throw new SecurityException("clinical resource unavailable");return o;}
}
