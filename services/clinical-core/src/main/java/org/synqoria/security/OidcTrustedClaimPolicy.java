package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0027: checks independently trusted, duplicate-free parsed claims. DOES NOT bind them to a JWS; not an admission API. */
public final class OidcTrustedClaimPolicy {
 public record Claims(String issuer,Set<String> audience,String authorizedParty,String subject,String sessionId,String tokenId,
                      Instant issuedAt,Instant notBefore,Instant expiresAt,Instant authenticatedAt){
  public Claims{audience=audience==null?Set.of():Set.copyOf(audience);}
 }
 private OidcTrustedClaimPolicy(){}
 public static boolean structurallyValid(Claims claims,OidcTrustPins pins,Instant now){
  if(claims==null||pins==null||now==null)return false;
  return pins.matches(claims.issuer(),claims.audience(),claims.authorizedParty())
   &&validOpaque(claims.subject(),8,128)&&validOpaque(claims.sessionId(),8,128)&&validOpaque(claims.tokenId(),12,128)
   &&OidcTemporalFence.current(claims.issuedAt(),claims.notBefore(),claims.expiresAt(),claims.authenticatedAt(),now);
 }
 private static boolean validOpaque(String s,int min,int max){return s!=null&&s.length()>=min&&s.length()<=max&&s.matches("[A-Za-z0-9_-]+");}
}
