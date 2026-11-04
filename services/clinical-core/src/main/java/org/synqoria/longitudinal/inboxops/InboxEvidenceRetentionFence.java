package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0152: Legal hold and minimum retention guard for clinical event evidence purges. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxEvidenceRetentionFence {
 private InboxEvidenceRetentionFence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Record(Instant recordedAt,boolean legalHold,boolean clinicalInvestigation){public Record{Objects.requireNonNull(recordedAt);}}
 public static void mayPurge(Record item,Instant now,Duration retention){
  require(item!=null&&now!=null&&retention!=null&&!item.legalHold()&&!item.clinicalInvestigation()&&retention.compareTo(Duration.ofDays(30))>=0&&retention.compareTo(Duration.ofDays(3650))<=0&& !now.isBefore(item.recordedAt().plus(retention)));
 }

}
