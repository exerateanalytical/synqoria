package org.synqoria.security;
import java.time.*;import java.util.*;import java.security.MessageDigest;
/** P03-B0026: verifies bytes and returns ONLY signed payload bytes, never an authenticated principal. */
public final class OidcSignedPayload {
 private OidcSignedPayload(){}
 public static Optional<byte[]> verify(String token,byte[] externallyParsedHeaderBytes,Map<String,Object> parsedHeader,
                                       OidcTrustedRsaJwk trustedKey,OidcKeyRotationFence rotation,Instant now){
  if(externallyParsedHeaderBytes==null||trustedKey==null||rotation==null||now==null||!rotation.active(trustedKey.kid(),now)||!OidcProtectedHeaderFence.allows(parsedHeader,trustedKey.kid()))return Optional.empty();
  var parts=OidcCompactEnvelope.parse(token);
  if(parts.isEmpty()||!MessageDigest.isEqual(parts.get().header(),externallyParsedHeaderBytes))return Optional.empty();
  if(!OidcRs256Primitive.verify(parts.get().signingInput(),parts.get().signature(),trustedKey.key()))return Optional.empty();
  return Optional.of(parts.get().payload());
 }
}
