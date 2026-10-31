package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0033. Bounded per-pseudonym denial burst; fail closed on capacity/clock rollback. In-memory only. */
public final class OidcSecurityBurstFence {
 private final int maxSubjects,limit;private final Duration window;private final Map<String,Deque<Instant>> attempts=new HashMap<>();private Instant lastNow=Instant.MIN;
 public OidcSecurityBurstFence(int maxSubjects,int limit,Duration window){
  if(maxSubjects<1||maxSubjects>100000||limit<1||limit>1000||window==null||window.isNegative()||window.isZero()||window.compareTo(Duration.ofHours(1))>0)throw new IllegalArgumentException("limits");
  this.maxSubjects=maxSubjects;this.limit=limit;this.window=window;
 }
 public synchronized boolean allow(String pseudonymousRef,Instant now){
  if(now==null||now.isBefore(lastNow)||pseudonymousRef==null||!pseudonymousRef.matches("[A-Za-z0-9_-]{43}"))return false;
  lastNow=now;
  attempts.values().forEach(q->{while(!q.isEmpty()&&!q.peekFirst().plus(window).isAfter(now))q.removeFirst();});
  attempts.entrySet().removeIf(e->e.getValue().isEmpty());
  if(!attempts.containsKey(pseudonymousRef)&&attempts.size()>=maxSubjects)return false;
  Deque<Instant> q=attempts.computeIfAbsent(pseudonymousRef,k->new ArrayDeque<>());
  if(q.size()>=limit)return false;
  q.addLast(now);return true;
 }
}
