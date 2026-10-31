package org.synqoria.registration;
import java.sql.*;import java.util.*;
/** P05-B0048: V2-only bounded history reader. Caller must supply vetted RLS role and scoped SERIALIZABLE transaction. */
final class ReviewEventJdbcReadV2 {
 private ReviewEventJdbcReadV2(){}
 static List<ReviewScopedHistoryV2.Event> read(Connection c,UUID tenant,UUID facility,UUID review)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||tenant==null||facility==null||review==null)throw new SQLException("Review unavailable");
  String sql="SELECT tenant_id,facility_id,review_id,command_id,event_version,event_kind,audit_binding_sha256,previous_digest_sha256,event_digest_sha256 "
   +"FROM patient_registration_review_events WHERE tenant_id=? AND facility_id=? AND review_id=? ORDER BY event_version ASC LIMIT 3";
  try(PreparedStatement ps=c.prepareStatement(sql)){
   ps.setQueryTimeout(5);ps.setObject(1,tenant);ps.setObject(2,facility);ps.setObject(3,review);
   List<ReviewScopedHistoryV2.Event> events=new ArrayList<>();
   try(ResultSet rs=ps.executeQuery()){
    while(rs.next()){
     if(events.size()>=2)throw new SQLException("Review overflow");
     try{events.add(new ReviewScopedHistoryV2.Event((UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),
      (UUID)rs.getObject(4),rs.getLong(5),ReviewEventTransition.Kind.valueOf(rs.getString(6)),rs.getString(7),rs.getString(8),rs.getString(9)));}
     catch(RuntimeException e){throw new SQLException("Invalid review row",e);}
    }
   }
   if(!events.isEmpty()&&ReviewScopedHistoryV2.verify(events,tenant,facility,review).isEmpty())throw new SQLException("Review integrity failure");
   return List.copyOf(events);
  }
 }
}
