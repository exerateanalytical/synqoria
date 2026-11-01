package org.synqoria.longitudinal;
import java.time.*;import java.util.Objects;
/** Only trusted error categories drive a retry; never log raw exceptions or patient messages. */
public final class LongitudinalInboxFailureClass {
 private LongitudinalInboxFailureClass() {}
 public enum Code {BROKER_TIMEOUT,BROKER_UNAVAILABLE,DB_CONFLICT,DB_UNAVAILABLE,
   AUTH_DENIED,SCHEMA_INVALID,SCOPE_MISMATCH,UNSUPPORTED_EVENT,UNKNOWN}
 public enum Decision {RETRY,QUARANTINE,STOP}
 public record Plan(Decision decision,Duration delay,String safeCode){
  public Plan{Objects.requireNonNull(decision);Objects.requireNonNull(delay);
   if(safeCode==null||!safeCode.matches("INBOX_[A-Z_]{2,40}"))throw new IllegalArgumentException("code");}
 }
 public static Plan plan(Code code,int attempt){
  Objects.requireNonNull(code);if(attempt<1||attempt>12)throw new IllegalArgumentException("attempt");
  return switch(code){
   case BROKER_TIMEOUT,BROKER_UNAVAILABLE,DB_CONFLICT,DB_UNAVAILABLE ->
    attempt==12?new Plan(Decision.QUARANTINE,Duration.ZERO,"INBOX_RETRY_EXHAUSTED"):
       new Plan(Decision.RETRY,Duration.ofSeconds(Math.min(300,1L<<Math.min(8,attempt-1))),"INBOX_TRANSIENT");
   case AUTH_DENIED,SCOPE_MISMATCH -> new Plan(Decision.STOP,Duration.ZERO,"INBOX_ACCESS_DENIED");
   case SCHEMA_INVALID,UNSUPPORTED_EVENT,UNKNOWN -> new Plan(Decision.QUARANTINE,Duration.ZERO,"INBOX_UNTRUSTED_EVENT");
  };
 }
}
