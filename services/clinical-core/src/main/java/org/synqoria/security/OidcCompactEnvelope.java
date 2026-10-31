package org.synqoria.security;
import java.util.*;
/** P03-B0019: strict canonical compact-JWS framing only; decoded bytes are UNTRUSTED until signature and claims are checked. */
public final class OidcCompactEnvelope {
 public record Parts(byte[] header,byte[] payload,byte[] signature,byte[] signingInput){
  public Parts {header=header.clone();payload=payload.clone();signature=signature.clone();signingInput=signingInput.clone();}
  @Override public byte[] header(){return header.clone();} @Override public byte[] payload(){return payload.clone();}
  @Override public byte[] signature(){return signature.clone();} @Override public byte[] signingInput(){return signingInput.clone();}
 }
 private OidcCompactEnvelope(){}
 public static Optional<Parts> parse(String token){
  if(token==null||token.length()<30||token.length()>8192||!token.matches("[A-Za-z0-9_.-]+"))return Optional.empty();
  String[] s=token.split("\\.",-1);if(s.length!=3||s[0].isEmpty()||s[1].isEmpty()||s[2].isEmpty())return Optional.empty();
  try{
   byte[][] decoded=new byte[3][];
   for(int i=0;i<3;i++){
    if(s[i].length()%4==1||!s[i].matches("[A-Za-z0-9_-]+"))return Optional.empty();
    decoded[i]=Base64.getUrlDecoder().decode(s[i]);
    if(!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded[i]).equals(s[i]))return Optional.empty();
   }
   if(decoded[0].length<2||decoded[0].length>2048||decoded[1].length<2||decoded[1].length>6144||decoded[2].length!=256&&decoded[2].length!=384&&decoded[2].length!=512)return Optional.empty();
   return Optional.of(new Parts(decoded[0],decoded[1],decoded[2],(s[0]+"."+s[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  }catch(IllegalArgumentException e){return Optional.empty();}
 }
}
