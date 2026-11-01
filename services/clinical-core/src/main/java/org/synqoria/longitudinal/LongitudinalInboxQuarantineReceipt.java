package org.synqoria.longitudinal;
import java.nio.charset.StandardCharsets;import java.security.*;import java.time.*;import java.util.*;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;
/** HMAC-bound quarantine metadata; no clinical content or raw incident details. */
public final class LongitudinalInboxQuarantineReceipt {
  private LongitudinalInboxQuarantineReceipt(){}
  public enum Reason {INVALID_SCHEMA,REPLAY_CONFLICT,RETRY_EXHAUSTED,BROKER_INTEGRITY}
  public record Receipt(UUID eventId,String eventDigest,Reason reason,Instant recordedAt,String mac) {
    public Receipt {Objects.requireNonNull(eventId);Objects.requireNonNull(reason);Objects.requireNonNull(recordedAt);
      if(eventDigest==null||!eventDigest.matches("[0-9a-f]{64}")||mac==null||!mac.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("invalid quarantine evidence");}
  }
  private static String sign(UUID event,String hash,Reason reason,Instant at,byte[] key){
    if(key==null||key.length<32)throw new SecurityException("unavailable quarantine key");
    try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));
      byte[] bytes=(event+"|"+hash+"|"+reason+"|"+at).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(m.doFinal(bytes));}
    catch(GeneralSecurityException e){throw new SecurityException("quarantine integrity unavailable");}
  }
  public static Receipt seal(UUID event,String hash,Reason reason,Instant at,byte[] key){
    Objects.requireNonNull(event);Objects.requireNonNull(reason);Objects.requireNonNull(at);
    if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("event digest");
    return new Receipt(event,hash,reason,at,sign(event,hash,reason,at,key));
  }
  public static void verify(Receipt receipt,byte[] key){
    Objects.requireNonNull(receipt);
    String computed=sign(receipt.eventId(),receipt.eventDigest(),receipt.reason(),receipt.recordedAt(),key);
    if(!MessageDigest.isEqual(HexFormat.of().parseHex(computed),HexFormat.of().parseHex(receipt.mac())))
      throw new SecurityException("quarantine integrity unavailable");
  }
}
