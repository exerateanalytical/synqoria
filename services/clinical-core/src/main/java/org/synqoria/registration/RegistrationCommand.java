package org.synqoria.registration;
import java.util.*;import java.time.*;
/** P05-B0003. Command key is UUID, fingerprint is server-created keyed HMAC, not PII digest. */
public record RegistrationCommand(UUID patientId, UUID commandId, UUID correlationId,
  byte[] fingerprintHmac, RegistrationDraft draft, Instant requestedAt) {
 public RegistrationCommand {
  if(patientId==null||commandId==null||correlationId==null||draft==null||requestedAt==null||fingerprintHmac==null||fingerprintHmac.length!=32)throw new IllegalArgumentException("Invalid command");
  fingerprintHmac=fingerprintHmac.clone();
 }
 @Override public byte[] fingerprintHmac(){return fingerprintHmac.clone();}
}
