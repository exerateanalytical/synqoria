package org.synqoria.security;
import java.time.Instant;
/** P03-B0013. No silent refresh or expiry grace for protected operations. */
public final class SessionLifetimeFence {
 private SessionLifetimeFence(){}
 public static boolean active(Instant now,Instant issuedAt,Instant expiresAt,Instant revocationCheckedAt,long maxRevocationLagSeconds){
  if(now==null||issuedAt==null||expiresAt==null||revocationCheckedAt==null||maxRevocationLagSeconds<0||maxRevocationLagSeconds>60)return false;
  return !now.isBefore(issuedAt)&&now.isBefore(expiresAt)&&!expiresAt.isAfter(issuedAt.plusSeconds(3600))
   &&!revocationCheckedAt.isAfter(now)&&!revocationCheckedAt.plusSeconds(maxRevocationLagSeconds).isBefore(now);
 }
}
