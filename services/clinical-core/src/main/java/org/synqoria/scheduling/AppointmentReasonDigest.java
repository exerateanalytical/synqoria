package org.synqoria.scheduling;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
/** Binds sensitive rationale into a digest without putting its plaintext into an outbox. */
public final class AppointmentReasonDigest {
 private AppointmentReasonDigest(){}
 public static String bind(String commandDigest,String rationale){
  if(commandDigest==null||!commandDigest.matches("[a-f0-9]{64}")||rationale==null||rationale.isBlank()||rationale.length()>500)
   throw new IllegalArgumentException("invalid rationale digest input");
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
   ("synqoria-appointment-rationale-v1:"+commandDigest+":"+rationale).getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
