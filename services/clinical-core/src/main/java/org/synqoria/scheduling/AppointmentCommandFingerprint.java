package org.synqoria.scheduling;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
/** Stable, length-prefixed command fingerprint. Never log patient identifiers or raw payload. */
public final class AppointmentCommandFingerprint {
 private AppointmentCommandFingerprint(){}
 public static String sha256(AppointmentAggregate booking,String command,long expectedRevision){
  Objects.requireNonNull(booking);
  if(command==null || !command.matches("[A-Z_]{3,40}") || expectedRevision<1)throw new IllegalArgumentException("invalid command");
  var fields=List.of("synqoria-appointment-v1",booking.tenant().toString(),booking.facility().toString(),
   booking.patient().toString(),booking.resource().toString(),booking.id().toString(),
   booking.window().start().toString(),booking.window().end().toString(),booking.window().zone().getId(),
   command,Long.toString(expectedRevision));
  try{
   var digest=MessageDigest.getInstance("SHA-256");
   for(String field:fields){byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(new byte[]{(byte)(bytes.length>>>24),(byte)(bytes.length>>>16),(byte)(bytes.length>>>8),(byte)bytes.length});digest.update(bytes);}
   return HexFormat.of().formatHex(digest.digest());
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
