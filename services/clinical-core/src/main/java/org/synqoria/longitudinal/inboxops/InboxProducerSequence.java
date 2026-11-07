package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0136: Sequence admission with independent per-producer last digest and replay conflict denial. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxProducerSequence {
 private InboxProducerSequence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public enum Outcome{NEXT,EXACT_REPLAY}
 public static Outcome classify(long committed,String committedDigest,long incoming,String incomingDigest){
  require(committed>=0&&incoming>0&&incoming<=100000000&&incomingDigest!=null&&incomingDigest.matches("[0-9a-f]{64}"));
  if(incoming==committed+1)return Outcome.NEXT;
  if(incoming==committed&&Objects.equals(committedDigest,incomingDigest))return Outcome.EXACT_REPLAY;
  throw new SecurityException("clinical event unavailable");
 }

}
