package org.synqoria.registration;
import java.time.*;import java.util.*;import org.synqoria.security.TrustedAccessContext;
/** P05-B0004. Strict preflight; no client-entered patient identifiers trusted as scope. */
public final class RegistrationRequestPolicy {
 private RegistrationRequestPolicy(){}
 public static boolean allowed(TrustedAccessContext actor, RegistrationCommand cmd, boolean activeFacility,
  boolean validShift, boolean currentGrants, boolean unresolvedDuplicate, boolean requestedAutoMerge, Instant now) {
  if(actor==null||cmd==null||now==null||!RegistrationAuthorization.canRegister(actor,actor.facilityId(),activeFacility,validShift,currentGrants))return false;
  if(DuplicateReviewGate.decide(cmd.draft().duplicateReviewCleared(),unresolvedDuplicate,requestedAutoMerge)!=DuplicateReviewGate.Decision.ALLOW_PROVISIONAL)return false;
  return !cmd.requestedAt().isAfter(now.plusSeconds(30))&&!cmd.requestedAt().isBefore(now.minusSeconds(300))
    &&cmd.draft().plausibleAt(LocalDate.ofInstant(now,ZoneOffset.UTC));
 }
}
