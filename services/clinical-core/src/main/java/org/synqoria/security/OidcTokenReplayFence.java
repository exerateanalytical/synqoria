package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0031. Process-local bounded replay fence. NOT durable/distributed; cannot authorize clinical admission. */
public final class OidcTokenReplayFence {
 private final int capacity;private final Map<String,Instant> seen=new HashMap<>();private Instant lastNow=Instant.MIN;
 public OidcTokenReplayFence(int capacity){if(capacity<1||capacity>100000)throw new IllegalArgumentException("capacity");this.capacity=capacity;}
 public synchronized boolean firstUse(String issuer,String jti,Instant expiresAt,Instant now){
  if(now==null||expiresAt==null||issuer==null||jti==null||!issuer.matches("https://[A-Za-z0-9./_-]{5,512}")||!jti.matches("[A-Za-z0-9_-]{12,128}")||now.isBefore(lastNow)||!expiresAt.isAfter(now)||Duration.between(now,expiresAt).compareTo(Duration.ofMinutes(15))>0)return false;
  lastNow=now;
  seen.entrySet().removeIf(e->!e.getValue().isAfter(now));
  String key=issuer+"\u0000"+jti;
  if(seen.containsKey(key)||seen.size()>=capacity)return false;
  seen.put(key,expiresAt);return true;
 }
 public synchronized int size(){return seen.size();}
}
