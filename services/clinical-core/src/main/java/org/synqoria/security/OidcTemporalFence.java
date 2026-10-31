package org.synqoria.security;
import java.time.*;
/** P03-B0021: time policy for already signature-verified claims; not a token validator. */
public final class OidcTemporalFence {
 private OidcTemporalFence(){}
 public static boolean current(Instant issuedAt,Instant notBefore,Instant expiresAt,Instant authenticatedAt,Instant now){
  if(issuedAt==null||notBefore==null||expiresAt==null||authenticatedAt==null||now==null)return false;
  if(expiresAt.isBefore(issuedAt)||!expiresAt.isAfter(issuedAt)||expiresAt.isAfter(issuedAt.plusSeconds(3600)))return false;
  if(notBefore.isAfter(expiresAt)||authenticatedAt.isAfter(issuedAt)||authenticatedAt.isBefore(issuedAt.minusSeconds(300)))return false;
  if(authenticatedAt.isAfter(now)||issuedAt.isAfter(now.plusSeconds(30))||notBefore.isAfter(now.plusSeconds(30))||!now.isBefore(expiresAt))return false;
  return !authenticatedAt.isBefore(now.minusSeconds(300));
 }
}
