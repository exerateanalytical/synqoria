package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0155: Cardinality-bounded enum-only metrics; reject PHI and arbitrary labels. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxPrivacySafeTelemetry {
 private InboxPrivacySafeTelemetry(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public enum Operation{INBOX_APPLY,INBOX_REPLAY,INBOX_RETRY,INBOX_QUARANTINE,INBOX_DENY}
 public enum Outcome{SUCCESS,RETRY,REJECTED}
 public record Metric(String metric,Operation op,Outcome outcome,int count){
  public Metric{require(metric!=null&&metric.matches("clinical_inbox_(events|latency|failures)_total")&&op!=null&&outcome!=null&&count>=0&&count<=1000000);}
 }
 public static Metric record(String name,Operation op,Outcome outcome,int count){return new Metric(name,op,outcome,count);}

}
