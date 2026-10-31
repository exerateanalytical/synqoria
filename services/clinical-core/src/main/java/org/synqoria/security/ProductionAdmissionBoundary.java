package org.synqoria.security;
import java.time.Instant;
import java.util.*;
/** Mandatory server-side production admission wiring: requires key-verified identity and current revocation/grants.
 * This enforces dependency configuration, NOT OIDC cryptographic verification; that still requires a supported provider.
 */
public final class ProductionAdmissionBoundary {
 private final TrustedSessionAdmission delegate;
 public ProductionAdmissionBoundary(TrustedSessionAdmission.IdentityVerifier cryptographicallyVerified,
    TrustedSessionAdmission.PrivilegeSource authoritativeGrants,SessionEpochFence currentRevocation,
    String expectedIssuer,String expectedAudience){
  delegate=new TrustedSessionAdmission(Objects.requireNonNull(cryptographicallyVerified),
    Objects.requireNonNull(authoritativeGrants),expectedIssuer,expectedAudience,Objects.requireNonNull(currentRevocation));
 }
 public Optional<TrustedAccessContext> admit(String bearer,Instant at){return delegate.admit(bearer,at);}
}
