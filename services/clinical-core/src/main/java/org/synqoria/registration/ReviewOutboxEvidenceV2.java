package org.synqoria.registration;
import java.util.*;
/** P05-B0051: non-PHI outbox envelope; NO broker, durable commit or clinical release. */
public final class ReviewOutboxEvidenceV2 {
 public record Envelope(String schema,String eventType,UUID eventId,UUID tenant,UUID facility,UUID review,
   long version,String digest){ }
 private ReviewOutboxEvidenceV2(){}
 public static Optional<Envelope> from(List<ReviewScopedHistoryV2.Event> history,UUID tenant,UUID facility,UUID review,UUID eventId){
  if(eventId==null)return Optional.empty();
  var verified=ReviewScopedHistoryV2.verify(history,tenant,facility,review);
  if(verified.isEmpty())return Optional.empty();
  var last=history.get(history.size()-1);
  String kind=last.kind()==ReviewEventTransition.Kind.REVIEW_APPROVED?"review.approved.v2":"review.revoked.v2";
  return Optional.of(new Envelope("synqoria.review.outbox.v2",kind,eventId,tenant,facility,review,last.version(),verified.get().head()));
 }
}
