package org.synqoria.registration;
import java.sql.*;import java.util.*;
/** Advisory replay preflight only. Database uniqueness + SERIALIZABLE commit still required. */
public final class ReviewCommandReplayProbe {
 private ReviewCommandReplayProbe(){}
 public static boolean unseen(Connection c,UUID tenant,UUID facility,UUID command)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||tenant==null||facility==null||command==null)return false;
  String sql="SELECT 1 FROM patient_registration_review_events WHERE tenant_id=? AND facility_id=? AND command_id=? LIMIT 1";
  try(PreparedStatement p=c.prepareStatement(sql)){
   p.setQueryTimeout(5);p.setObject(1,tenant);p.setObject(2,facility);p.setObject(3,command);
   try(ResultSet r=p.executeQuery()){return !r.next();}
  }
 }
}
