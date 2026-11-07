package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0130: Authenticated producer identity, tenant/facility binding and event type grant. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxProducerBinding {
 private InboxProducerBinding(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Grant(UUID producer,UUID tenant,UUID facility,Set<String> types,Instant expiresAt,boolean revoked) {
   public Grant {Objects.requireNonNull(producer);Objects.requireNonNull(tenant);Objects.requireNonNull(facility);types=Set.copyOf(types);Objects.requireNonNull(expiresAt);require(!types.isEmpty()&&types.size()<=32);}
 }
 public static void admit(Grant g,UUID producer,UUID tenant,UUID facility,String type,Instant now){
  Objects.requireNonNull(g);Objects.requireNonNull(now);
  require(!g.revoked()&&g.producer().equals(producer)&&g.tenant().equals(tenant)&&g.facility().equals(facility)&&g.types().contains(type)&&now.isBefore(g.expiresAt()));
 }

}
