package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0143 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxReplayReceiptIntegrityTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0143 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0143 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID id=UUID.randomUUID();var r=new InboxReplayReceiptIntegrity.Receipt(id,3,"a".repeat(64),"b".repeat(64),true);
 InboxReplayReceiptIntegrity.replayOnlyIfExact(r,id,3,"a".repeat(64),"b".repeat(64));ok(true);
 denied(()->InboxReplayReceiptIntegrity.replayOnlyIfExact(r,id,3,"c".repeat(64),"b".repeat(64)));
 denied(()->InboxReplayReceiptIntegrity.replayOnlyIfExact(r,id,4,"a".repeat(64),"b".repeat(64)));
 denied(()->InboxReplayReceiptIntegrity.replayOnlyIfExact(r,UUID.randomUUID(),3,"a".repeat(64),"b".repeat(64)));
 denied(()->InboxReplayReceiptIntegrity.replayOnlyIfExact(new InboxReplayReceiptIntegrity.Receipt(id,3,"a".repeat(64),"b".repeat(64),false),id,3,"a".repeat(64),"b".repeat(64)));

 System.out.println("PASS P07-B0143 ReplayReceiptIntegrity assertions="+assertions);
 }
}
