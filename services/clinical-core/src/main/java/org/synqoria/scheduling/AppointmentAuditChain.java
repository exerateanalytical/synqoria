package org.synqoria.scheduling;
import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
/** Tamper-evident synthetic audit chain reference; production requires append-only protected storage. */
public final class AppointmentAuditChain {
 public record Entry(long sequence,String previousDigest,String commandDigest,String actorPseudonym,String digest){
  public Entry{if(sequence<1||!hex(previousDigest)||!hex(commandDigest)||!hex(digest)||actorPseudonym==null||!actorPseudonym.matches("[A-Za-z0-9_-]{8,64}"))throw new IllegalArgumentException("invalid audit entry");}
 }
 public static String genesis(){return "0".repeat(64);}
 private static boolean hex(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
 public static Entry append(Entry prior,String commandDigest,String actorPseudonym){
  if(!hex(commandDigest)||actorPseudonym==null||!actorPseudonym.matches("[A-Za-z0-9_-]{8,64}"))throw new IllegalArgumentException("invalid audit input");
  long sequence=prior==null?1:Math.addExact(prior.sequence(),1);String previous=prior==null?genesis():prior.digest();
  return new Entry(sequence,previous,commandDigest,actorPseudonym,hash(sequence+":"+previous+":"+commandDigest+":"+actorPseudonym));
 }
 public static boolean verify(List<Entry> entries){
  if(entries==null)return false;Entry prior=null;
  for(Entry e:entries){if(e==null||e.sequence()!=(prior==null?1:prior.sequence()+1)||!MessageDigest.isEqual(e.previousDigest().getBytes(StandardCharsets.US_ASCII),(prior==null?genesis():prior.digest()).getBytes(StandardCharsets.US_ASCII))||!hash(e.sequence()+":"+e.previousDigest()+":"+e.commandDigest()+":"+e.actorPseudonym()).equals(e.digest()))return false;prior=e;}
  return true;
 }
 private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
