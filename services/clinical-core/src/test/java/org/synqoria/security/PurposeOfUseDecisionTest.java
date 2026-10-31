package org.synqoria.security;
import java.time.Instant;import java.util.*;
public class PurposeOfUseDecisionTest {
 static int n;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var t=UUID.randomUUID();var a=UUID.randomUUID();var f=UUID.randomUUID();var p=UUID.randomUUID();var now=Instant.parse("2026-10-30T00:00:00Z");
  var context=new TrustedAccessContext(t,f,a,Set.of("patient:read"),Set.of(p));
  var good=new PurposeOfUseDecision.Evidence(t,a,p,"TREATMENT",Set.of("patient:read"),now.minusSeconds(1),now.plusSeconds(60),"audit_reference_123",true);
  yes(PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:read",good,now).allowed());
  yes(PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:read",good,now).auditRequired());
  yes(!PurposeOfUseDecision.evaluate(context,UUID.randomUUID(),"TREATMENT","patient:read",good,now).allowed());
  yes(!PurposeOfUseDecision.evaluate(context,p,"MARKETING","patient:read",good,now).allowed());
  yes(!PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:write",good,now).allowed());
  yes(!PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:read",good,now.plusSeconds(61)).allowed());
  var forged=new PurposeOfUseDecision.Evidence(t,a,p,"TREATMENT",Set.of("patient:read"),now,now.plusSeconds(20),"audit_reference_123",false);
  yes(!PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:read",forged,now).allowed());
  var longLived=new PurposeOfUseDecision.Evidence(t,a,p,"TREATMENT",Set.of("patient:read"),now,now.plusSeconds(3600),"audit_reference_123",true);
  yes(!PurposeOfUseDecision.evaluate(context,p,"TREATMENT","patient:read",longLived,now).allowed());
  System.out.println("PASS: "+n+" purpose assertions");
 }
}
