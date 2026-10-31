package org.synqoria.registration;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
/** P05-B0019. Domain-separated audit reference; not a substitute for an immutable persisted audit record. */
public final class ReviewAuditBinding {
 private ReviewAuditBinding(){}
 public static String digest(UUID tenant,UUID facility,UUID reviewId,UUID reviewer,UUID commandId,
  UUID candidate, long candidateRevision, String eventKind){
  if(tenant==null||facility==null||reviewId==null||reviewer==null||commandId==null||candidate==null
    ||candidateRevision<0||!"REVIEW_APPROVED".equals(eventKind)&&!"REVIEW_REVOKED".equals(eventKind))
   throw new IllegalArgumentException("Invalid review audit reference");
  String payload=String.join("\n","SYNQORIA-REVIEW-AUDIT-v1",tenant.toString(),facility.toString(),
   reviewId.toString(),reviewer.toString(),commandId.toString(),candidate.toString(),Long.toString(candidateRevision),eventKind);
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException("Audit reference unavailable",e);}
 }
}
