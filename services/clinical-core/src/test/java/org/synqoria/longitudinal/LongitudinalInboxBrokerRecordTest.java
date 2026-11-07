package org.synqoria.longitudinal;
import java.time.Instant;import java.util.*;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
public class LongitudinalInboxBrokerRecordTest {
 public static void main(String[] args){Scope s=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
 Envelope e=new Envelope(s,UUID.randomUUID(),UUID.randomUUID(),1,"a".repeat(64),"note.sign",Instant.now());
 var r=LongitudinalInboxBrokerRecord.serialize("synqoria.clinical.events.v1",e);
 if(!LongitudinalInboxBrokerRecord.verify(r).equals(e))throw new AssertionError("round-trip");
 byte[] b=r.body();b[0]='X'; if(!LongitudinalInboxBrokerRecord.verify(r).equals(e))throw new AssertionError("mutable alias");
 var evil=new LongitudinalInboxBrokerRecord.Record(r.topic(),"b".repeat(64),r.headers(),r.body());
 try{LongitudinalInboxBrokerRecord.verify(evil);throw new AssertionError("partition spoof");}catch(SecurityException expected){}
 var forged=new LongitudinalInboxBrokerRecord.Record(r.topic(),r.partitionKey(),Map.of("schema","synqoria.inbox.v1","event-sha256","0".repeat(64)),r.body());
 try{LongitudinalInboxBrokerRecord.verify(forged);throw new AssertionError("digest spoof");}catch(SecurityException expected){}
 try{LongitudinalInboxBrokerRecord.serialize("other.topic",e);throw new AssertionError("untrusted topic");}catch(SecurityException expected){}
 System.out.println("PASS B0120 broker binding 5 checks");}
}
