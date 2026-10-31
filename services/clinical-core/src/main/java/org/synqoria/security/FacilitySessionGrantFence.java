package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0023: comparison against a fresh authoritative grant snapshot, not a client-supplied role or header. */
public final class FacilitySessionGrantFence {
 public record Grant(UUID tenant,UUID facility,UUID actor,UUID session,Set<String> capabilities,long policyRevision,Instant verifiedAt,Instant expiresAt,boolean shiftActive,boolean revoked){
  public Grant {if(tenant==null||facility==null||actor==null||session==null||capabilities==null||policyRevision<0||verifiedAt==null||expiresAt==null||!expiresAt.isAfter(verifiedAt))throw new IllegalArgumentException("Invalid grant");capabilities=Set.copyOf(capabilities);}
 }
 private FacilitySessionGrantFence(){}
 public static boolean allows(Grant grant,UUID tenant,UUID facility,UUID actor,UUID session,String capability,long requiredPolicyRevision,Instant now){
  return grant!=null&&now!=null&&tenant!=null&&facility!=null&&actor!=null&&session!=null&&capability!=null
   &&grant.tenant().equals(tenant)&&grant.facility().equals(facility)&&grant.actor().equals(actor)&&grant.session().equals(session)
   &&grant.policyRevision()>=requiredPolicyRevision&&requiredPolicyRevision>=0&&grant.shiftActive()&&!grant.revoked()
   &&grant.capabilities().contains(capability)&&!now.isBefore(grant.verifiedAt())
   &&!grant.verifiedAt().isBefore(now.minusSeconds(15))&&now.isBefore(grant.expiresAt());
 }
}
