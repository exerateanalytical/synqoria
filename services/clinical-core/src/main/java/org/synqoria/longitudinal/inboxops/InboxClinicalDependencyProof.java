package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0158: Trusted prerequisite evidence gate for inbox runtime (RLS, OIDC, KMS, Kafka, clock). Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxClinicalDependencyProof {
 private InboxClinicalDependencyProof(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Proof(boolean rlsVerified,boolean identityVerified,boolean kmsVerified,boolean brokerVerified,boolean clockVerified,Instant expiresAt){public Proof{Objects.requireNonNull(expiresAt);}}
 public static void admit(Proof p,Instant now){require(p!=null&&now!=null&&p.rlsVerified()&&p.identityVerified()&&p.kmsVerified()&&p.brokerVerified()&&p.clockVerified()&&now.isBefore(p.expiresAt()));}

}
