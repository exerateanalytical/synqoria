package org.synqoria.registration;
import java.time.*;import java.util.*;
/** Strict, fail-closed freshness for reviewer event evidence. Not clinical authority. */
final class ReviewCommandFreshnessFence {
 private ReviewCommandFreshnessFence(){}
 static boolean valid(ReviewAtomicAppend.Request q,Instant now){
  if(q==null||now==null||q.at()==null||q.kind()==null||q.eventVersion()<1||q.candidateRevision()<0)return false;
  if(q.tenant()==null||q.facility()==null||q.review()==null||q.actor()==null||q.command()==null||q.candidate()==null||q.event()==null||q.audit()==null||q.outbox()==null)return false;
  if(new HashSet<>(List.of(q.event(),q.audit(),q.outbox(),q.command())).size()!=4)return false;
  if(q.at().isAfter(now.plusSeconds(30))||q.at().isBefore(now.minusSeconds(300)))return false;
  return q.binding()!=null&&q.binding().matches("[0-9a-f]{64}")&&q.previous()!=null&&q.previous().matches("[0-9a-f]{64}");
 }
}
