package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0154: Trusted issuer revocation epoch and credential validity fence for consumers. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxCredentialRevocationEpoch {
 private InboxCredentialRevocationEpoch(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Token(UUID principal,long issuedEpoch,Instant expiresAt){public Token{Objects.requireNonNull(principal);Objects.requireNonNull(expiresAt);require(issuedEpoch>=1);}}
 public static void admit(Token token,long currentEpoch,Set<UUID> revoked,Instant now){
  Objects.requireNonNull(revoked);Objects.requireNonNull(now);
  require(token!=null&&currentEpoch>=1&&token.issuedEpoch()>=currentEpoch&&!revoked.contains(token.principal())&&now.isBefore(token.expiresAt()));
 }

}
