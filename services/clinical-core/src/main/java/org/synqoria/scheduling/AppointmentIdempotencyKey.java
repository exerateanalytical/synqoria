package org.synqoria.scheduling;
import java.nio.charset.StandardCharsets;
public final class AppointmentIdempotencyKey {
 private AppointmentIdempotencyKey(){}
 public static String validate(String key){
  if(key==null||key.length()<16||key.length()>128||!key.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("invalid idempotency key");
  if(key.getBytes(StandardCharsets.US_ASCII).length!=key.length())throw new IllegalArgumentException("invalid key encoding");
  return key;
 }
}
