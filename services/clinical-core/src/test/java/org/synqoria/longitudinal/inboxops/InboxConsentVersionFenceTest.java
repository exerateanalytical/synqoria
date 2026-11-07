package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0139 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxConsentVersionFenceTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0139 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0139 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID(),f=UUID.randomUUID();Instant n=Instant.parse("2026-11-04T03:00:00Z");
 var g=new InboxConsentVersionFence.Proof(p,f,7,"direct-care",n.plusSeconds(30),false);
 InboxConsentVersionFence.verify(g,p,f,6,"direct-care",n);ok(true);
 denied(()->InboxConsentVersionFence.verify(g,p,f,8,"direct-care",n));
 denied(()->InboxConsentVersionFence.verify(g,UUID.randomUUID(),f,6,"direct-care",n));
 denied(()->InboxConsentVersionFence.verify(g,p,f,6,"research",n));
 denied(()->InboxConsentVersionFence.verify(g,p,f,6,"direct-care",n.plusSeconds(30)));
 denied(()->InboxConsentVersionFence.verify(new InboxConsentVersionFence.Proof(p,f,7,"direct-care",n.plusSeconds(30),true),p,f,6,"direct-care",n));

 System.out.println("PASS P07-B0139 ConsentVersionFence assertions="+assertions);
 }
}
