package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0131: Constant-time HMAC-SHA256 envelope authenticity with bounded canonical bytes. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxAuthenticatedMessage {
 private InboxAuthenticatedMessage(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public static byte[] sign(byte[] key,byte[] frame){
  Objects.requireNonNull(key);Objects.requireNonNull(frame);require(key.length>=32&&frame.length>0&&frame.length<=2048);
  try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(frame);}
  catch(GeneralSecurityException e){throw new SecurityException("clinical event unavailable");}
 }
 public static void verify(byte[] key,byte[] frame,byte[] signature){
  require(signature!=null&&signature.length==32);require(MessageDigest.isEqual(sign(key,frame),signature));
 }

}
