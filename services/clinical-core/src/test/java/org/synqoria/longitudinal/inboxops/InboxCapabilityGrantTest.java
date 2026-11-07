package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0134 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxCapabilityGrantTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0134 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0134 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID a=UUID.randomUUID();Instant now=Instant.parse("2026-11-04T02:00:00Z");
 var g=new InboxCapabilityGrant.Grant(a,Set.of("clinical.note.read"),now.plusSeconds(2));var m=Map.of("note.signed","clinical.note.read");
 InboxCapabilityGrant.authorize(g,m,"note.signed",a,now);ok(true);
 denied(()->InboxCapabilityGrant.authorize(g,m,"note.signed",UUID.randomUUID(),now));
 denied(()->InboxCapabilityGrant.authorize(g,m,"note.amended",a,now));
 denied(()->InboxCapabilityGrant.authorize(g,m,"note.signed",a,now.plusSeconds(2)));
 denied(()->InboxCapabilityGrant.authorize(new InboxCapabilityGrant.Grant(a,Set.of(),now.plusSeconds(3)),m,"note.signed",a,now));

 System.out.println("PASS P07-B0134 CapabilityGrant assertions="+assertions);
 }
}
