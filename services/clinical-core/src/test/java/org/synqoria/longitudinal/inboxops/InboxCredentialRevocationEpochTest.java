package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0154 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxCredentialRevocationEpochTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0154 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0154 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID();Instant now=Instant.parse("2026-11-04T08:00:00Z");
 var t=new InboxCredentialRevocationEpoch.Token(p,7,now.plusSeconds(50));
 InboxCredentialRevocationEpoch.admit(t,7,Set.of(),now);ok(true);
 denied(()->InboxCredentialRevocationEpoch.admit(t,8,Set.of(),now));
 denied(()->InboxCredentialRevocationEpoch.admit(t,7,Set.of(p),now));
 denied(()->InboxCredentialRevocationEpoch.admit(t,7,Set.of(),now.plusSeconds(50)));
 denied(()->new InboxCredentialRevocationEpoch.Token(p,0,now.plusSeconds(50)));

 System.out.println("PASS P07-B0154 CredentialRevocationEpoch assertions="+assertions);
 }
}
