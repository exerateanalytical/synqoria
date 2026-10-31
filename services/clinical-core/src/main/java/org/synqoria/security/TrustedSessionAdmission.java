package org.synqoria.security;
import java.time.Instant;import java.util.*;
/**
 * P03-B0004 adapter admission boundary. IdentityVerifier MUST perform real
 * cryptographic OIDC validation (issuer, audience, algorithm, signature, key
 * rotation, nonce/session, exp/nbf, revocation). NO such verifier ships here.
 * Claims from an HTTP body/header MUST NOT be passed as a verified identity.
 */
public final class TrustedSessionAdmission {
 public record VerifiedIdentity(String issuer,String audience,UUID actor,UUID tenant,UUID facility,
                                Instant issuedAt,Instant expiresAt,boolean mfa,boolean revoked) {
  public VerifiedIdentity {
   Objects.requireNonNull(issuer);Objects.requireNonNull(audience);Objects.requireNonNull(actor);
   Objects.requireNonNull(tenant);Objects.requireNonNull(facility);
   Objects.requireNonNull(issuedAt);Objects.requireNonNull(expiresAt);
  }
 }
 @FunctionalInterface public interface IdentityVerifier { Optional<VerifiedIdentity> verifyOpaqueBearer(String bearer); }
 @FunctionalInterface public interface PrivilegeSource { Optional<Privileges> forVerifiedIdentity(VerifiedIdentity identity); }
 public record Privileges(Set<String> capabilities,Set<UUID> patientIds,boolean facilityActive,boolean shiftActive) {
  public Privileges {capabilities=Set.copyOf(capabilities);patientIds=Set.copyOf(patientIds);}
 }
 private final IdentityVerifier verifier;private final PrivilegeSource privileges;
 private final String issuer,audience;
 private final SessionEpochFence epochFence;
 public TrustedSessionAdmission(IdentityVerifier verifier,PrivilegeSource privileges,String issuer,String audience) {
  this(verifier,privileges,issuer,audience,(id,now)->true);
 }
 /** A live backend MUST use this overload with authoritative revocation checks. */
 public TrustedSessionAdmission(IdentityVerifier verifier,PrivilegeSource privileges,String issuer,String audience,SessionEpochFence epochFence){
  this.verifier=Objects.requireNonNull(verifier);this.privileges=Objects.requireNonNull(privileges);
  this.epochFence=Objects.requireNonNull(epochFence);
  if(issuer==null||issuer.isBlank()||audience==null||audience.isBlank())throw new IllegalArgumentException("Missing trust pins");
  this.issuer=issuer;this.audience=audience;
 }
 public Optional<TrustedAccessContext> admit(String opaqueBearer,Instant serverNow) {
  if(opaqueBearer==null||opaqueBearer.length()<16||opaqueBearer.length()>8192||serverNow==null)return Optional.empty();
  try {
   var checked=verifier.verifyOpaqueBearer(opaqueBearer);
   if(checked==null||checked.isEmpty())return Optional.empty();
   var c=checked.get();
   if(!issuer.equals(c.issuer())||!audience.equals(c.audience())||!c.mfa()||c.revoked()
      ||serverNow.isBefore(c.issuedAt())||!serverNow.isBefore(c.expiresAt())
      ||c.expiresAt().isAfter(c.issuedAt().plusSeconds(3600)))return Optional.empty();
   if(!epochFence.current(c,serverNow))return Optional.empty();
   var grants=privileges.forVerifiedIdentity(c);
   if(grants==null||grants.isEmpty()||!grants.get().facilityActive()||!grants.get().shiftActive())return Optional.empty();
   return Optional.of(new TrustedAccessContext(c.tenant(),c.facility(),c.actor(),grants.get().capabilities(),grants.get().patientIds()));
  } catch(RuntimeException e){return Optional.empty();}
 }
}
