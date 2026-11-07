package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0136 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxProducerSequenceTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0136 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0136 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 String a="a".repeat(64);ok(InboxProducerSequence.classify(0,null,1,a)==InboxProducerSequence.Outcome.NEXT);
 ok(InboxProducerSequence.classify(8,a,8,a)==InboxProducerSequence.Outcome.EXACT_REPLAY);
 denied(()->InboxProducerSequence.classify(8,a,8,"b".repeat(64)));
 denied(()->InboxProducerSequence.classify(8,a,10,a));
 denied(()->InboxProducerSequence.classify(8,a,7,a));
 denied(()->InboxProducerSequence.classify(8,a,9,"INVALID"));

 System.out.println("PASS P07-B0136 ProducerSequence assertions="+assertions);
 }
}
