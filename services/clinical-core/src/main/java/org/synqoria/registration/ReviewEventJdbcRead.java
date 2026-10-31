package org.synqoria.registration;
import java.sql.*;import java.util.*;
/** P05-B0042. Restricted, bounded JDBC history read port. No public HTTP or DB role wired.
 * Caller must provide an independently verified transaction and PostgreSQL RLS policy. */
final class ReviewEventJdbcRead {
 private ReviewEventJdbcRead(){}
 static List<ReviewEventJournal.Entry> read(Connection c,UUID tenant,UUID facility,UUID review)throws SQLException{
  if(!ReviewTransactionFence.eligible(c)||tenant==null||facility==null||review==null)throw new SQLException("Review history unavailable");
  // v0.22.0 SQL contract is unapplied. Fail if digest column is not present.
  String sql="SELECT tenant_id,facility_id,review_id,command_id,event_version,event_kind,audit_binding_sha256,previous_digest_sha256,event_digest_sha256 "
   +"FROM patient_registration_review_events WHERE tenant_id=? AND facility_id=? AND review_id=? ORDER BY event_version ASC LIMIT 3";
  try(PreparedStatement ps=c.prepareStatement(sql)){
   ps.setQueryTimeout(5);ps.setObject(1,tenant);ps.setObject(2,facility);ps.setObject(3,review);
   ArrayList<ReviewEventJournal.Entry> rows=new ArrayList<>();
   try(ResultSet rs=ps.executeQuery()){
    while(rs.next()){
     if(rows.size()==2)throw new SQLException("Review history overflow");
     try{
      rows.add(new ReviewEventJournal.Entry((UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),
       (UUID)rs.getObject(4),rs.getLong(5),ReviewEventTransition.Kind.valueOf(rs.getString(6)),rs.getString(7),rs.getString(8),rs.getString(9)));
     }catch(RuntimeException e){throw new SQLException("Invalid review history",e);}
    }
   }
   if(!rows.isEmpty()&&ReviewHistoryRebuilder.rebuild(rows,tenant,facility,review).isEmpty())throw new SQLException("Review history integrity failure");
   return List.copyOf(rows);
  }
 }
}
