package org.synqoria.triage;

import java.io.*;
import java.time.Instant;
import java.util.*;

/** Deterministic, versioned, bounded binary aggregate encoding. Never Java object serialization. */
public final class TriageSnapshotCodec {
    private static final int MAGIC = 0x54524732, VERSION = 2, MAX_BYTES = 4_194_304;
    private TriageSnapshotCodec() {}
    private static void uuid(DataOutputStream o, UUID x) throws IOException {o.writeBoolean(x!=null);if(x!=null){o.writeLong(x.getMostSignificantBits());o.writeLong(x.getLeastSignificantBits());}}
    private static UUID uuid(DataInputStream i) throws IOException {return i.readBoolean()?new UUID(i.readLong(),i.readLong()):null;}
    private static void time(DataOutputStream o,Instant v)throws IOException{o.writeBoolean(v!=null);if(v!=null){o.writeLong(v.getEpochSecond());o.writeInt(v.getNano());}}
    private static Instant time(DataInputStream i)throws IOException {if(!i.readBoolean())return null;return Instant.ofEpochSecond(i.readLong(),i.readInt());}
    private static void string(DataOutputStream o,String s)throws IOException {o.writeBoolean(s!=null);if(s!=null){byte[] b=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(b.length>32768)throw new IOException("string limit");o.writeInt(b.length);o.write(b);}}
    private static String string(DataInputStream i)throws IOException {if(!i.readBoolean())return null;int n=i.readInt();if(n<0||n>32768)throw new IOException("invalid string length");byte[] b=i.readNBytes(n);if(b.length!=n)throw new EOFException();return java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString();}
    private static int count(DataInputStream i,int max)throws IOException{int n=i.readInt();if(n<0||n>max)throw new IOException("invalid aggregate count");return n;}
    public static byte[] encode(TriageCase c) {
        if(c==null||!TriageAudit.verify(c))throw new SecurityException("triage aggregate integrity invalid");
        try(var bytes=new ByteArrayOutputStream();var o=new DataOutputStream(bytes)) {
            o.writeInt(MAGIC);o.writeInt(VERSION);
            uuid(o,c.id());uuid(o,c.tenant());uuid(o,c.facility());uuid(o,c.patient());uuid(o,c.appointment());uuid(o,c.station());uuid(o,c.assignee());
            string(o,c.stage().name());o.writeInt(c.revision());string(o,c.clinicianPriority());string(o,c.escalationReason());string(o,c.handoverNote());
            o.writeBoolean(c.paused());o.writeBoolean(c.safetyHold());o.writeBoolean(c.assessedSigned());time(o,c.arrivedAt());time(o,c.lastReassessment());
            var obs=c.observations().values().stream().sorted(Comparator.comparing(TriageCase.Observation::id)).toList();
            if(obs.size()>10000||c.metadata().size()>2000||c.changes().size()>30000)throw new IOException("aggregate record limit");
            o.writeInt(obs.size());for(var v:obs){uuid(o,v.id());string(o,v.code());string(o,v.value());string(o,v.unit());time(o,v.takenAt());uuid(o,v.recordedBy());uuid(o,v.replaces());}
            var meta=new TreeMap<>(c.metadata());o.writeInt(meta.size());for(var e:meta.entrySet()){string(o,e.getKey());string(o,e.getValue());}
            o.writeInt(c.changes().size());for(var e:c.changes()){writeChange(o,e);}
            o.writeBoolean(c.genesis()!=null);if(c.genesis()!=null)writeChange(o,c.genesis());
            o.flush();byte[] out=bytes.toByteArray();if(out.length>MAX_BYTES)throw new IOException("snapshot too large");return out;
        }catch(IOException ex){throw new IllegalArgumentException("triage snapshot unavailable",ex);}
    }
    private static void writeChange(DataOutputStream o,TriageCase.Change e)throws IOException{
        o.writeLong(e.revision());string(o,e.operation());uuid(o,e.actor());uuid(o,e.commandId());time(o,e.at());string(o,e.previousDigest());string(o,e.stateDigest());string(o,e.digest());
    }
    private static TriageCase.Change readChange(DataInputStream i)throws IOException{
        return new TriageCase.Change(i.readLong(),string(i),uuid(i),uuid(i),time(i),string(i),string(i),string(i));
    }
    public static TriageCase decode(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new SecurityException("triage snapshot unavailable");
        try(var i=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(i.readInt()!=MAGIC)throw new IOException("invalid snapshot magic");
            int version=i.readInt();if(version!=1&&version!=VERSION)throw new IOException("unsupported snapshot version");
            UUID id=uuid(i),tenant=uuid(i),facility=uuid(i),patient=uuid(i),appointment=uuid(i),station=uuid(i),assignee=uuid(i);
            TriageCase.Stage stage=TriageCase.Stage.valueOf(string(i));int rev=i.readInt();String priority=string(i),escalation=string(i),handover=string(i);
            boolean paused=i.readBoolean(),held=i.readBoolean(),signed=i.readBoolean();Instant arrival=time(i), reassessment=time(i);
            Map<UUID,TriageCase.Observation> observations=new HashMap<>();for(int j=count(i,10000);j>0;j--){var o=new TriageCase.Observation(uuid(i),string(i),string(i),string(i),time(i),uuid(i),uuid(i));if(observations.putIfAbsent(o.id(),o)!=null)throw new IOException("duplicate observation");}
            Map<String,String> meta=new HashMap<>();for(int j=count(i,2000);j>0;j--){String k=string(i),v=string(i);if(k==null||v==null||meta.putIfAbsent(k,v)!=null)throw new IOException("duplicate metadata");}
            List<TriageCase.Change> changes=new ArrayList<>();for(int j=count(i,30000);j>0;j--){changes.add(readChange(i));}
            TriageCase.Change genesis=null;
            if(version==2 && i.readBoolean())genesis=readChange(i);
            if(i.read()!=-1)throw new IOException("snapshot trailing bytes");
            var c=new TriageCase(id,tenant,facility,patient,appointment,station,assignee,stage,rev,priority,escalation,handover,paused,held,signed,arrival,reassessment,observations,meta,changes,genesis);
            if(!TriageAudit.verify(c))throw new SecurityException("triage audit integrity invalid");return c;
        }catch(IOException|RuntimeException ex){throw new SecurityException("triage snapshot invalid",ex);}
    }
}
