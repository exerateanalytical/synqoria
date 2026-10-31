package org.synqoria.registration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
/** P05-B0043. V2 digest binds all scope and command IDs; no clinical release authority. */
public final class ReviewScopedDigestV2 {
 private ReviewScopedDigestV2(){}
 public static String digest(UUID tenant,UUID facility,UUID review,UUID command,long version,
   ReviewEventTransition.Kind kind,String binding,String previous){
  if(tenant==null||facility==null||review==null||command==null||kind==null||version<1
    ||binding==null||!binding.matches("[0-9a-f]{64}")||previous==null||!previous.matches("[0-9a-f]{64}"))
   throw new IllegalArgumentException("Invalid scoped review digest input");
  String payload=String.join("\n","SYNQORIA-REVIEW-SCOPED-v2",tenant.toString(),facility.toString(),review.toString(),
    command.toString(),Long.toString(version),kind.name(),binding,previous);
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String genesis(){return "0".repeat(64);}
 public static boolean equalDigest(String expected,String actual){
  if(expected==null||actual==null||!expected.matches("[0-9a-f]{64}")||!actual.matches("[0-9a-f]{64}"))return false;
  return MessageDigest.isEqual(HexFormat.of().parseHex(expected),HexFormat.of().parseHex(actual));
 }
}
