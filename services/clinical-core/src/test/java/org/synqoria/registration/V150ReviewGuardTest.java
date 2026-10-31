package org.synqoria.registration;
import java.time.*;import java.util.*;
/** 6 focused executable batches, no live OIDC or PostgreSQL and no approval to release mutations. */
public final class V150ReviewGuardTest {
 static int count=0;static void ok(boolean v){count++;if(!v)throw new AssertionError("assertion "+count);}
 static final UUID T=UUID.randomUUID(),F=UUID.randomUUID(),R=UUID.randomUUID(),A=UUID.randomUUID(),C=UUID.randomUUID(),CMD=UUID.randomUUID(),P=UUID.randomUUID(),REV=UUID.randomUUID();
 static final Instant NOW=Instant.parse("2026-10-30T02:00:00Z");
 static VerifiedReviewerSession session(){return new VerifiedReviewerSession(T,F,R,UUID.randomUUID(),Set.of("patient:identity-review:approve"),6,NOW.minusSeconds(60),NOW.plusSeconds(60),false);}
 static void b16(){
  ok(session().authorizes(T,F,A,6,NOW));ok(!session().authorizes(T,UUID.randomUUID(),A,6,NOW));
  ok(!session().authorizes(T,F,R,6,NOW));ok(!session().authorizes(T,F,A,7,NOW));
  ok(!new VerifiedReviewerSession(T,F,R,UUID.randomUUID(),Set.of(),6,NOW.minusSeconds(60),NOW.plusSeconds(60),false).authorizes(T,F,A,6,NOW));
  ok(!new VerifiedReviewerSession(T,F,R,UUID.randomUUID(),Set.of("patient:identity-review:approve"),6,NOW.minusSeconds(60),NOW.plusSeconds(60),true).authorizes(T,F,A,6,NOW));
 }
 static void b17(){
  ok(ReviewerSeparationFence.allow(session(),T,F,A,C,CMD,P,2,2,6,NOW));
  ok(!ReviewerSeparationFence.allow(session(),T,F,A,C,CMD,P,2,3,6,NOW));
  ok(!ReviewerSeparationFence.allow(session(),T,F,R,C,CMD,P,2,2,6,NOW));
  ok(!ReviewerSeparationFence.allow(session(),T,F,A,C,null,P,2,2,6,NOW));
 }
 static void b18(){
  var d=new ReviewRevocationFence(REV,2,3,false,NOW.plusSeconds(30),NOW.minusSeconds(2));
  ok(d.freshAndUnrevoked(REV,2,3,NOW));ok(!d.freshAndUnrevoked(REV,1,3,NOW));
  ok(!d.freshAndUnrevoked(REV,2,4,NOW));ok(!d.freshAndUnrevoked(REV,2,3,NOW.plusSeconds(31)));
  ok(!new ReviewRevocationFence(REV,2,3,true,NOW.plusSeconds(30),NOW.minusSeconds(2)).freshAndUnrevoked(REV,2,3,NOW));
  ok(!new ReviewRevocationFence(REV,2,3,false,NOW.plusSeconds(30),NOW.minusSeconds(16)).freshAndUnrevoked(REV,2,3,NOW));
 }
 static void b19(){
  String hash=ReviewAuditBinding.digest(T,F,REV,R,CMD,C,3,"REVIEW_APPROVED");
  ok(hash.length()==64);ok(hash.equals(ReviewAuditBinding.digest(T,F,REV,R,CMD,C,3,"REVIEW_APPROVED")));
  ok(!hash.equals(ReviewAuditBinding.digest(T,F,REV,R,CMD,C,4,"REVIEW_APPROVED")));
  ok(!hash.equals(ReviewAuditBinding.digest(T,F,REV,R,CMD,C,3,"REVIEW_REVOKED")));
  ok(!hash.contains(C.toString()));
 }
 static void b20(){
  for(boolean id:new boolean[]{true,false})for(boolean durable:new boolean[]{true,false})for(boolean rls:new boolean[]{true,false})for(boolean approved:new boolean[]{true,false})
   ok(ReviewApprovalReleaseFence.admission(id,durable,rls,approved)!=null);
  ok(ReviewApprovalReleaseFence.admission(true,true,true,true)==ReviewApprovalReleaseFence.Decision.LOCKED_PENDING_CLINICAL_SIGNOFF);
 }
 static void b21(){
  var d=new ReviewRevocationFence(REV,1,4,false,NOW.plusSeconds(20),NOW.minusSeconds(1));
  ok(RegistrationReviewPreflight.evaluate(session(),T,F,A,C,CMD,P,4,4,6,d,REV,1,NOW)==RegistrationReviewPreflight.Result.REVIEWED_NOT_RELEASED);
  ok(RegistrationReviewPreflight.evaluate(session(),T,F,R,C,CMD,P,4,4,6,d,REV,1,NOW)==RegistrationReviewPreflight.Result.DENIED_UNVERIFIED_REVIEWER);
  ok(RegistrationReviewPreflight.evaluate(session(),T,F,A,C,CMD,P,4,5,6,d,REV,1,NOW)==RegistrationReviewPreflight.Result.DENIED_UNVERIFIED_REVIEWER);
  ok(RegistrationReviewPreflight.evaluate(session(),T,F,A,C,CMD,P,4,4,6,d,REV,2,NOW)==RegistrationReviewPreflight.Result.DENIED_STALE_DECISION);
 }
 public static void main(String[]args){if(args.length!=1)throw new IllegalArgumentException("Specify batch");switch(args[0]){
 case "P05-B0016"->b16();case "P05-B0017"->b17();case "P05-B0018"->b18();case "P05-B0019"->b19();case "P05-B0020"->b20();case "P05-B0021"->b21();default->throw new IllegalArgumentException("Unknown batch");}
 System.out.println("PASS "+args[0]+" "+count+" assertions (local Java; no clinical release)");}
}
