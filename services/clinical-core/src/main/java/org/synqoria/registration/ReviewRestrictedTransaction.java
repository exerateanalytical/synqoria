package org.synqoria.registration;
import java.sql.*;import java.time.*;import java.util.*;
/** P05-B0057: internal transaction integration adapter. NOT wired to a production route.
 * Caller must supply independently verified OIDC/grant/clinical signoffs before ever invoking in production.
 * This adapter does not commit; its return is NOT registration authorization. */
final class ReviewRestrictedTransaction {
 private ReviewRestrictedTransaction(){}
 static String execute(Connection c,String pinnedDbRole,ReviewAtomicAppend.Request q,Instant now)throws SQLException {
  try {
   if(q==null||now==null||!ReviewCommandFreshnessFence.valid(q,now)||!ReviewRuntimePrivilegeFence.verify(c,pinnedDbRole))throw new SQLException("Restricted reviewer DB unavailable");
   List<ReviewScopedHistoryV2.Event> history=ReviewEventRowLock.lockAndRead(c,q.tenant(),q.facility(),q.review(),q.candidate(),q.candidateRevision(),now);
   var snapshot=history.isEmpty()?new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0):
      ReviewScopedHistoryV2.verify(history,q.tenant(),q.facility(),q.review()).orElseThrow(()->new SQLException("Corrupt review history")).state();
   if(q.eventVersion()!=snapshot.version()+1)throw new SQLException("Stale review version");
   try{ReviewEventTransition.apply(snapshot,q.eventVersion(),q.kind());}
   catch(IllegalArgumentException e){throw new SQLException("Illegal review transition",e);}
   String previous=history.isEmpty()?ReviewScopedDigestV2.genesis():history.get(history.size()-1).digest();
   if(!ReviewScopedDigestV2.equalDigest(previous,q.previous()))throw new SQLException("Stale review digest");
   if(history.stream().anyMatch(e->e.command().equals(q.command())))throw new SQLException("Replayed command");
   return ReviewAtomicAppend.append(c,q);
  }catch(SQLException|RuntimeException e){
   try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
   throw new SQLException("Review transaction denied; rollback requested",e);
  }
 }
}
