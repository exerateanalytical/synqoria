package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0139: Scope and time-bound consent version evidence with fail-closed revocation check. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxConsentVersionFence {
 private InboxConsentVersionFence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Proof(UUID patient,UUID facility,long policyRevision,String purpose,Instant validUntil,boolean revoked){
  public Proof{Objects.requireNonNull(patient);Objects.requireNonNull(facility);Objects.requireNonNull(validUntil);require(policyRevision>=1&&purpose!=null&&purpose.matches("[a-z][a-z0-9_.-]{2,64}"));}
 }
 public static void verify(Proof proof,UUID patient,UUID facility,long requiredRevision,String requiredPurpose,Instant now){
  Objects.requireNonNull(proof);Objects.requireNonNull(now);
  require(!proof.revoked()&&proof.patient().equals(patient)&&proof.facility().equals(facility)&&proof.policyRevision()>=requiredRevision&&proof.purpose().equals(requiredPurpose)&&now.isBefore(proof.validUntil()));
 }

}
