package org.synqoria.registration;
import java.sql.*;import java.util.*;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
/** Internal evidence reconciliation, read-only, under a SERIALIZABLE restricted transaction.
 * Does NOT grant a reviewer decision or establish PostgreSQL RLS. */
public final class ReviewEvidenceCrossCheck {
 private ReviewEvidenceCrossCheck(){}
 public static boolean consistent(Connection c,UUID tenant,UUID facility,UUID review,UUID event,UUID audit,UUID outbox,String digest)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||tenant==null||facility==null||review==null||event==null||audit==null||outbox==null||digest==null||!digest.matches("[a-f0-9]{64}"))return false;
  String sql="SELECT e.event_digest_sha256 FROM patient_registration_review_events e "
   +"JOIN patient_registration_review_audit a ON a.tenant_id=e.tenant_id AND a.facility_id=e.facility_id AND a.review_id=e.review_id AND a.review_event_id=e.event_id "
   +"JOIN clinical_outbox o ON o.tenant_id=e.tenant_id AND o.facility_id=e.facility_id AND o.event_id=a.outbox_event_id "
   +"WHERE e.tenant_id=? AND e.facility_id=? AND e.review_id=? AND e.event_id=? AND a.audit_event_id=? AND o.event_id=? "
   +"AND a.review_digest_sha256=e.event_digest_sha256 LIMIT 2";
  try(PreparedStatement p=c.prepareStatement(sql)){
   p.setQueryTimeout(5);p.setObject(1,tenant);p.setObject(2,facility);p.setObject(3,review);p.setObject(4,event);p.setObject(5,audit);p.setObject(6,outbox);
   try(ResultSet rows=p.executeQuery()){
    if(!rows.next())return false;
    String actual=rows.getString(1);
    boolean match=actual!=null&&MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),digest.getBytes(StandardCharsets.US_ASCII));
    return match&&!rows.next();
   }
  }
 }
}
