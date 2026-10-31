package org.synqoria.registration;
import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
/** P05-B0040. Non-PHI integrity receipt, NOT an externally anchored audit log. */
public final class ReviewAuditExport {
 public record Receipt(int events,String terminalState,String chainHead,String receiptDigest){}
 private ReviewAuditExport(){}
 public static Optional<Receipt> receipt(List<ReviewEventJournal.Entry> events,UUID tenant,UUID facility,UUID review){
  var verified=ReviewHistoryRebuilder.rebuild(events,tenant,facility,review);
  if(verified.isEmpty())return Optional.empty();
  var v=verified.get();
  try{
   // Never include patient identifiers, review IDs, or command IDs in the portable receipt.
   String payload=String.join("\n","SYNQORIA-REVIEW-RECEIPT-v1",Integer.toString(events.size()),v.snapshot().state().name(),v.headDigest());
   String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
   return Optional.of(new Receipt(events.size(),v.snapshot().state().name(),v.headDigest(),digest));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
