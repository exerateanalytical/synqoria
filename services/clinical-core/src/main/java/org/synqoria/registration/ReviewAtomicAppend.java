package org.synqoria.registration;
import java.sql.*;import java.time.*;import java.util.*;
/** P05-B0056: same-connection event+outbox+audit adapter, no commit and NO HTTP/clinical route. */
final class ReviewAtomicAppend {
 record Request(UUID tenant,UUID facility,UUID review,UUID event,UUID audit,UUID outbox,
                UUID actor,UUID command,UUID candidate,long candidateRevision,long eventVersion,
                ReviewEventTransition.Kind kind,String binding,String previous,Instant at){}
 private ReviewAtomicAppend(){}
 static String append(Connection c,Request q)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||q==null||q.event()==null||q.audit()==null||q.outbox()==null
    ||q.event().equals(q.audit())||q.event().equals(q.outbox())||q.audit().equals(q.outbox()))throw new SQLException("Review append unavailable");
  var plan=ReviewEventSqlPlanV2.prepare(q.tenant(),q.facility(),q.event(),q.review(),q.actor(),q.command(),q.candidate(),
       q.candidateRevision(),q.eventVersion(),q.kind(),q.binding(),q.previous(),q.at());
  try {
   try(PreparedStatement p=c.prepareStatement(plan.sql())){
    p.setQueryTimeout(5);for(int i=0;i<plan.params().size();i++)p.setObject(i+1,plan.params().get(i) instanceof Instant stamp?Timestamp.from(stamp):plan.params().get(i));requireOne(p.executeUpdate());
   }
   String outbox="INSERT INTO clinical_outbox (tenant_id,facility_id,event_id,aggregate_type,aggregate_id,aggregate_revision,event_type,schema_version,correlation_id,payload) VALUES (?,?,?,'registration_review',?,?,?,2,?,?::jsonb)";
   try(PreparedStatement p=c.prepareStatement(outbox)){
    p.setQueryTimeout(5);p.setObject(1,q.tenant());p.setObject(2,q.facility());p.setObject(3,q.outbox());p.setObject(4,q.review());
    p.setLong(5,q.eventVersion());p.setString(6,q.kind()==ReviewEventTransition.Kind.REVIEW_APPROVED?"review.approved.v2":"review.revoked.v2");
    p.setObject(7,q.command());p.setString(8,"{\"review_digest_sha256\":\""+plan.digest()+"\"}");requireOne(p.executeUpdate());
   }
   String audit="INSERT INTO patient_registration_review_audit (tenant_id,facility_id,audit_event_id,review_event_id,review_id,actor_id,outbox_event_id,review_digest_sha256,recorded_at) VALUES (?,?,?,?,?,?,?,?,?)";
   try(PreparedStatement p=c.prepareStatement(audit)){
    p.setQueryTimeout(5);p.setObject(1,q.tenant());p.setObject(2,q.facility());p.setObject(3,q.audit());p.setObject(4,q.event());
    p.setObject(5,q.review());p.setObject(6,q.actor());p.setObject(7,q.outbox());p.setString(8,plan.digest());p.setTimestamp(9,Timestamp.from(q.at()));requireOne(p.executeUpdate());
   }
   return plan.digest();
  }catch(SQLException|RuntimeException e){
   try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
   throw new SQLException("Atomic reviewer evidence append failed; transaction invalid",e);
  }
 }
 private static void requireOne(int affected)throws SQLException{if(affected!=1)throw new SQLException("Unexpected row count");}
}
