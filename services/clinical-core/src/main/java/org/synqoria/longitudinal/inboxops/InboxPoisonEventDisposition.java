package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0146: Poison, security and transient outcomes with no silent discard or bypass. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxPoisonEventDisposition {
 private InboxPoisonEventDisposition(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public enum Kind{TRANSIENT,SCHEMA_CONFLICT,AUTHORIZATION,INTEGRITY_FAILURE,UNKNOWN}
 public enum Action{RETRY_WITH_BACKOFF,QUARANTINE,HALT_CELL}
 public static Action decide(Kind k,int attempts){Objects.requireNonNull(k);require(attempts>=1&&attempts<=12);
  return switch(k){case TRANSIENT->attempts==12?Action.QUARANTINE:Action.RETRY_WITH_BACKOFF;
   case SCHEMA_CONFLICT->Action.QUARANTINE;
   case AUTHORIZATION,INTEGRITY_FAILURE,UNKNOWN->Action.HALT_CELL;};
 }

}
