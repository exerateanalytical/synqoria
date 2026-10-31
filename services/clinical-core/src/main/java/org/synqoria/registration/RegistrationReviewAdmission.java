package org.synqoria.registration;
import java.time.*;import java.util.*;import org.synqoria.security.TrustedAccessContext;
/** Internal mandatory chain of review checks. A true result is NOT external API write authorization. */
public final class RegistrationReviewAdmission {
 private RegistrationReviewAdmission(){}
 public static boolean reviewed(TrustedAccessContext actor,RegistrationCommand cmd,ReviewDecisionProof proof,ReviewDecisionAuthority authority,
    UUID candidateId,long currentRevision,boolean committedAuthoritativeRow,boolean activeFacility,boolean shiftCurrent,boolean grantsCurrent,Instant now){
  return actor!=null&&cmd!=null&&proof!=null&&authority!=null&&candidateId!=null&&now!=null&&committedAuthoritativeRow
   &&RegistrationAuthorization.canRegister(actor,actor.facilityId(),activeFacility,shiftCurrent,grantsCurrent)
   &&proof.tenantId().equals(actor.tenantId())&&proof.facilityId().equals(actor.facilityId())&&proof.registrarId().equals(actor.actorId())
   &&proof.candidateId().equals(candidateId)&&proof.commandId().equals(cmd.commandId())&&proof.patientId().equals(cmd.patientId())&&proof.candidateRevision()==currentRevision&&!proof.reviewerId().equals(actor.actorId())
   &&RegistrationFingerprint.same(proof.fingerprint(),cmd.fingerprintHmac())&&authority.verify(proof,now);
 }
}
