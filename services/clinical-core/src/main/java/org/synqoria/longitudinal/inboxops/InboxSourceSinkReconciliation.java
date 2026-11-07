package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0153: Cross-check source and destination privacy-minimal event fingerprint without content. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxSourceSinkReconciliation {
 private InboxSourceSinkReconciliation(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Fingerprint(UUID tenant,UUID facility,UUID patient,UUID encounter,UUID event,long revision,String hash){
  public Fingerprint{Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(patient);Objects.requireNonNull(encounter);Objects.requireNonNull(event);require(revision>=1&&hash!=null&&hash.matches("[0-9a-f]{64}"));}
 }
 public static void verify(Fingerprint source,Fingerprint sink){require(source!=null&&source.equals(sink));}

}
