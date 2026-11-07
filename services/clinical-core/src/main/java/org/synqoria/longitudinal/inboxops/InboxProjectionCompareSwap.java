package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0144: Patient projection compare-and-swap proof guards stale revision and digest. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxProjectionCompareSwap {
 private InboxProjectionCompareSwap(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 private static boolean hex(String h){return h!=null&&h.matches("[0-9a-f]{64}");}
 public record State(UUID patient,long revision,String digest){public State{Objects.requireNonNull(patient);require(revision>=0&&revision<20000&&hex(digest));}}
 public static State update(State actual,UUID requestedPatient,long expectedRevision,String expectedDigest,String nextDigest){
  require(actual!=null&&actual.patient().equals(requestedPatient)&&actual.revision()==expectedRevision&&actual.digest().equals(expectedDigest)&&hex(nextDigest)&&!nextDigest.equals(expectedDigest));
  return new State(actual.patient(),actual.revision()+1,nextDigest);
 }

}
