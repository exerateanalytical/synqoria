package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0142 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxPayloadDigestProofTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0142 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0142 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 byte[] content="clinical event".getBytes(StandardCharsets.UTF_8);String hash=InboxPayloadDigestProof.sha256(content);
 ok(hash.length()==64);InboxPayloadDigestProof.verify(content,hash);ok(true);
 denied(()->InboxPayloadDigestProof.verify("altered event".getBytes(StandardCharsets.UTF_8),hash));
 denied(()->InboxPayloadDigestProof.verify(content,"not-a-digest"));
 denied(()->InboxPayloadDigestProof.sha256(new byte[0]));
 denied(()->InboxPayloadDigestProof.sha256(new byte[1048577]));

 System.out.println("PASS P07-B0142 PayloadDigestProof assertions="+assertions);
 }
}
