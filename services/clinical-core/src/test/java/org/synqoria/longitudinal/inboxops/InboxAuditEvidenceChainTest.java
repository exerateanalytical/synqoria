package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0149 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxAuditEvidenceChainTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0149 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0149 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var now=Instant.parse("2026-11-04T04:00:00Z");UUID a=UUID.randomUUID();
 var e=new InboxAuditEvidenceChain.Evidence("a".repeat(64),"b".repeat(64),a,"APPLIED",2,now);
 String h=InboxAuditEvidenceChain.digest(e);ok(h.matches("[0-9a-f]{64}"));ok(h.equals(InboxAuditEvidenceChain.digest(e)));
 ok(!h.equals(InboxAuditEvidenceChain.digest(new InboxAuditEvidenceChain.Evidence("c".repeat(64),"b".repeat(64),a,"APPLIED",2,now))));
 denied(()->new InboxAuditEvidenceChain.Evidence("invalid","b".repeat(64),a,"APPLIED",2,now));
 denied(()->new InboxAuditEvidenceChain.Evidence("a".repeat(64),"b".repeat(64),a,"patientName",2,now));

 System.out.println("PASS P07-B0149 AuditEvidenceChain assertions="+assertions);
 }
}
