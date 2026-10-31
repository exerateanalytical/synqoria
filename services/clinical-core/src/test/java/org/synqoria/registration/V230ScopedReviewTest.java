package org.synqoria.registration;
import java.time.Instant;import java.util.*;
/** Synthetic test data only. Each case corresponds to one independently meaningful batch. */
public final class V230ScopedReviewTest {
 static int assertions=0;
 static void ok(boolean condition){assertions++;if(!condition)throw new AssertionError("Assertion "+assertions);}
 static void denied(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException|UnsupportedOperationException ex){rejected=true;}ok(rejected);}
 static final UUID T=UUID.fromString("00000000-0000-0000-0000-000000000101"),F=UUID.fromString("00000000-0000-0000-0000-000000000102"),R=UUID.fromString("00000000-0000-0000-0000-000000000103"),C=UUID.fromString("00000000-0000-0000-0000-000000000104"),C2=UUID.fromString("00000000-0000-0000-0000-000000000105"),OTHER=UUID.fromString("00000000-0000-0000-0000-000000000106");
 static final String B="a".repeat(64);
 static ReviewScopedHistoryV2.Event first(){String p=ReviewScopedDigestV2.genesis();return new ReviewScopedHistoryV2.Event(T,F,R,C,1,ReviewEventTransition.Kind.REVIEW_APPROVED,B,p,ReviewScopedDigestV2.digest(T,F,R,C,1,ReviewEventTransition.Kind.REVIEW_APPROVED,B,p));}
 static ReviewScopedHistoryV2.Event second(){var e=first();return new ReviewScopedHistoryV2.Event(T,F,R,C2,2,ReviewEventTransition.Kind.REVIEW_REVOKED,B,e.digest(),ReviewScopedDigestV2.digest(T,F,R,C2,2,ReviewEventTransition.Kind.REVIEW_REVOKED,B,e.digest()));}
 public static void main(String[] args){switch(args[0]){
 case "P05-B0043" -> {
  var e=first();ok(ReviewScopedDigestV2.equalDigest(e.digest(),ReviewScopedDigestV2.digest(T,F,R,C,1,e.kind(),B,e.previous())));
  ok(!ReviewScopedDigestV2.equalDigest(e.digest(),ReviewScopedDigestV2.digest(OTHER,F,R,C,1,e.kind(),B,e.previous())));
  ok(!ReviewScopedDigestV2.equalDigest(e.digest(),ReviewScopedDigestV2.digest(T,F,R,OTHER,1,e.kind(),B,e.previous())));
  denied(()->ReviewScopedDigestV2.digest(T,F,R,C,1,e.kind(),"bad",e.previous()));
  ok(!ReviewScopedDigestV2.equalDigest("bad",e.digest()));
 }
 case "P05-B0044" -> {
  var e=first();ok(ReviewScopedHistoryV2.verify(List.of(e),T,F,R).isPresent());
  ok(ReviewScopedHistoryV2.verify(List.of(e,second()),T,F,R).get().state().state()==ReviewEventTransition.State.REVOKED);
  ok(ReviewScopedHistoryV2.verify(List.of(e),OTHER,F,R).isEmpty());
  ok(ReviewScopedHistoryV2.verify(List.of(e,e),T,F,R).isEmpty());
  var bad=new ReviewScopedHistoryV2.Event(T,F,R,C,1,e.kind(),B,e.previous(),"f".repeat(64));
  ok(ReviewScopedHistoryV2.verify(List.of(bad),T,F,R).isEmpty());
  ok(ReviewScopedHistoryV2.verify(List.of(e,second(),second()),T,F,R).isEmpty());
 }
 case "P05-B0045" -> {
  var r=ReviewReceiptV2.from(List.of(first()),T,F,R).orElseThrow();
  ok(r.version()==1);ok(!r.revoked());ok(r.headDigest().equals(first().digest()));
  ok(ReviewReceiptV2.from(List.of(first(),second()),T,F,R).orElseThrow().revoked());
  ok(ReviewReceiptV2.from(List.of(first()),OTHER,F,R).isEmpty());
 }
 case "P05-B0046" -> {
  var rows=List.of(first());
  ok(ReviewCurrentStateFenceV2.inspect(rows,T,F,R,1,1,5,5,true)==ReviewCurrentStateFenceV2.Decision.PRECONDITIONS_ONLY);
  ok(ReviewCurrentStateFenceV2.inspect(rows,T,F,R,1,2,5,5,true)==ReviewCurrentStateFenceV2.Decision.DENIED);
  ok(ReviewCurrentStateFenceV2.inspect(rows,T,F,R,1,1,5,6,true)==ReviewCurrentStateFenceV2.Decision.DENIED);
  ok(ReviewCurrentStateFenceV2.inspect(rows,T,F,R,1,1,5,5,false)==ReviewCurrentStateFenceV2.Decision.DENIED);
  ok(ReviewCurrentStateFenceV2.inspect(List.of(first(),second()),T,F,R,2,2,5,5,true)==ReviewCurrentStateFenceV2.Decision.DENIED);
 }
 case "P05-B0047" -> {
  var result=ReviewEvidenceExportV2.export(List.of(first()),T,F,R,Instant.parse("2026-10-30T00:00:00Z")).orElseThrow();
  ok(result.keySet().equals(Set.of("schema","version","headDigest","revoked","exportedAt")));
  ok(!result.toString().contains(T.toString()));ok(!result.toString().contains(R.toString()));
  denied(()->result.put("patientName","synthetic"));
  ok(ReviewEvidenceExportV2.export(List.of(first()),OTHER,F,R,Instant.now()).isEmpty());
  ok(ReviewEvidenceExportV2.export(List.of(first()),T,F,R,Instant.now().plusSeconds(3600)).isEmpty());
 }
 default -> throw new IllegalArgumentException("Unknown batch "+args[0]);
 }System.out.println("PASS "+args[0]+" assertions="+assertions);}
}
