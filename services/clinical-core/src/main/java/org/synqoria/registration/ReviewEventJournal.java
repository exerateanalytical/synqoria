package org.synqoria.registration;
import java.util.*;
/** P05-B0038. Synchronized, bounded, in-memory review event ledger for domain testing only.
 * NOT a durable decision authority; no registration writer consumes this ledger. */
public final class ReviewEventJournal {
 public record Entry(UUID tenant,UUID facility,UUID review,UUID command,long version,
    ReviewEventTransition.Kind kind,String binding,String previous,String digest) {
  public Entry { if(tenant==null||facility==null||review==null||command==null||kind==null||version<1
     ||binding==null||!binding.matches("[0-9a-f]{64}")||previous==null||!previous.matches("[0-9a-f]{64}")
     ||digest==null||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid review event"); }
 }
 private final UUID tenant,facility,review;
 private final ArrayList<Entry> entries=new ArrayList<>();
 private ReviewEventTransition.Snapshot state=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
 public ReviewEventJournal(UUID tenant,UUID facility,UUID review){
  if(tenant==null||facility==null||review==null)throw new IllegalArgumentException("Missing scope");
  this.tenant=tenant;this.facility=facility;this.review=review;
 }
 public synchronized Entry append(UUID incomingTenant,UUID incomingFacility,UUID incomingReview,UUID command,
   long expectedVersion,ReviewEventTransition.Kind kind,String binding){
  if(!tenant.equals(incomingTenant)||!facility.equals(incomingFacility)||!review.equals(incomingReview)
     ||command==null||binding==null||!binding.matches("[0-9a-f]{64}")||expectedVersion!=state.version())
   throw new IllegalArgumentException("Review event precondition failed");
  if(entries.size()>=2||entries.stream().anyMatch(e->e.command().equals(command)))throw new IllegalArgumentException("Duplicate or full review");
  var next=ReviewEventTransition.apply(state,expectedVersion+1,kind);
  String previous=entries.isEmpty()?ReviewAuditChain.genesis():entries.get(entries.size()-1).digest();
  String digest=ReviewAuditChain.append(previous,binding,next.version(),kind.name());
  var entry=new Entry(tenant,facility,review,command,next.version(),kind,binding,previous,digest);
  entries.add(entry);state=next;return entry;
 }
 public synchronized List<Entry> history(){return List.copyOf(entries);}
 public synchronized ReviewEventTransition.Snapshot snapshot(){return state;}
}
