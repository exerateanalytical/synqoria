package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0142: Bounded payload SHA-256 comparison and no content exposure in exceptions. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxPayloadDigestProof {
 private InboxPayloadDigestProof(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public static String sha256(byte[] bytes){Objects.requireNonNull(bytes);require(bytes.length>0&&bytes.length<=1024*1024);
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static void verify(byte[] bytes,String expected){require(expected!=null&&expected.matches("[0-9a-f]{64}"));
  require(MessageDigest.isEqual(sha256(bytes).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)));
 }

}
