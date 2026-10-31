package org.synqoria.registration;
import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** P05-B0024. Domain-separated tamper-evident digest chain, NOT durable immutable audit custody. */
public final class ReviewAuditChain {
 private ReviewAuditChain(){}
 public static String append(String previousDigest,String bindingDigest,long eventVersion,String kind){
  if(eventVersion<1||bindingDigest==null||!bindingDigest.matches("[0-9a-f]{64}")
   ||previousDigest==null||!previousDigest.matches("[0-9a-f]{64}")
   ||!("REVIEW_APPROVED".equals(kind)||"REVIEW_REVOKED".equals(kind)))throw new IllegalArgumentException("Invalid chain event");
  try{
   String payload=String.join("\n","SYNQORIA-REVIEW-EVENT-CHAIN-v1",previousDigest,Long.toString(eventVersion),kind,bindingDigest);
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String genesis(){return "0".repeat(64);}
}
