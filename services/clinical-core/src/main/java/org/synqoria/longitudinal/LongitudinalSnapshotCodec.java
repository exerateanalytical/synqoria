package org.synqoria.longitudinal;

import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

/** Lossless, deterministic, bounded binary clinical aggregate snapshot. Never Java native serialization. */
public final class LongitudinalSnapshotCodec {
 private LongitudinalSnapshotCodec() {}
 private static final int MAGIC=0x53594e4c, VERSION=1, MAX_BYTES=4_194_304, MAX_COLLECTION=20_000;
 private static void id(DataOutputStream d,UUID v)throws IOException {d.writeBoolean(v!=null);if(v!=null){d.writeLong(v.getMostSignificantBits());d.writeLong(v.getLeastSignificantBits());}}
 private static UUID id(DataInputStream d)throws IOException {return d.readBoolean()?new UUID(d.readLong(),d.readLong()):null;}
 private static void time(DataOutputStream d,Instant v)throws IOException {d.writeBoolean(v!=null);if(v!=null){d.writeLong(v.getEpochSecond());d.writeInt(v.getNano());}}
 private static Instant time(DataInputStream d)throws IOException {if(!d.readBoolean())return null;try{return Instant.ofEpochSecond(d.readLong(),d.readInt());}catch(RuntimeException e){throw new IOException("invalid time",e);}}
 private static void string(DataOutputStream d,String v)throws IOException {if(v==null){d.writeInt(-1);return;}ByteBuffer encoded;try{encoded=StandardCharsets.UTF_8.newEncoder()
  .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
  .encode(java.nio.CharBuffer.wrap(v));}catch(CharacterCodingException e){throw new IOException("invalid Unicode clinical text",e);}
  byte[] b=new byte[encoded.remaining()];encoded.get(b);if(b.length>32768)throw new IOException("field exceeds limit");d.writeInt(b.length);d.write(b);}
 private static String string(DataInputStream d)throws IOException {int n=d.readInt();if(n==-1)return null;if(n<0||n>32768)throw new IOException("invalid field length");byte[] b=d.readNBytes(n);if(b.length!=n)throw new EOFException();try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}catch(CharacterCodingException e){throw new IOException("noncanonical text",e);}}
 private static int count(DataInputStream d)throws IOException {int c=d.readInt();if(c<0||c>MAX_COLLECTION)throw new IOException("invalid collection bound");return c;}
 private static void length(DataOutputStream d,int n)throws IOException {if(n<0||n>MAX_COLLECTION)throw new IOException("collection exceeds bound");d.writeInt(n);}
 private static <E extends Enum<E>> E enumValue(DataInputStream d,Class<E> type)throws IOException {try{return Enum.valueOf(type,Objects.requireNonNull(string(d)));}catch(RuntimeException e){throw new IOException("invalid status",e);}}
 private static void event(DataOutputStream d,Event e)throws IOException {d.writeLong(e.revision());string(d,e.action());id(d,e.actor());id(d,e.commandId());time(d,e.at());string(d,e.previousHash());string(d,e.stateDigest());string(d,e.hash());}
 private static Event event(DataInputStream d)throws IOException {return new Event(d.readLong(),string(d),id(d),id(d),time(d),string(d),string(d),string(d));}
 public static byte[] encode(State s){
  if(s==null||!auditValid(s))throw new SecurityException("clinical aggregate integrity unavailable");
  try(var bytes=new ByteArrayOutputStream();var d=new DataOutputStream(bytes)){
   d.writeInt(MAGIC);d.writeInt(VERSION);id(d,s.scope.tenant());id(d,s.scope.facility());id(d,s.scope.patient());id(d,s.scope.encounter());
   d.writeLong(s.revision);string(d,s.stage.name());time(d,s.checkedInAt);time(d,s.startedAt);time(d,s.dischargeAt);
   id(d,s.responsible);id(d,s.bed);id(d,s.bedReservation);id(d,s.transferDestination);id(d,s.admissionRequester);id(d,s.transferRequester);
   string(d,s.admissionReason);string(d,s.dischargePlan);string(d,s.dischargeAuthority);string(d,s.room);string(d,s.location);
   d.writeBoolean(s.admissionRequested);d.writeBoolean(s.admissionApproved);d.writeBoolean(s.transferRequested);d.writeBoolean(s.transferAccepted);
   d.writeBoolean(s.onLeave);d.writeBoolean(s.dischargeAuthorized);
   length(d,s.careTeam.size());for(UUID item:s.careTeam.stream().sorted().toList())id(d,item);
   length(d,s.notes.size());for(Note n:s.notes.values().stream().sorted(Comparator.comparing(a->a.id)).toList()){
    id(d,n.id);id(d,n.author);id(d,n.parent);string(d,n.status.name());string(d,n.title);string(d,n.body);string(d,n.reviewComment);
    string(d,n.errorReason);id(d,n.reviewer);id(d,n.signer);id(d,n.cosigner);time(d,n.signedAt);time(d,n.cosignedAt);
    d.writeBoolean(n.requiresCosign);d.writeBoolean(n.confidential);
    length(d,n.sections.size());for(var e:new TreeMap<>(n.sections).entrySet()){string(d,e.getKey());string(d,e.getValue());}
    length(d,n.diagnosisCodes.size());for(String x:n.diagnosisCodes.stream().sorted().toList())string(d,x);
    length(d,n.problemLabels.size());for(var e:new TreeMap<>(n.problemLabels).entrySet()){string(d,e.getKey());string(d,e.getValue());}
    length(d,n.references.size());for(String x:n.references)string(d,x);
   }
   length(d,s.observations.size());for(Observation o:s.observations.values().stream().sorted(Comparator.comparing(a->a.id)).toList()){
    id(d,o.id);id(d,o.author);id(d,o.replaces);string(d,o.code);string(d,o.unit);string(d,o.value);string(d,o.kind);
    time(d,o.measuredAt);string(d,o.status.name());string(d,o.device);string(d,o.context);string(d,o.verificationReason);
    string(d,o.invalidationReason);id(d,o.verifier);id(d,o.setId);length(d,o.metadata.size());
    for(var e:new TreeMap<>(o.metadata).entrySet()){string(d,e.getKey());string(d,e.getValue());}
   }
   length(d,s.observationSets.size());for(var e:new TreeMap<>(s.observationSets).entrySet()){id(d,e.getKey());length(d,e.getValue().size());for(UUID x:e.getValue())id(d,x);}
   length(d,s.verifiedSets.size());for(UUID x:s.verifiedSets.stream().sorted().toList())id(d,x);
   length(d,s.events.size());for(Event e:s.events)event(d,e);
   length(d,s.outbox.size());for(Event e:s.outbox)event(d,e);
   length(d,s.receipts.size());for(var e:new TreeMap<>(s.receipts).entrySet()){Receipt r=e.getValue();id(d,r.commandId());string(d,r.fingerprint());d.writeLong(r.revision());string(d,r.eventHash());}
   d.flush();byte[] encoded=bytes.toByteArray();if(encoded.length>MAX_BYTES)throw new IOException("snapshot limit");return encoded;
  }catch(IOException e){throw new IllegalArgumentException("snapshot encoding unavailable",e);}
 }
 public static State decode(byte[] bytes){
  if(bytes==null||bytes.length<8||bytes.length>MAX_BYTES)throw new SecurityException("clinical snapshot unavailable");
  try(var d=new DataInputStream(new ByteArrayInputStream(bytes))){
   if(d.readInt()!=MAGIC||d.readInt()!=VERSION)throw new IOException("snapshot format unavailable");
   Scope scope=new Scope(id(d),id(d),id(d),id(d));State s=new State(scope);
   s.revision=d.readLong();s.stage=enumValue(d,Stage.class);s.checkedInAt=time(d);s.startedAt=time(d);s.dischargeAt=time(d);
   s.responsible=id(d);s.bed=id(d);s.bedReservation=id(d);s.transferDestination=id(d);s.admissionRequester=id(d);s.transferRequester=id(d);
   s.admissionReason=string(d);s.dischargePlan=string(d);s.dischargeAuthority=string(d);s.room=string(d);s.location=string(d);
   s.admissionRequested=d.readBoolean();s.admissionApproved=d.readBoolean();s.transferRequested=d.readBoolean();s.transferAccepted=d.readBoolean();
   s.onLeave=d.readBoolean();s.dischargeAuthorized=d.readBoolean();
   for(int i=count(d);i>0;i--){UUID x=Objects.requireNonNull(id(d));if(!s.careTeam.add(x))throw new IOException("duplicate team");}
   for(int i=count(d);i>0;i--){UUID k=Objects.requireNonNull(id(d)),author=Objects.requireNonNull(id(d));Note n=new Note(k,author,id(d));
    n.status=enumValue(d,Note.Status.class);n.title=string(d);n.body=string(d);n.reviewComment=string(d);n.errorReason=string(d);
    n.reviewer=id(d);n.signer=id(d);n.cosigner=id(d);n.signedAt=time(d);n.cosignedAt=time(d);n.requiresCosign=d.readBoolean();n.confidential=d.readBoolean();
    for(int j=count(d);j>0;j--){String x=Objects.requireNonNull(string(d)),v=Objects.requireNonNull(string(d));if(n.sections.putIfAbsent(x,v)!=null)throw new IOException("duplicate section");}
    for(int j=count(d);j>0;j--){if(!n.diagnosisCodes.add(Objects.requireNonNull(string(d))))throw new IOException("duplicate diagnosis");}
    for(int j=count(d);j>0;j--){String x=Objects.requireNonNull(string(d)),v=Objects.requireNonNull(string(d));if(n.problemLabels.putIfAbsent(x,v)!=null)throw new IOException("duplicate problem");}
    for(int j=count(d);j>0;j--)n.references.add(Objects.requireNonNull(string(d)));
    if(s.notes.putIfAbsent(k,n)!=null)throw new IOException("duplicate note");
   }
   for(int i=count(d);i>0;i--){UUID k=Objects.requireNonNull(id(d)),author=Objects.requireNonNull(id(d)),replaces=id(d);
    String code=Objects.requireNonNull(string(d)),unit=Objects.requireNonNull(string(d)),value=Objects.requireNonNull(string(d)),kind=Objects.requireNonNull(string(d));
    Observation o=new Observation(k,author,replaces,code,unit,value,kind,Objects.requireNonNull(time(d)));
    o.status=enumValue(d,Observation.Status.class);o.device=string(d);o.context=string(d);o.verificationReason=string(d);
    o.invalidationReason=string(d);o.verifier=id(d);o.setId=id(d);
    for(int j=count(d);j>0;j--){String x=Objects.requireNonNull(string(d)),v=Objects.requireNonNull(string(d));if(o.metadata.putIfAbsent(x,v)!=null)throw new IOException("duplicate metadata");}
    if(s.observations.putIfAbsent(k,o)!=null)throw new IOException("duplicate observation");
   }
   for(int i=count(d);i>0;i--){UUID k=Objects.requireNonNull(id(d));List<UUID> group=new ArrayList<>();for(int j=count(d);j>0;j--)group.add(Objects.requireNonNull(id(d)));if(s.observationSets.putIfAbsent(k,group)!=null)throw new IOException("duplicate observation set");}
   for(int i=count(d);i>0;i--){if(!s.verifiedSets.add(Objects.requireNonNull(id(d))))throw new IOException("duplicate completed set");}
   for(int i=count(d);i>0;i--)s.events.add(event(d));
   for(int i=count(d);i>0;i--)s.outbox.add(event(d));
   for(int i=count(d);i>0;i--){UUID k=Objects.requireNonNull(id(d));Receipt r=new Receipt(k,string(d),d.readLong(),string(d));if(s.receipts.putIfAbsent(k,r)!=null)throw new IOException("duplicate receipt");}
   if(d.read()!=-1||!auditValid(s))throw new IOException("snapshot evidence integrity mismatch");
   return s;
  }catch(IOException|RuntimeException e){throw new SecurityException("clinical snapshot integrity unavailable",e);}
 }
}
