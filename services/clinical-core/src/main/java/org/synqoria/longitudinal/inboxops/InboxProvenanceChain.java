package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0135: Deterministic per-cell audit provenance hash chain with forward revision fence. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxProvenanceChain {
 private InboxProvenanceChain(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 private static boolean hex(String h){return h!=null&&h.matches("[0-9a-f]{64}");}
 public static String next(UUID cell,long revision,String previous,String event){
  Objects.requireNonNull(cell);require(revision>=1&&revision<=20000&&hex(previous)&&hex(event));
  try{String s="SYNQORIA-AUDIT-v1|"+cell+"|"+revision+"|"+previous+"|"+event;
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static void verify(UUID cell,long priorRevision,long incomingRevision,String previous,String event,String expected){
  require(incomingRevision==priorRevision+1&&hex(expected));require(MessageDigest.isEqual(next(cell,incomingRevision,previous,event).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)));
 }

}
