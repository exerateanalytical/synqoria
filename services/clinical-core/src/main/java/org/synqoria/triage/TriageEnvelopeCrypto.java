package org.synqoria.triage;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** AES-GCM at-rest envelope. Key material supplied by trusted application KMS boundary, never persisted here. */
public final class TriageEnvelopeCrypto {
 private static final SecureRandom RAND=new SecureRandom();
 private static final int MAX=4_200_000;
 private TriageEnvelopeCrypto(){}
 public record Scope(UUID tenant,UUID facility,UUID patient,UUID caseId,int revision){
  public Scope{if(tenant==null||facility==null||patient==null||caseId==null||revision<1)throw new IllegalArgumentException("invalid encryption scope");}
 }
 public record Envelope(String keyId,byte[] ciphertext){public Envelope{if(keyId==null||!keyId.matches("[A-Za-z0-9._-]{1,80}")||ciphertext==null||ciphertext.length<29||ciphertext.length>MAX)throw new IllegalArgumentException("invalid envelope");ciphertext=ciphertext.clone();}public byte[] ciphertext(){return ciphertext.clone();}}
 private static byte[] aad(Scope scope,String keyId){return ("synqoria-triage-envelope-v1|"+scope.tenant()+"|"+scope.facility()+"|"+scope.patient()+"|"+scope.caseId()+"|"+scope.revision()+"|"+keyId).getBytes(StandardCharsets.UTF_8);}
 private static void key(SecretKey key){if(key==null||!"AES".equalsIgnoreCase(key.getAlgorithm())||key.getEncoded()==null||key.getEncoded().length!=32)throw new IllegalArgumentException("AES-256 key required");}
 public static Envelope seal(Scope scope,String keyId,SecretKey key,byte[] clear){
  key(key);if(clear==null||clear.length==0||clear.length>4_194_304)throw new IllegalArgumentException("invalid cleartext");
  try{byte[] nonce=new byte[12];RAND.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(aad(scope,keyId));byte[] encrypted=c.doFinal(clear);return new Envelope(keyId,ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array());}
  catch(Exception e){throw new IllegalStateException("encryption failure",e);}
 }
 public static byte[] open(Scope scope,Envelope envelope,SecretKey key){
  key(key);try{byte[] input=envelope.ciphertext();byte[] nonce=Arrays.copyOfRange(input,0,12);byte[] data=Arrays.copyOfRange(input,12,input.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(aad(scope,envelope.keyId()));return c.doFinal(data);}
  catch(Exception e){throw new SecurityException("triage ciphertext integrity unavailable");}
 }
}
