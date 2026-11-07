package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0134: Event-type-to-capability authorization with immutable actor-scoped grant set. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxCapabilityGrant {
 private InboxCapabilityGrant(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Grant(UUID actor,Set<String> capabilities,Instant expiresAt){public Grant{Objects.requireNonNull(actor);capabilities=Set.copyOf(capabilities);Objects.requireNonNull(expiresAt);require(capabilities.size()<=64);}}
 public static void authorize(Grant grant,Map<String,String> requiredByEvent,String type,UUID actor,Instant now){
  Objects.requireNonNull(grant);Objects.requireNonNull(requiredByEvent);Objects.requireNonNull(now);
  String required=requiredByEvent.get(type);require(required!=null&&required.matches("[a-z][a-z0-9_.-]{2,95}")&&grant.actor().equals(actor)&&grant.capabilities().contains(required)&&now.isBefore(grant.expiresAt()));
 }

}
