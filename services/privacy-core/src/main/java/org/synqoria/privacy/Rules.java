package org.synqoria.privacy;

import java.time.Instant;
import java.util.*;

/** Invariant rules only. Patient identity, legal bases and grant issuance are external authorities. */
public final class Rules {
 private Rules(){}
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> permissions){
  public Actor {
   Objects.requireNonNull(id);Objects.requireNonNull(cell);Objects.requireNonNull(facility);
   permissions=Set.copyOf(Objects.requireNonNull(permissions));
  }
  public void require(String permission){
   if(!permissions.contains(permission))throw new Problem("permission_denied");
  }
 }
 public static final Set<String> PERMISSIONS=Set.of("m32:read","m32:directive_create",
  "m32:directive_verify","m32:directive_activate","m32:directive_revoke",
  "m32:disclosure_request","m32:disclosure_review","m32:policy_evaluate",
  "m32:case_create","m32:case_review");
 public static final class Problem extends RuntimeException {
  private final String code;
  public Problem(String code){super(code);this.code=code;}
  public String code(){return code;}
 }
 public record Directive(UUID subjectRef,String purpose,String recipient,String operation,
                         Instant effectiveAt,Instant expiresAt){
  public Directive{
   Objects.requireNonNull(subjectRef);
   purpose=code(purpose);recipient=code(recipient);operation=code(operation);
   Objects.requireNonNull(effectiveAt);Objects.requireNonNull(expiresAt);
   if(!expiresAt.isAfter(effectiveAt))throw new Problem("invalid_effective_window");
  }
 }
 public record Version(long expectedVersion){
  public Version{if(expectedVersion<1)throw new Problem("invalid_version");}
 }
 public record Verification(long expectedVersion,UUID evidenceId){
  public Verification{if(expectedVersion<1||evidenceId==null)throw new Problem("invalid_evidence");}
 }
 public record Disclosure(UUID subjectRef,UUID directiveId,String purpose,String recipient,String operation,String recordClass){
  public Disclosure{
   Objects.requireNonNull(subjectRef);Objects.requireNonNull(directiveId);
   purpose=code(purpose);recipient=code(recipient);operation=code(operation);
   recordClass=code(recordClass);
  }
 }
 public record Decision(long expectedVersion,boolean approve,String reason){
  public Decision{
   if(expectedVersion<1)throw new Problem("invalid_version");
   reason=code(reason);
  }
 }
 public record PrivacyCase(UUID subjectRef,String category,String reason){
  public PrivacyCase{
   Objects.requireNonNull(subjectRef);
   category=code(category);reason=code(reason);
   if(!Set.of("ACCESS","CORRECTION","COMPLAINT","INCIDENT").contains(category))
    throw new Problem("invalid_case_category");
  }
 }
 public static String code(String input){
  if(input==null||!input.matches("[A-Z][A-Z0-9_]{1,63}"))throw new Problem("invalid_code");
  return input;
 }
 public static void transition(String previous,String next){
  boolean permitted=switch(previous){
   case "DRAFT"->"VERIFIED".equals(next);
   case "VERIFIED"->"ACTIVE".equals(next);
   case "ACTIVE"->"REVOKED".equals(next);
   default->false;
  };
  if(!permitted)throw new Problem("invalid_transition");
 }
 public static boolean allowed(String status,Instant effective,Instant expiry,Instant now,boolean proofFresh){
  return proofFresh&&"ACTIVE".equals(status)&&!now.isBefore(effective)&&now.isBefore(expiry);
 }
}
