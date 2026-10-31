package org.synqoria.registration;
import java.util.*;
/** P05-B0052: bounded non-PHI disclosure receipt, no patient/candidate identifiers or approval grant. */
public final class ReviewDisclosureReceiptV2 {
 public record SafeReceipt(String schema,String status,long revision,String integrityHead){ }
 private ReviewDisclosureReceiptV2(){}
 public static Optional<SafeReceipt> from(List<ReviewScopedHistoryV2.Event> events,UUID tenant,UUID facility,UUID review){
  return ReviewScopedHistoryV2.verify(events,tenant,facility,review).map(v->new SafeReceipt("synqoria.review.status.v2",
   v.state().state()==ReviewEventTransition.State.REVOKED?"REVOKED":"REVIEWED_NOT_RELEASED",v.state().version(),v.head()));
 }
}
