package org.synqoria.registration;
import java.lang.reflect.Modifier;import java.time.*;import java.util.*;
/** Five separately executed Java batches; synthetic identities; no real IAM/PG. */
public final class V160ReviewSafetyTest {
 static int assertions=0;static void ok(boolean b){assertions++;if(!b)throw new AssertionError("Assertion "+assertions);}
 static void bad(Runnable fn){try{fn.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException expected){assertions++;}}
 static final UUID T=UUID.randomUUID(),F=UUID.randomUUID(),R=UUID.randomUUID(),A=UUID.randomUUID(),S=UUID.randomUUID(),REV=UUID.randomUUID();
 static final Instant NOW=Instant.parse("2026-10-30T03:00:00Z");
 static VerifiedReviewerSession session(){return new VerifiedReviewerSession(T,F,R,S,Set.of("patient:identity-review:approve"),8,NOW.minusSeconds(30),NOW.plusSeconds(60),false);}
 static void b22(){
  ok(!Modifier.isPublic(RegistrationJdbc.class.getModifiers()));
  for(boolean identity:new boolean[]{false,true})for(boolean db:new boolean[]{false,true})for(boolean clinical:new boolean[]{false,true})
   ok(RegistrationProductionBoundary.admission(identity,db,clinical)!=null);
  ok(RegistrationProductionBoundary.admission(true,true,true)!=RegistrationProductionBoundary.State.LOCKED_PENDING_TRUSTED_IAM);
 }
 static void b23(){
  var none=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
  var approved=ReviewEventTransition.apply(none,1,ReviewEventTransition.Kind.REVIEW_APPROVED);
  ok(approved.state()==ReviewEventTransition.State.APPROVED&&approved.version()==1);
  var revoked=ReviewEventTransition.apply(approved,2,ReviewEventTransition.Kind.REVIEW_REVOKED);
  ok(revoked.state()==ReviewEventTransition.State.REVOKED);
  bad(()->ReviewEventTransition.apply(none,2,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(approved,2,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(revoked,3,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(none,1,ReviewEventTransition.Kind.REVIEW_REVOKED));
 }
 static void b24(){
  var binding=ReviewAuditBinding.digest(T,F,REV,R,S,A,1,"REVIEW_APPROVED");
  var first=ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_APPROVED");
  ok(first.matches("[0-9a-f]{64}"));ok(!first.equals(ReviewAuditChain.genesis()));
  ok(first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_APPROVED")));
  ok(!first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,2,"REVIEW_APPROVED")));
  ok(!first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_REVOKED")));
  bad(()->ReviewAuditChain.append("bad",binding,1,"REVIEW_APPROVED"));
  bad(()->ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,0,"REVIEW_APPROVED"));
 }
 static void b25(){
  var grant=new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),false);
  ok(grant.matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,7,NOW.minusSeconds(1),false).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),true).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(16),false).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(UUID.randomUUID(),R,8,NOW.minusSeconds(1),false).matches(session(),NOW));
  ok(!grant.matches(session(),NOW.plusSeconds(61)));
 }
 static void b26(){
  var grant=new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),false);
  var decision=new ReviewRevocationFence(REV,2,3,false,NOW.plusSeconds(30),NOW.minusSeconds(1));
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,true,true,NOW)==ReviewReadinessReport.Status.REVIEWED_NOT_RELEASED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,false,true,NOW)==ReviewReadinessReport.Status.DATABASE_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,true,false,NOW)==ReviewReadinessReport.Status.DATABASE_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,4,8,true,true,NOW)==ReviewReadinessReport.Status.REVIEW_REVOKED_OR_STALE);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,R,REV,2,3,8,true,true,NOW)==ReviewReadinessReport.Status.IAM_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,9,true,true,NOW)==ReviewReadinessReport.Status.IAM_NOT_VERIFIED);
 }
 public static void main(String[]args){if(args.length!=1)throw new IllegalArgumentException("Batch required");switch(args[0]){
  case "P05-B0022"->b22();case "P05-B0023"->b23();case "P05-B0024"->b24();case "P05-B0025"->b25();case "P05-B0026"->b26();
  default->throw new IllegalArgumentException("Unknown batch");}
  System.out.println("PASS "+args[0]+" "+assertions+" assertions (Java unit; production write LOCKED)");
 }
}
