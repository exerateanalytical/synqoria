package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0032. Requires independently trusted claims and server-side facility/shift grant; never accepts client role flags. */
public final class OidcFacilitySessionFence {
 public record Grant(String subject,String sessionId,String facilityId,String role,String shiftId,Instant expiresAt,long epoch,boolean revoked){}
 private OidcFacilitySessionFence(){}
 public static boolean permits(OidcTrustedClaimPolicy.Claims claims,OidcTrustPins pins,Grant grant,String requestedFacility,String requiredRole,String requiredShift,long serverEpoch,Instant now){
  return OidcTrustedClaimPolicy.structurallyValid(claims,pins,now)&&grant!=null&&now!=null&&grant.expiresAt()!=null&&now.isBefore(grant.expiresAt())
   &&!grant.revoked()&&serverEpoch>0&&grant.epoch()==serverEpoch
   &&Objects.equals(grant.subject(),claims.subject())&&Objects.equals(grant.sessionId(),claims.sessionId())
   &&requestedFacility!=null&&requestedFacility.matches("[A-Za-z0-9_-]{8,128}")&&requestedFacility.equals(grant.facilityId())
   &&requiredRole!=null&&requiredRole.equals(grant.role())&&requiredShift!=null&&requiredShift.equals(grant.shiftId());
 }
}
