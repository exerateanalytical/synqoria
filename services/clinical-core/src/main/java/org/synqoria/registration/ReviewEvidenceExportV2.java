package org.synqoria.registration;
import java.time.Instant;
import java.util.*;
/** P05-B0047. Strict evidence export allowlist, never accepts free-form clinical payload. */
public final class ReviewEvidenceExportV2 {
 private ReviewEvidenceExportV2(){}
 public static Optional<Map<String,String>> export(List<ReviewScopedHistoryV2.Event> rows,UUID tenant,UUID facility,
   UUID review,Instant exportedAt){
  if(exportedAt==null||exportedAt.isAfter(Instant.now().plusSeconds(60)))return Optional.empty();
  return ReviewReceiptV2.from(rows,tenant,facility,review).map(r->{
   Map<String,String> m=new LinkedHashMap<>();m.put("schema",r.schema());m.put("version",Long.toString(r.version()));
   m.put("headDigest",r.headDigest());m.put("revoked",Boolean.toString(r.revoked()));
   m.put("exportedAt",exportedAt.toString());return Collections.unmodifiableMap(m);
  });
 }
}
