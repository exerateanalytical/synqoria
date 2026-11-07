package org.synqoria.longitudinal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;

public final class LongitudinalInboxWireTest {
  static int cases=0;
  static void check(boolean x){cases++;if(!x)throw new AssertionError("wire #"+cases);}
  static void reject(Runnable r){cases++;try{r.run();throw new AssertionError("accepted invalid frame");}
    catch(IllegalArgumentException expected){}}
  public static void main(String[] args){
    Scope s=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    Envelope e=new Envelope(s,UUID.randomUUID(),UUID.randomUUID(),1,"a".repeat(64),"note.sign",Instant.parse("2026-11-01T12:00:00Z"));
    byte[] data=LongitudinalInboxWire.encode(e);
    check(data.length<512);check(LongitudinalInboxWire.decode(data).equals(e));
    reject(()->LongitudinalInboxWire.decode(null));
    reject(()->LongitudinalInboxWire.decode(new byte[513]));
    reject(()->LongitudinalInboxWire.decode(new byte[]{(byte)0xc3,(byte)0x28}));
    reject(()->LongitudinalInboxWire.decode((new String(data,StandardCharsets.US_ASCII)+"|extra").getBytes(StandardCharsets.UTF_8)));
    reject(()->LongitudinalInboxWire.decode(new String(data,StandardCharsets.US_ASCII).replace("note.sign","note.sign\nHIV").getBytes(StandardCharsets.UTF_8)));
    reject(()->LongitudinalInboxWire.decode(new String(data,StandardCharsets.US_ASCII).replace("|1|","|0001|").getBytes(StandardCharsets.UTF_8)));
    reject(()->LongitudinalInboxWire.decode(new String(data,StandardCharsets.US_ASCII).replace("|"+"a".repeat(64)+"|","|BADHASH|").getBytes(StandardCharsets.UTF_8)));
    check(!new String(data,StandardCharsets.UTF_8).contains("patient_name"));
    System.out.println("PASS P07-B0116 wire "+cases+" assertions");
  }
}
