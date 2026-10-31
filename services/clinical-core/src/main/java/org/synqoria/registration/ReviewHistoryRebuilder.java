package org.synqoria.registration;
import java.util.*;
/** P05-B0039. Validates an untrusted review history before deriving any state.
 * Scope and command uniqueness are checked in addition to digest chaining. */
public final class ReviewHistoryRebuilder {
 public record Verified(ReviewEventTransition.Snapshot snapshot,String headDigest){}
 private ReviewHistoryRebuilder(){}
 public static Optional<Verified> rebuild(List<ReviewEventJournal.Entry> rows,UUID tenant,UUID facility,UUID review){
  if(rows==null||rows.isEmpty()||rows.size()>2||tenant==null||facility==null||review==null)return Optional.empty();
  String previous=ReviewAuditChain.genesis();var state=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
  Set<UUID> commands=new HashSet<>();
  for(var row:rows){
   if(row==null||!tenant.equals(row.tenant())||!facility.equals(row.facility())||!review.equals(row.review())
     ||!commands.add(row.command())||row.version()!=state.version()+1||!previous.equals(row.previous()))return Optional.empty();
   try{
    String digest=ReviewAuditChain.append(previous,row.binding(),row.version(),row.kind().name());
    if(!digest.equals(row.digest()))return Optional.empty();
    state=ReviewEventTransition.apply(state,row.version(),row.kind());previous=digest;
   }catch(IllegalArgumentException e){return Optional.empty();}
  }
  return Optional.of(new Verified(state,previous));
 }
}
