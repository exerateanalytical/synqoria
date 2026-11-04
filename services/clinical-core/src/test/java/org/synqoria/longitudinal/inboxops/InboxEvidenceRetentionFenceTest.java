package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0152 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxEvidenceRetentionFenceTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0152 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0152 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 Instant n=Instant.parse("2026-11-04T07:00:00Z");var r=new InboxEvidenceRetentionFence.Record(n.minus(Duration.ofDays(120)),false,false);
 InboxEvidenceRetentionFence.mayPurge(r,n,Duration.ofDays(90));ok(true);
 denied(()->InboxEvidenceRetentionFence.mayPurge(r,n,Duration.ofDays(200)));
 denied(()->InboxEvidenceRetentionFence.mayPurge(r,n,Duration.ofDays(10)));
 denied(()->InboxEvidenceRetentionFence.mayPurge(new InboxEvidenceRetentionFence.Record(r.recordedAt(),true,false),n,Duration.ofDays(90)));
 denied(()->InboxEvidenceRetentionFence.mayPurge(new InboxEvidenceRetentionFence.Record(r.recordedAt(),false,true),n,Duration.ofDays(90)));

 System.out.println("PASS P07-B0152 EvidenceRetentionFence assertions="+assertions);
 }
}
