package org.synqoria.registration;
import java.util.*;
/** P05-B0044. Independent bounded V2 event verifier, not a durable authority. */
public final class ReviewScopedHistoryV2 {
 public record Event(UUID tenant,UUID facility,UUID review,UUID command,long version,
    ReviewEventTransition.Kind kind,String binding,String previous,String digest){}
 public record Verified(ReviewEventTransition.Snapshot state,String head){}
 private ReviewScopedHistoryV2(){}
 public static Optional<Verified> verify(List<Event> rows,UUID tenant,UUID facility,UUID review){
  if(rows==null||rows.isEmpty()||rows.size()>2||tenant==null||facility==null||review==null)return Optional.empty();
  var state=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
  String previous=ReviewScopedDigestV2.genesis();Set<UUID> commands=new HashSet<>();
  for(Event e:rows){
   if(e==null||!tenant.equals(e.tenant())||!facility.equals(e.facility())||!review.equals(e.review())
     ||e.command()==null||!commands.add(e.command())||e.version()!=state.version()+1
     ||!ReviewScopedDigestV2.equalDigest(previous,e.previous()))return Optional.empty();
   try{
    String computed=ReviewScopedDigestV2.digest(e.tenant(),e.facility(),e.review(),e.command(),e.version(),e.kind(),e.binding(),previous);
    if(!ReviewScopedDigestV2.equalDigest(computed,e.digest()))return Optional.empty();
    state=ReviewEventTransition.apply(state,e.version(),e.kind());previous=computed;
   }catch(IllegalArgumentException ex){return Optional.empty();}
  }
  return Optional.of(new Verified(state,previous));
 }
}
