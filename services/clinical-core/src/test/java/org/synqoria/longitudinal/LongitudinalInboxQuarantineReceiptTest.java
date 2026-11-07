package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
public class LongitudinalInboxQuarantineReceiptTest {
 public static void main(String[] x){byte[] key=new byte[32];Arrays.fill(key,(byte)7);
 var r=LongitudinalInboxQuarantineReceipt.seal(UUID.randomUUID(),"b".repeat(64),
 LongitudinalInboxQuarantineReceipt.Reason.RETRY_EXHAUSTED,Instant.now(),key);
 LongitudinalInboxQuarantineReceipt.verify(r,key);byte[] wrong=new byte[32];Arrays.fill(wrong,(byte)8);
 try{LongitudinalInboxQuarantineReceipt.verify(r,wrong);throw new AssertionError("key spoof");}catch(SecurityException expected){}
 var corrupt=new LongitudinalInboxQuarantineReceipt.Receipt(r.eventId(),r.eventDigest(),
 LongitudinalInboxQuarantineReceipt.Reason.INVALID_SCHEMA,r.recordedAt(),r.mac());
 try{LongitudinalInboxQuarantineReceipt.verify(corrupt,key);throw new AssertionError("reason spoof");}catch(SecurityException expected){}
 try{LongitudinalInboxQuarantineReceipt.seal(UUID.randomUUID(),"a".repeat(64),r.reason(),Instant.now(),new byte[2]);
 throw new AssertionError("short key");}catch(SecurityException expected){}
 if(r.toString().contains("patientName"))throw new AssertionError("PHI");
 System.out.println("PASS B0127 quarantine integrity 5 checks");}
}
