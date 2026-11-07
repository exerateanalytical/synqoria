package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0140: Ciphertext descriptor enforcement for AES-256-GCM envelope (without decrypting). Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxCipherEnvelopeProof {
 private InboxCipherEnvelopeProof(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Descriptor(UUID patient,String algorithm,String keyId,byte[] nonce,int tagBytes,String payloadHash){
  public Descriptor{Objects.requireNonNull(patient);Objects.requireNonNull(algorithm);Objects.requireNonNull(keyId);Objects.requireNonNull(nonce);Objects.requireNonNull(payloadHash);
   require(algorithm.equals("AES-256-GCM")&&keyId.matches("[a-zA-Z0-9_.-]{8,64}")&&nonce.length==12&&tagBytes==16&&payloadHash.matches("[0-9a-f]{64}"));nonce=nonce.clone();}
  @Override public byte[] nonce(){return nonce.clone();}
 }
 public static void requireForPatient(Descriptor d,UUID patient){require(d!=null&&d.patient().equals(patient));}

}
