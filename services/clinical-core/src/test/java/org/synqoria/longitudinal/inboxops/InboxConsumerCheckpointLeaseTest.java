package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0145 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxConsumerCheckpointLeaseTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0145 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0145 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID o=UUID.randomUUID();var c=new InboxConsumerCheckpointLease.Checkpoint(o,"clinical-group",2,5,7);
 var next=InboxConsumerCheckpointLease.advance(c,o,"clinical-group",2,7,6);ok(next.offset()==6);
 denied(()->InboxConsumerCheckpointLease.advance(c,o,"clinical-group",2,7,8));
 denied(()->InboxConsumerCheckpointLease.advance(c,o,"clinical-group",2,6,6));
 denied(()->InboxConsumerCheckpointLease.advance(c,UUID.randomUUID(),"clinical-group",2,7,6));
 denied(()->InboxConsumerCheckpointLease.advance(c,o,"other-group",2,7,6));

 System.out.println("PASS P07-B0145 ConsumerCheckpointLease assertions="+assertions);
 }
}
