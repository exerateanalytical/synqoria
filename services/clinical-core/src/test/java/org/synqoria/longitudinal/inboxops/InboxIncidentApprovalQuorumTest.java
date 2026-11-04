package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0150 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxIncidentApprovalQuorumTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0150 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0150 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID(),r=UUID.randomUUID(),s=UUID.randomUUID(),c=UUID.randomUUID(),o=UUID.randomUUID();
 Instant now=Instant.parse("2026-11-04T05:00:00Z");
 var a=new InboxIncidentApprovalQuorum.Approval(UUID.randomUUID(),r,s,c,o,p,"INC-2026-00001",now.plusSeconds(120));
 InboxIncidentApprovalQuorum.authorize(a,p,now);ok(true);
 denied(()->InboxIncidentApprovalQuorum.authorize(a,UUID.randomUUID(),now));
 denied(()->InboxIncidentApprovalQuorum.authorize(a,p,now.plusSeconds(120)));
 denied(()->new InboxIncidentApprovalQuorum.Approval(UUID.randomUUID(),r,r,c,o,p,"INC-2026-00001",now.plusSeconds(120)));
 denied(()->new InboxIncidentApprovalQuorum.Approval(UUID.randomUUID(),r,s,c,o,p,"patient=Mary",now.plusSeconds(120)));

 System.out.println("PASS P07-B0150 IncidentApprovalQuorum assertions="+assertions);
 }
}
