package org.synqoria.registration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;
/** Server-only proof issuer/verifier for a verified reviewer event; production HSM/rotation not yet integrated. */
public final class ReviewDecisionAuthority {
 private final byte[] key; private final String keyId;
 public ReviewDecisionAuthority(String keyId,byte[] key){
  if(keyId==null||!keyId.matches("[A-Za-z0-9._-]{3,64}")||key==null||key.length<32||sameBytes(key))throw new IllegalArgumentException("Invalid key authority");
  this.keyId=keyId;this.key=key.clone();
 }
 private static boolean sameBytes(byte[] b){for(int i=1;i<b.length;i++)if(b[i]!=b[0])return false;return true;}
 private byte[] sign(ReviewDecisionProof p){
  try{
   String msg=String.join("\n","SYNQORIA-REVIEW-v1",p.tenantId().toString(),p.facilityId().toString(),p.reviewId().toString(),p.reviewerId().toString(),p.registrarId().toString(),p.candidateId().toString(),p.commandId().toString(),p.patientId().toString(),Long.toString(p.candidateRevision()),HexFormat.of().formatHex(p.fingerprint()),p.decidedAt().toString(),p.expiresAt().toString(),p.outcome(),p.keyId());
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
  }catch(Exception e){throw new IllegalStateException("Reviewer proof unavailable",e);}
 }
 /** Only call after authoritative reviewer IAM and a durable reviewer decision transaction. */
 public ReviewDecisionProof issue(UUID tenant,UUID facility,UUID reviewer,UUID registrar,UUID candidate,UUID commandId,UUID patientId,long revision,byte[] fingerprint,Instant now,Duration ttl){
  if(ttl==null||ttl.isNegative()||ttl.isZero()||ttl.compareTo(Duration.ofMinutes(10))>0||now==null)throw new IllegalArgumentException("Invalid review window");
  ReviewDecisionProof draft=new ReviewDecisionProof(tenant,facility,UUID.randomUUID(),reviewer,registrar,candidate,commandId,patientId,revision,fingerprint,now,now.plus(ttl),"NO_UNRESOLVED_MATCH",keyId,new byte[32]);
  return new ReviewDecisionProof(draft.tenantId(),draft.facilityId(),draft.reviewId(),draft.reviewerId(),draft.registrarId(),draft.candidateId(),draft.commandId(),draft.patientId(),draft.candidateRevision(),draft.fingerprint(),draft.decidedAt(),draft.expiresAt(),draft.outcome(),draft.keyId(),sign(draft));
 }
 public boolean verify(ReviewDecisionProof proof,Instant now){
  if(proof==null||now==null||!keyId.equals(proof.keyId())||now.isBefore(proof.decidedAt())||!now.isBefore(proof.expiresAt()))return false;
  return MessageDigest.isEqual(sign(proof),proof.mac());
 }
}
