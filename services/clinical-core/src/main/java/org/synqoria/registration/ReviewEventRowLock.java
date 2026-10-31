package org.synqoria.registration;
import java.sql.*;import java.time.*;import java.util.*;
/** P05-B0055: bounded scoped lock, NOT clinical approval or authorization. */
final class ReviewEventRowLock {
 private ReviewEventRowLock(){}
 static List<ReviewScopedHistoryV2.Event> lockAndRead(Connection c,UUID tenant,UUID facility,UUID review,UUID candidate,long revision,Instant now)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||tenant==null||facility==null||review==null||candidate==null||revision<0||now==null)
   throw new SQLException("Review lock unavailable");
  String sql="SELECT candidate_revision,expires_at,revoked_at FROM patient_registration_review_decisions WHERE tenant_id=? AND facility_id=? AND review_id=? AND candidate_id=? FOR UPDATE NOWAIT";
  try(PreparedStatement p=c.prepareStatement(sql)){
   p.setQueryTimeout(5);p.setObject(1,tenant);p.setObject(2,facility);p.setObject(3,review);p.setObject(4,candidate);
   try(ResultSet r=p.executeQuery()){
    if(!r.next()||r.getLong(1)!=revision)throw new SQLException("Review revision mismatch");
    Timestamp expires=r.getTimestamp(2);
    if(expires==null||!expires.toInstant().isAfter(now)||r.getTimestamp(3)!=null||r.next())throw new SQLException("Review expired or revoked");
   }
  }
  return ReviewEventJdbcReadV2.read(c,tenant,facility,review);
 }
}
