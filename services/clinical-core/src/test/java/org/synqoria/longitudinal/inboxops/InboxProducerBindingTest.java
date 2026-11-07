package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0130 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxProducerBindingTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0130 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0130 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID();Instant now=Instant.parse("2026-11-04T01:00:00Z");
 var grant=new InboxProducerBinding.Grant(p,t,f,Set.of("note.signed"),now.plusSeconds(100),false);
 InboxProducerBinding.admit(grant,p,t,f,"note.signed",now);ok(true);
 denied(()->InboxProducerBinding.admit(grant,UUID.randomUUID(),t,f,"note.signed",now));
 denied(()->InboxProducerBinding.admit(grant,p,t,UUID.randomUUID(),"note.signed",now));
 denied(()->InboxProducerBinding.admit(grant,p,t,f,"note.draft",now));
 denied(()->InboxProducerBinding.admit(grant,p,t,f,"note.signed",now.plusSeconds(100)));
 denied(()->InboxProducerBinding.admit(new InboxProducerBinding.Grant(p,t,f,Set.of("note.signed"),now.plusSeconds(100),true),p,t,f,"note.signed",now));

 System.out.println("PASS P07-B0130 ProducerBinding assertions="+assertions);
 }
}
