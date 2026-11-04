package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0143: Exact-match persisted replay receipt and side-effect checksum verification. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxReplayReceiptIntegrity {
 private InboxReplayReceiptIntegrity(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Receipt(UUID eventId,long revision,String eventHash,String effectsHash,boolean committed){
  public Receipt{Objects.requireNonNull(eventId);require(revision>=1&&valid(eventHash)&&valid(effectsHash));}
 }
 private static boolean valid(String x){return x!=null&&x.matches("[0-9a-f]{64}");}
 public static void replayOnlyIfExact(Receipt r,UUID eventId,long revision,String hash,String expectedEffects){
  require(r!=null&&r.committed()&&r.eventId().equals(eventId)&&r.revision()==revision&&r.eventHash().equals(hash)&&r.effectsHash().equals(expectedEffects));
 }

}
