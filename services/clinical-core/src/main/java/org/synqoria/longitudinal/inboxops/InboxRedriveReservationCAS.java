package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0151: Single-use incident redrive reservation guarded by holder and generation. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxRedriveReservationCAS {
 private InboxRedriveReservationCAS(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Reservation(UUID incident,UUID holder,long generation,Instant expiresAt,boolean consumed){
  public Reservation{Objects.requireNonNull(incident);Objects.requireNonNull(holder);Objects.requireNonNull(expiresAt);require(generation>=1);}
 }
 public static Reservation consume(Reservation r,UUID incident,UUID actor,long expectedGeneration,Instant now){
  require(r!=null&&!r.consumed()&&r.incident().equals(incident)&&r.holder().equals(actor)&&r.generation()==expectedGeneration&&now.isBefore(r.expiresAt()));
  return new Reservation(r.incident(),r.holder(),r.generation()+1,r.expiresAt(),true);
 }

}
