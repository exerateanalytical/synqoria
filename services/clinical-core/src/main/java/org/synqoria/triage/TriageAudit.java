package org.synqoria.triage;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
/** Deterministic hash-chain proof for domain mutations. Protected database evidence still required. */
public final class TriageAudit {
 private TriageAudit(){}
 static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 static String stateDigest(TriageCase c){
  var values=new StringBuilder("synqoria-triage-state-v1");
  for(Object part:List.of(c.id(),c.tenant(),c.facility(),c.patient(),c.stage(),c.revision(),c.arrivedAt(),c.paused(),c.safetyHold(),c.assessedSigned())){append(values,part);}
  append(values,c.appointment());append(values,c.station());append(values,c.assignee());append(values,c.clinicianPriority());append(values,c.escalationReason());append(values,c.handoverNote());append(values,c.lastReassessment());
  c.metadata().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{append(values,e.getKey());append(values,e.getValue());});
  c.observations().values().stream().sorted(Comparator.comparing(TriageCase.Observation::id)).forEach(o->{append(values,o.id());append(values,o.code());append(values,o.value());append(values,o.unit());append(values,o.takenAt());append(values,o.recordedBy());append(values,o.replaces());});
  return hash(values.toString());
 }
 private static void append(StringBuilder b,Object v){String s=String.valueOf(v);b.append(s.length()).append(':').append(s).append(';');}
 static String eventDigest(TriageCase c,String previous,String state,String op,UUID actor,UUID commandId,Instant now,long revision){
  var b=new StringBuilder("synqoria-triage-chain-v1");
  for(Object v:List.of(c.id(),c.tenant(),c.facility(),c.patient(),revision,previous,state,op,actor,commandId,now))append(b,v);
  return hash(b.toString());
 }
 public static TriageCase.Change next(TriageCase original,TriageCase candidate,String operation,TriageCommand command){
   if(original==null||candidate==null||command==null||!TriageCase.safe(operation,80)||candidate.revision()!=original.revision()+1)throw new IllegalArgumentException("audit input invalid");
   String previous=original.changes().isEmpty()?(original.genesis()==null?"0".repeat(64):original.genesis().digest()):original.changes().get(original.changes().size()-1).digest();
   String state=stateDigest(candidate);
   return new TriageCase.Change(candidate.revision(),operation,command.actor(),command.commandId(),command.trustedNow(),previous,state,
      eventDigest(candidate,previous,state,operation,command.actor(),command.commandId(),command.trustedNow(),candidate.revision()));
 }
 public static boolean verify(TriageCase c){
   if(c==null||c.changes().size()!=c.revision()-1)return false;
   String previous="0".repeat(64);int sequence=2; Set<UUID> commands=new HashSet<>();
   if(c.genesis()!=null){
     var genesis=c.genesis();
     if(!genesis.previousDigest().equals(previous)||genesis.revision()!=1||!genesis.operation().equals("ARRIVAL")||!genesis.at().equals(c.arrivedAt())||
        !genesis.digest().equals(eventDigest(c,previous,genesis.stateDigest(),"ARRIVAL",genesis.actor(),genesis.commandId(),genesis.at(),1)))return false;
     if(c.revision()==1&&!genesis.stateDigest().equals(stateDigest(c)))return false;
     previous=genesis.digest();commands.add(genesis.commandId());
   }
   for(var ch:c.changes()){
     if(ch.revision()!=sequence++ || !ch.previousDigest().equals(previous) || !commands.add(ch.commandId()))return false;
     if(!ch.digest().equals(eventDigest(c,previous,ch.stateDigest(),ch.operation(),ch.actor(),ch.commandId(),ch.at(),ch.revision())))return false;
     previous=ch.digest();
   }
   return c.changes().isEmpty()||c.changes().get(c.changes().size()-1).stateDigest().equals(stateDigest(c));
 }
}
