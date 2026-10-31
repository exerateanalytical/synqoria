package org.synqoria.consent;
import java.time.Instant;import java.util.*;
/** P03-B0005 explicit-consent predicate; reviewed legal-basis exceptions NOT implemented. */
public final class ConsentScopePolicy {
 public record Grant(UUID tenant,UUID facility,UUID patient,UUID consentId,String purpose,String operation,
                     Instant effectiveAt,Instant expiresAt,Instant revokedAt,long revision,String status) {
  public Grant {
   Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(patient);Objects.requireNonNull(consentId);
   Objects.requireNonNull(purpose);Objects.requireNonNull(operation);Objects.requireNonNull(effectiveAt);Objects.requireNonNull(expiresAt);
   if(!expiresAt.isAfter(effectiveAt)||revision<1||!Set.of("ACTIVE","REVOKED","EXPIRED").contains(status))
    throw new IllegalArgumentException("Invalid consent record");
  }
 }
 public static boolean allows(Grant grant,UUID tenant,UUID facility,UUID patient,String purpose,String operation,Instant now,boolean revocationFresh) {
  if(grant==null||tenant==null||facility==null||patient==null||purpose==null||operation==null||now==null||!revocationFresh)return false;
  return grant.tenant().equals(tenant)&&grant.facility().equals(facility)&&grant.patient().equals(patient)
   &&grant.purpose().equals(purpose)&&grant.operation().equals(operation)
   &&"ACTIVE".equals(grant.status())&&grant.revokedAt()==null
   &&!now.isBefore(grant.effectiveAt())&&now.isBefore(grant.expiresAt());
 }
}
