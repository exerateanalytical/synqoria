package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0030: minimal metadata-only outbox descriptor; excludes patient name/medical content. */
public record ReviewOutboxDescriptor(UUID tenant,UUID facility,UUID reviewId,UUID commandId,long version,Kind kind,
 Instant occurredAt,String auditDigest){
 public enum Kind { REVIEW_APPROVED, REVIEW_REVOKED }
 public ReviewOutboxDescriptor{
  if(tenant==null||facility==null||reviewId==null||commandId==null||version<1||kind==null||occurredAt==null
    ||auditDigest==null||!auditDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid outbox descriptor");
 }
 public String topic(){return "synqoria.review.decision.v1";}
 public String partitionKey(){return tenant+":"+reviewId;}
 public String eventType(){return kind.name();}
}
