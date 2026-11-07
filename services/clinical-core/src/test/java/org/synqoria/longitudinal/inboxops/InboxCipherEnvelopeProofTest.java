package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0140 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxCipherEnvelopeProofTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0140 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0140 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID p=UUID.randomUUID();var d=new InboxCipherEnvelopeProof.Descriptor(p,"AES-256-GCM","key-2026-a",new byte[12],16,"a".repeat(64));
 InboxCipherEnvelopeProof.requireForPatient(d,p);ok(true);
 byte[] out=d.nonce();out[0]=5;ok(d.nonce()[0]==0);
 denied(()->InboxCipherEnvelopeProof.requireForPatient(d,UUID.randomUUID()));
 denied(()->new InboxCipherEnvelopeProof.Descriptor(p,"AES-128-GCM","key-2026-a",new byte[12],16,"a".repeat(64)));
 denied(()->new InboxCipherEnvelopeProof.Descriptor(p,"AES-256-GCM","key-2026-a",new byte[8],16,"a".repeat(64)));
 denied(()->new InboxCipherEnvelopeProof.Descriptor(p,"AES-256-GCM","key-2026-a",new byte[12],12,"a".repeat(64)));

 System.out.println("PASS P07-B0140 CipherEnvelopeProof assertions="+assertions);
 }
}
