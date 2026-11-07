package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0149: Immutable privacy-minimal SHA256 audit record linking actor, decision and event revision. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxAuditEvidenceChain {
 private InboxAuditEvidenceChain(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Evidence(String previous,String eventDigest,UUID actor,String outcome,long revision,Instant time){
  public Evidence{Objects.requireNonNull(actor);Objects.requireNonNull(time);require(hex(previous)&&hex(eventDigest)&&outcome!=null&&outcome.matches("(APPLIED|REPLAY|QUARANTINED|DENIED)")&&revision>=1);}
 }
 private static boolean hex(String h){return h!=null&&h.matches("[0-9a-f]{64}");}
 public static String digest(Evidence e){Objects.requireNonNull(e);
  String text="SYNQORIA-INBOX-AUDIT-v1|"+e.previous()+"|"+e.eventDigest()+"|"+e.actor()+"|"+e.outcome()+"|"+e.revision()+"|"+e.time();
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII)));}
  catch(NoSuchAlgorithmException x){throw new IllegalStateException(x);}
 }

}
