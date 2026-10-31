package org.synqoria.security;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** P03-B0024: construct a trusted, operator-pinned RSA JWK (not token-provided JWK). */
public final class OidcTrustedRsaJwk {
 private final String kid,thumbprint; private final RSAPublicKey key;
 public OidcTrustedRsaJwk(String kid, BigInteger modulus, BigInteger exponent, String expectedThumbprint){
  if(kid==null||!kid.matches("[A-Za-z0-9_-]{8,80}")||modulus==null||exponent==null||modulus.bitLength()<2048||modulus.bitLength()>4096||!exponent.equals(BigInteger.valueOf(65537)))throw new IllegalArgumentException("Invalid trusted RSA JWK");
  if(expectedThumbprint==null||!expectedThumbprint.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Missing operator-pinned thumbprint");
  try{
   String e=Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(exponent));
   String n=Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(modulus));
   String canonical="{\"e\":\""+e+"\",\"kty\":\"RSA\",\"n\":\""+n+"\"}";
   String digest=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
   if(!MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),expectedThumbprint.getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("Trusted key thumbprint mismatch");
   key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent));
   this.kid=kid;this.thumbprint=digest;
  }catch(GeneralSecurityException ex){throw new IllegalArgumentException("Invalid RSA public key",ex);}
 }
 private static byte[] unsigned(BigInteger v){byte[] a=v.toByteArray();return a.length>1&&a[0]==0?Arrays.copyOfRange(a,1,a.length):a;}
 public String kid(){return kid;} public String thumbprint(){return thumbprint;} public RSAPublicKey key(){return key;}
}
