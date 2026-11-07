package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0144 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxProjectionCompareSwapTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0144 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0144 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID();var prior=new InboxProjectionCompareSwap.State(p,3,"a".repeat(64));
 var next=InboxProjectionCompareSwap.update(prior,p,3,"a".repeat(64),"b".repeat(64));ok(next.revision()==4);
 ok(prior.revision()==3);
 denied(()->InboxProjectionCompareSwap.update(prior,p,2,"a".repeat(64),"b".repeat(64)));
 denied(()->InboxProjectionCompareSwap.update(prior,p,3,"c".repeat(64),"b".repeat(64)));
 denied(()->InboxProjectionCompareSwap.update(prior,UUID.randomUUID(),3,"a".repeat(64),"b".repeat(64)));
 denied(()->InboxProjectionCompareSwap.update(prior,p,3,"a".repeat(64),"a".repeat(64)));

 System.out.println("PASS P07-B0144 ProjectionCompareSwap assertions="+assertions);
 }
}
