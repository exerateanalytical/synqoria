package org.synqoria.security;
import java.time.*;import java.util.*;
/** Validates timestamps and scope of ALREADY cryptographically verified server-side session evidence.
 * Does not parse tokens or establish JWT/OIDC trust. Never construct evidence from HTTP body/headers. */
public final class SessionFreshnessPolicy {
 private SessionFreshnessPolicy(){}
 public record VerifiedSessionEvidence(UUID tenant,UUID facility,UUID actor,Instant authenticatedAt,Instant expiresAt,
              Instant mfaAt,Instant revocationCheckedAt,long policyRevision,boolean externallyVerified) {
  public VerifiedSessionEvidence {
    if(tenant==null||facility==null||actor==null||authenticatedAt==null||expiresAt==null||mfaAt==null
       ||revocationCheckedAt==null||policyRevision<1||!expiresAt.isAfter(authenticatedAt))throw new IllegalArgumentException("Invalid session evidence");
  }
 }
 public static boolean allows(TrustedAccessContext context, VerifiedSessionEvidence session,Instant serverNow,
      Duration maxMfaAge,Duration maxRevocationAge,long minimumPolicyRevision) {
   if(context==null||session==null||serverNow==null||maxMfaAge==null||maxRevocationAge==null
      ||maxMfaAge.isNegative()||maxMfaAge.isZero()||maxRevocationAge.isNegative()||maxRevocationAge.isZero()||minimumPolicyRevision<1)return false;
   if(!session.externallyVerified()||!session.tenant().equals(context.tenantId())||!session.facility().equals(context.facilityId())
      ||!session.actor().equals(context.actorId())||session.policyRevision()<minimumPolicyRevision)return false;
   if(serverNow.isBefore(session.authenticatedAt())||!serverNow.isBefore(session.expiresAt()))return false;
   if(session.mfaAt().isAfter(serverNow)||session.mfaAt().isBefore(session.authenticatedAt())
       ||session.mfaAt().plus(maxMfaAge).isBefore(serverNow))return false;
   if(session.revocationCheckedAt().isAfter(serverNow)||session.revocationCheckedAt().isBefore(session.authenticatedAt())
       ||session.revocationCheckedAt().plus(maxRevocationAge).isBefore(serverNow))return false;
   return true;
 }
}
