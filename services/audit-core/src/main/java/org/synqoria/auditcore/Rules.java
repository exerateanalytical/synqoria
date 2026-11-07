package org.synqoria.auditcore;

import java.time.Instant;
import java.util.*;

/** M34 metadata-only governance. Record content/PHI remains at its source. */
public final class Rules {
 private Rules(){}
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> permissions){
  public Actor{
   Objects.requireNonNull(id);Objects.requireNonNull(cell);Objects.requireNonNull(facility);
   permissions=Set.copyOf(Objects.requireNonNull(permissions));
  }
  public void require(String permission){
   if(!permissions.contains(permission))throw new Problem("permission_denied");
  }
 }
 public static final Set<String> PERMISSIONS=Set.of("m34:ingest","m34:search",
  "m34:read","m34:timeline","m34:export_request","m34:export_review",
  "m34:manifest_read","m34:hold_read","m34:retention_request","m34:retention_review");
 public static final class Problem extends RuntimeException{
  private final String code;
  public Problem(String c){super(c);code=c;}
  public String code(){return code;}
 }
 public static String code(String text){
  if(text==null||!text.matches("[A-Z][A-Z0-9_]{1,63}"))
   throw new Problem("invalid_code");
  return text;
 }
 public static String hex(String v){
  if(v==null||!v.matches("[0-9a-f]{64}"))throw new Problem("invalid_sha256");
  return v;
 }
 public static void step(String previous,String next){
  if(!"PROPOSED".equals(previous)||!Set.of("APPROVED","DENIED").contains(next))
   throw new Problem("invalid_transition");
 }
 public record Ingest(UUID streamId,UUID sourceId,UUID sourceEventId,
  UUID sourceActor,UUID subjectRef,long sequence,String action,String outcome,
  String payloadSha256,UUID proofId,Instant occurredAt){
  public Ingest {
   Objects.requireNonNull(streamId);Objects.requireNonNull(sourceId);
   Objects.requireNonNull(sourceEventId);Objects.requireNonNull(sourceActor);
   Objects.requireNonNull(proofId);Objects.requireNonNull(occurredAt);
   if(sequence<1)throw new Problem("invalid_sequence");
   action=code(action);outcome=code(outcome);payloadSha256=hex(payloadSha256);
   if(!Set.of("SUCCESS","DENIED","ERROR").contains(outcome))throw new Problem("invalid_outcome");
  }
 }
 public record ExportRequest(UUID streamId,String purpose,String recipient,long fromSequence,
                              long toSequence,Instant expiresAt){
  public ExportRequest{
   Objects.requireNonNull(streamId);purpose=code(purpose);recipient=code(recipient);
   Objects.requireNonNull(expiresAt);
   if(fromSequence<1||toSequence<fromSequence||toSequence-fromSequence>10000)
    throw new Problem("invalid_export_range");
  }
 }
 public record RetentionReview(UUID streamId,String policy,String reason){
  public RetentionReview{
   Objects.requireNonNull(streamId);policy=code(policy);reason=code(reason);
  }
 }
 public record Decision(long expectedVersion,boolean approve,String reason){
  public Decision{
   if(expectedVersion<1)throw new Problem("invalid_version");
   reason=code(reason);
  }
 }
}
