package org.synqoria.triage;

import java.time.Instant;
import java.util.*;

/** M03 internal aggregate. Clinical priority is NEVER calculated automatically. */
public final class TriageCase {
    public enum Stage { ARRIVED, WAITING, CALLED, ASSESSING, ASSESSED, PRIORITIZED, ASSIGNED, HANDED_OVER, CLOSED }
    public record Observation(UUID id, String code, String value, String unit, Instant takenAt, UUID recordedBy, UUID replaces) {
        public Observation {
            if(id==null||recordedBy==null||takenAt==null||!safe(code,64)||!safe(value,256)||!safe(unit,48)) throw new IllegalArgumentException("observation evidence incomplete");
            if(replaces!=null && replaces.equals(id))throw new IllegalArgumentException("self-correction");
        }
    }
    public record Change(long revision, String operation, UUID actor, UUID commandId, Instant at, String previousDigest, String stateDigest, String digest) {
        public Change { if(revision<1 || !safe(operation,80)||actor==null||commandId==null||at==null||!hex(previousDigest)||!hex(stateDigest)||!hex(digest))throw new IllegalArgumentException("invalid audit change"); }
    }
    public static boolean hex(String v){return v!=null&&v.matches("[0-9a-f]{64}");}
    public static boolean safe(String value,int max){return value!=null&&!value.isBlank()&&value.length()<=max&&value.chars().noneMatch(c->Character.isISOControl(c)&&c!='\n');}
    private final UUID id,tenant,facility,patient,appointment,station,assignee;
    private final Stage stage;
    private final int revision;
    private final String clinicianPriority, escalationReason, handoverNote;
    private final boolean paused,safetyHold,assessedSigned;
    private final Instant arrivedAt,lastReassessment;
    private final Map<UUID,Observation> observations;
    private final Map<String,String> metadata;
    private final List<Change> changes;
    /** Revision-one arrival event, persisted separately from revisions 2+. Legacy test-only aggregates may lack it. */
    private final Change genesis;
    public TriageCase(UUID id,UUID tenant,UUID facility,UUID patient,UUID appointment,UUID station,UUID assignee,Stage stage,int revision,
            String clinicianPriority,String escalationReason,String handoverNote,boolean paused,boolean safetyHold,boolean assessedSigned,
            Instant arrivedAt,Instant lastReassessment,Map<UUID,Observation> observations,Map<String,String> metadata,List<Change> changes){
        this(id,tenant,facility,patient,appointment,station,assignee,stage,revision,clinicianPriority,escalationReason,handoverNote,
            paused,safetyHold,assessedSigned,arrivedAt,lastReassessment,observations,metadata,changes,null);
    }
    public TriageCase(UUID id,UUID tenant,UUID facility,UUID patient,UUID appointment,UUID station,UUID assignee,Stage stage,int revision,
            String clinicianPriority,String escalationReason,String handoverNote,boolean paused,boolean safetyHold,boolean assessedSigned,
            Instant arrivedAt,Instant lastReassessment,Map<UUID,Observation> observations,Map<String,String> metadata,List<Change> changes,Change genesis){
        if(id==null||tenant==null||facility==null||patient==null||stage==null||revision<1||arrivedAt==null||observations==null||metadata==null||changes==null)throw new IllegalArgumentException("invalid triage aggregate");
        this.id=id;this.tenant=tenant;this.facility=facility;this.patient=patient;this.appointment=appointment;this.station=station;this.assignee=assignee;
        this.stage=stage;this.revision=revision;this.clinicianPriority=clinicianPriority;this.escalationReason=escalationReason;this.handoverNote=handoverNote;
        this.paused=paused;this.safetyHold=safetyHold;this.assessedSigned=assessedSigned;this.arrivedAt=arrivedAt;this.lastReassessment=lastReassessment;
        if(genesis!=null && (genesis.revision()!=1||!genesis.operation().equals("ARRIVAL")||!genesis.previousDigest().equals("0".repeat(64))||!genesis.at().equals(arrivedAt)))
            throw new IllegalArgumentException("invalid triage genesis evidence");
        this.observations=Map.copyOf(observations);this.metadata=Map.copyOf(metadata);this.changes=List.copyOf(changes);this.genesis=genesis;
    }
    public UUID id(){return id;} public UUID tenant(){return tenant;} public UUID facility(){return facility;} public UUID patient(){return patient;}
    public UUID appointment(){return appointment;} public UUID station(){return station;}public UUID assignee(){return assignee;}
    public Stage stage(){return stage;} public int revision(){return revision;} public String clinicianPriority(){return clinicianPriority;}
    public String escalationReason(){return escalationReason;}public String handoverNote(){return handoverNote;}
    public boolean paused(){return paused;}public boolean safetyHold(){return safetyHold;}public boolean assessedSigned(){return assessedSigned;}
    public Instant arrivedAt(){return arrivedAt;}public Instant lastReassessment(){return lastReassessment;}
    public Map<UUID,Observation> observations(){return observations;}public Map<String,String> metadata(){return metadata;}public List<Change> changes(){return changes;}
    public Change genesis(){return genesis;}
    public TriageCase withGenesis(Change entry){
        if(genesis!=null||revision!=1||!changes.isEmpty()||entry==null||entry.revision()!=1||!entry.operation().equals("ARRIVAL")||
           !entry.at().equals(arrivedAt)||!entry.previousDigest().equals("0".repeat(64))||
           !entry.stateDigest().equals(TriageAudit.stateDigest(this))||
           !entry.digest().equals(TriageAudit.eventDigest(this,entry.previousDigest(),entry.stateDigest(),entry.operation(),entry.actor(),entry.commandId(),entry.at(),1)))
            throw new SecurityException("invalid triage arrival anchor");
        return new TriageCase(id,tenant,facility,patient,appointment,station,assignee,stage,revision,clinicianPriority,escalationReason,handoverNote,
            paused,safetyHold,assessedSigned,arrivedAt,lastReassessment,observations,metadata,changes,entry);
    }
    public TriageCase next(Stage s,UUID station,UUID assignee,String priority,String escalation,String handover,
            boolean paused,boolean safetyHold,boolean signed,Instant assessedAt,Map<UUID,Observation> obs,Map<String,String> meta,Change entry){
        if(entry.revision()!=revision+1)throw new IllegalArgumentException("audit revision mismatch");
        var audit=new ArrayList<>(changes);audit.add(entry);
        return new TriageCase(id,tenant,facility,patient,appointment,station,assignee,s,revision+1,priority,escalation,handover,
            paused,safetyHold,signed,arrivedAt,assessedAt,obs,meta,audit,genesis);
    }
}
