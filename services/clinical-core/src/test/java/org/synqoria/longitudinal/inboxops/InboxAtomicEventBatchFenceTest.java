package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0159 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxAtomicEventBatchFenceTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0159 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0159 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID(),e=UUID.randomUUID();
 var a=new InboxAtomicEventBatchFence.Event(t,f,p,e,5,"a".repeat(64));
 var b=new InboxAtomicEventBatchFence.Event(t,f,p,e,6,"b".repeat(64));
 ok(InboxAtomicEventBatchFence.verify(List.of(a,b),4)==6);
 denied(()->InboxAtomicEventBatchFence.verify(List.of(a,b),3));
 denied(()->InboxAtomicEventBatchFence.verify(List.of(a,a),4));
 denied(()->InboxAtomicEventBatchFence.verify(List.of(a,new InboxAtomicEventBatchFence.Event(t,f,UUID.randomUUID(),e,6,"b".repeat(64))),4));
 denied(()->InboxAtomicEventBatchFence.verify(List.of(),4));

 System.out.println("PASS P07-B0159 AtomicEventBatchFence assertions="+assertions);
 }
}
