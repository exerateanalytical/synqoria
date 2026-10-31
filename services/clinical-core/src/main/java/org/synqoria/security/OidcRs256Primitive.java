package org.synqoria.security;
import java.security.*;import java.security.interfaces.RSAPublicKey;import java.util.Objects;
/** P03-B0018: RSA signature primitive, not a JWT/OIDC verifier. Key trust and JSON claims must be established elsewhere. */
public final class OidcRs256Primitive {
 private OidcRs256Primitive(){}
 public static boolean verify(byte[] signingInput,byte[] signature,PublicKey key){
  if(signingInput==null||signature==null||!(key instanceof RSAPublicKey rsa)||signingInput.length<1||signingInput.length>8192)return false;
  int bits=rsa.getModulus().bitLength();
  if(bits<2048||bits>4096||!rsa.getPublicExponent().equals(java.math.BigInteger.valueOf(65537))||signature.length!=(bits+7)/8)return false;
  try{Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(rsa);verifier.update(signingInput);return verifier.verify(signature);}
  catch(GeneralSecurityException e){return false;}
 }
}
