package org.synqoria.longitudinal;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Key-material-free envelope helper: caller owns production KMS lifecycle and rotation policy. */
public final class LongitudinalEnvelopeCrypto {
 private static final SecureRandom RAND=new SecureRandom();
 private LongitudinalEnvelopeCrypto(){}
 public record Context(Scope scope,long revision,String keyId){public Context{
  Objects.requireNonNull(scope);if(revision<0||keyId==null||!keyId.matches("[A-Za-z0-9._-]{1,80}"))
   throw new IllegalArgumentException("invalid envelope context");}}
 public record Envelope(String keyId,byte[] value){public Envelope{
  if(keyId==null||!keyId.matches("[A-Za-z0-9._-]{1,80}")||value==null||value.length<29||value.length>4_194_400)
   throw new IllegalArgumentException("invalid envelope");value=value.clone();
 }public byte[] value(){return value.clone();}}
 public interface KeyResolver {String activeId();SecretKey activeKey();SecretKey resolve(String keyId);}
 private static void checkKey(SecretKey k){if(k==null||!"AES".equalsIgnoreCase(k.getAlgorithm())||k.getEncoded()==null||k.getEncoded().length!=32)
  throw new IllegalArgumentException("AES-256-GCM key required");}
 private static byte[] aad(Context ctx){Scope s=ctx.scope();return ("synqoria-longitudinal-v1|"+s.tenant()+"|"+s.facility()+"|"+s.patient()+"|"+s.encounter()+"|"+ctx.revision()+"|"+ctx.keyId()).getBytes(StandardCharsets.UTF_8);}
 public static Envelope seal(Context ctx,byte[] clear,SecretKey key){
  Objects.requireNonNull(ctx);checkKey(key);if(clear==null||clear.length==0||clear.length>4_194_304)throw new IllegalArgumentException("invalid snapshot size");
  try {byte[] nonce=new byte[12];RAND.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(ctx));byte[] sealed=cipher.doFinal(clear);
   return new Envelope(ctx.keyId(),ByteBuffer.allocate(12+sealed.length).put(nonce).put(sealed).array());
  }catch(Exception e){throw new IllegalStateException("clinical encryption unavailable",e);}
 }
 public static byte[] open(Context ctx,Envelope envelope,SecretKey key){
  Objects.requireNonNull(ctx);Objects.requireNonNull(envelope);checkKey(key);
  if(!ctx.keyId().equals(envelope.keyId()))throw new SecurityException("clinical encryption context unavailable");
  try{byte[] bytes=envelope.value();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
   cipher.updateAAD(aad(ctx));return cipher.doFinal(bytes,12,bytes.length-12);
  }catch(Exception e){throw new SecurityException("clinical snapshot authentication failed");}
 }
}
