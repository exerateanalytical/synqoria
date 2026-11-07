package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0135 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxProvenanceChainTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0135 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0135 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID cell=UUID.randomUUID();String prev="a".repeat(64),event="b".repeat(64);
 String hash=InboxProvenanceChain.next(cell,4,prev,event);ok(hash.length()==64);
 InboxProvenanceChain.verify(cell,3,4,prev,event,hash);ok(true);
 denied(()->InboxProvenanceChain.verify(cell,2,4,prev,event,hash));
 denied(()->InboxProvenanceChain.verify(UUID.randomUUID(),3,4,prev,event,hash));
 denied(()->InboxProvenanceChain.verify(cell,3,4,prev,"c".repeat(64),hash));
 denied(()->InboxProvenanceChain.next(cell,4,"short",event));

 System.out.println("PASS P07-B0135 ProvenanceChain assertions="+assertions);
 }
}
