package org.synqoria.security;
import java.time.*;import java.util.*;
public final class SessionFreshnessPolicyTest {
 static int n=0;static void ok(boolean x){if(!x)throw new AssertionError("Session "+n);n++;}
 public static void main(String[]args){var t=UUID.randomUUID();var f=UUID.randomUUID();var a=UUID.randomUUID();var now=Instant.parse("2026-10-30T12:00:00Z");
  var ctx=new TrustedAccessContext(t,f,a,Set.of(),Set.of());
  var good=new SessionFreshnessPolicy.VerifiedSessionEvidence(t,f,a,now.minusSeconds(200),now.plusSeconds(100),now.minusSeconds(20),now.minusSeconds(5),8,true);
  var max=Duration.ofMinutes(5);var rev=Duration.ofSeconds(30);
  ok(SessionFreshnessPolicy.allows(ctx,good,now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,good,now,max,rev,9));
  ok(!SessionFreshnessPolicy.allows(ctx,good,now.plusSeconds(101),max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,new SessionFreshnessPolicy.VerifiedSessionEvidence(t,f,a,now.minusSeconds(200),now.plusSeconds(100),now.minusSeconds(20),now.minusSeconds(5),8,false),now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,new SessionFreshnessPolicy.VerifiedSessionEvidence(t,UUID.randomUUID(),a,now.minusSeconds(200),now.plusSeconds(100),now.minusSeconds(20),now.minusSeconds(5),8,true),now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,new SessionFreshnessPolicy.VerifiedSessionEvidence(t,f,a,now.minusSeconds(200),now.plusSeconds(100),now.minusSeconds(201),now.minusSeconds(5),8,true),now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,new SessionFreshnessPolicy.VerifiedSessionEvidence(t,f,a,now.minusSeconds(200),now.plusSeconds(100),now.minusSeconds(20),now.minusSeconds(45),8,true),now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,good,now,Duration.ZERO,rev,8));
  ok(!SessionFreshnessPolicy.allows(null,good,now,max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,good,now.minusSeconds(201),max,rev,8));
  ok(!SessionFreshnessPolicy.allows(ctx,good,now,max,rev,0));
  System.out.println("PASS: "+n+" externally verified session evidence policy assertions; OIDC still not integrated");
 }
}
