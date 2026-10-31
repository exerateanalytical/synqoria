package org.synqoria.registration;
import java.util.*;
/** P05-B0045. Minimal non-PHI receipt only after verified V2 history. Not immutable custody. */
public final class ReviewReceiptV2 {
 public record Receipt(String schema,long version,String headDigest,boolean revoked){}
 private ReviewReceiptV2(){}
 public static Optional<Receipt> from(List<ReviewScopedHistoryV2.Event> rows,UUID tenant,UUID facility,UUID review){
  return ReviewScopedHistoryV2.verify(rows,tenant,facility,review).map(v->new Receipt("synqoria.review.receipt.v2",
   v.state().version(),v.head(),v.state().state()==ReviewEventTransition.State.REVOKED));
 }
}
