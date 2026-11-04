package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0131 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxAuthenticatedMessageTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0131 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0131 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 byte[] key=new byte[32];Arrays.fill(key,(byte)7);byte[] frame="envelope-v1".getBytes(StandardCharsets.UTF_8);
 byte[] sig=InboxAuthenticatedMessage.sign(key,frame);ok(sig.length==32);
 InboxAuthenticatedMessage.verify(key,frame,sig);ok(true);
 denied(()->InboxAuthenticatedMessage.verify(key,"envelope-v2".getBytes(StandardCharsets.UTF_8),sig));
 denied(()->InboxAuthenticatedMessage.verify(new byte[16],frame,sig));
 denied(()->InboxAuthenticatedMessage.verify(key,frame,new byte[31]));
 denied(()->InboxAuthenticatedMessage.sign(key,new byte[2049]));

 System.out.println("PASS P07-B0131 AuthenticatedMessage assertions="+assertions);
 }
}
