package org.synqoria.registration;
import java.util.*;
/** P05-B0049: SQL parameter plan, NOT an executable clinical authorization or deployment. */
final class ReviewEventSqlPlanV2 {
 static final String INSERT="INSERT INTO patient_registration_review_events (tenant_id,facility_id,event_id,review_id,event_version,event_kind,actor_id,command_id,candidate_id,candidate_revision,audit_binding_sha256,occurred_at,previous_digest_sha256,event_digest_sha256) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
 record Plan(String sql,List<Object> params,String digest){}
 private ReviewEventSqlPlanV2(){}
 static Plan prepare(UUID tenant,UUID facility,UUID event,UUID review,UUID actor,UUID command,UUID candidate,
  long candidateRevision,long version,ReviewEventTransition.Kind kind,String binding,String previous,java.time.Instant at){
  if(tenant==null||facility==null||event==null||review==null||actor==null||command==null||candidate==null||at==null||candidateRevision<0)
   throw new IllegalArgumentException("Missing reviewer event scope");
  String digest=ReviewScopedDigestV2.digest(tenant,facility,review,command,version,kind,binding,previous);
  return new Plan(INSERT,List.of(tenant,facility,event,review,version,kind.name(),actor,command,candidate,candidateRevision,binding,at,previous,digest),digest);
 }
}
