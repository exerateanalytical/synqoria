package org.synqoria.registration;
import java.util.*;
/** Idempotency comparison. Never return a prior patient ID on uncertain DB state. */
public final class RegistrationReplayDecision {
 public enum Decision{NEW_COMMAND,SAFE_REPLAY,CONFLICT,UNAVAILABLE}
 public record Claim(UUID tenantId,UUID facilityId,UUID commandId,UUID patientId,byte[] fingerprint,boolean committed){
  public Claim{if(tenantId==null||facilityId==null||commandId==null||patientId==null||fingerprint==null||fingerprint.length!=32)throw new IllegalArgumentException("Invalid claim");fingerprint=fingerprint.clone();}
  @Override public byte[] fingerprint(){return fingerprint.clone();}
 }
 private RegistrationReplayDecision(){}
 public static Decision decide(Claim existing,UUID tenant,UUID facility,RegistrationCommand incoming,boolean authoritativeRead){
  if(!authoritativeRead||tenant==null||facility==null||incoming==null)return Decision.UNAVAILABLE;
  if(existing==null)return Decision.NEW_COMMAND;
  if(!existing.tenantId().equals(tenant)||!existing.facilityId().equals(facility)||!existing.commandId().equals(incoming.commandId()))return Decision.CONFLICT;
  if(!RegistrationFingerprint.same(existing.fingerprint(),incoming.fingerprintHmac())||!existing.patientId().equals(incoming.patientId()))return Decision.CONFLICT;
  return existing.committed()?Decision.SAFE_REPLAY:Decision.UNAVAILABLE;
 }
}
