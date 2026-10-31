package org.synqoria.security;
import java.time.Instant;import java.util.*;
/** P03-B0008 synthetic emergency-access reference. No actual break-glass grant is issued. */
public final class EmergencyAccessPolicy {
 private EmergencyAccessPolicy(){}
 public enum Result { REVIEWABLE_REQUEST, DENIED }
 public record Request(UUID tenant,UUID facility,UUID actor,UUID patient,String reasonCode,String incidentId,
                       Instant requestedAt,Instant expiresAt,boolean identityMfaVerified,boolean witnessedOrApproved){}
 public static Result review(TrustedAccessContext context,Request request,Instant now){
  if(context==null||request==null||now==null)return Result.DENIED;
  if(!context.tenantId().equals(request.tenant())||!context.facilityId().equals(request.facility())
    ||!context.actorId().equals(request.actor())||request.patient()==null||!context.capabilities().contains("emergency:request"))return Result.DENIED;
  if(!Set.of("IMMEDIATE_LIFE_THREAT","UNIDENTIFIED_EMERGENCY").contains(request.reasonCode())
    ||request.incidentId()==null||!request.incidentId().matches("[A-Za-z0-9_-]{12,96}")
    ||!request.identityMfaVerified()||!request.witnessedOrApproved()
    ||request.requestedAt()==null||request.expiresAt()==null||now.isBefore(request.requestedAt())
    ||!now.isBefore(request.expiresAt())||request.expiresAt().isAfter(request.requestedAt().plusSeconds(600)))return Result.DENIED;
  return Result.REVIEWABLE_REQUEST; // not clinical read authorization; must emit audit in actual server workflow
 }
}
