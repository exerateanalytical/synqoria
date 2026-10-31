package org.synqoria.security;
import java.time.Instant;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public class TrustedSessionAdmissionTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args){
  var now=Instant.parse("2026-10-29T20:00:00Z");var actor=UUID.randomUUID();var tenant=UUID.randomUUID();var facility=UUID.randomUUID();
  var valid=new TrustedSessionAdmission.VerifiedIdentity("https://id.example.invalid","synqoria-clinical",actor,tenant,facility,now.minusSeconds(2),now.plusSeconds(120),true,false);
  var calls=new AtomicInteger();var token="synthetic_opaque_token";
  TrustedSessionAdmission.PrivilegeSource grant=c->{calls.incrementAndGet();return Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read"),Set.of(UUID.randomUUID()),true,true));};
  var service=new TrustedSessionAdmission(t->Optional.of(valid),grant,valid.issuer(),valid.audience());
  ok(service.admit(token,now).isPresent(),"verified identity admitted");
  ok(calls.get()==1,"privileges queried after validation");
  ok(service.admit("bad",now).isEmpty(),"short token denied");
  ok(service.admit(token,null).isEmpty(),"clock required");
  var otherIssuer=new TrustedSessionAdmission(t->Optional.of(new TrustedSessionAdmission.VerifiedIdentity("https://evil.invalid",valid.audience(),actor,tenant,facility,now,now.plusSeconds(20),true,false)),grant,valid.issuer(),valid.audience());
  ok(otherIssuer.admit(token,now).isEmpty(),"issuer pin");
  var otherAudience=new TrustedSessionAdmission(t->Optional.of(new TrustedSessionAdmission.VerifiedIdentity(valid.issuer(),"other",actor,tenant,facility,now,now.plusSeconds(20),true,false)),grant,valid.issuer(),valid.audience());
  ok(otherAudience.admit(token,now).isEmpty(),"audience pin");
  var noMfa=new TrustedSessionAdmission(t->Optional.of(new TrustedSessionAdmission.VerifiedIdentity(valid.issuer(),valid.audience(),actor,tenant,facility,now,now.plusSeconds(20),false,false)),grant,valid.issuer(),valid.audience());
  ok(noMfa.admit(token,now).isEmpty(),"MFA required");
  var revoked=new TrustedSessionAdmission(t->Optional.of(new TrustedSessionAdmission.VerifiedIdentity(valid.issuer(),valid.audience(),actor,tenant,facility,now,now.plusSeconds(20),true,true)),grant,valid.issuer(),valid.audience());
  ok(revoked.admit(token,now).isEmpty(),"revocation denied");
  ok(service.admit(token,now.plusSeconds(300)).isEmpty(),"expired denied");
  ok(service.admit(token,now.minusSeconds(30)).isEmpty(),"not-yet-issued denied");
  var noGrant=new TrustedSessionAdmission(t->Optional.of(valid),c->Optional.empty(),valid.issuer(),valid.audience());
  ok(noGrant.admit(token,now).isEmpty(),"missing privileges denied");
  var offShift=new TrustedSessionAdmission(t->Optional.of(valid),c->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read"),Set.of(),true,false)),valid.issuer(),valid.audience());
  ok(offShift.admit(token,now).isEmpty(),"off shift denied");
  var failure=new TrustedSessionAdmission(t->{throw new IllegalStateException("secret");},grant,valid.issuer(),valid.audience());
  ok(failure.admit(token,now).isEmpty(),"verifier failure denied");
  ok(calls.get()==1,"invalid identities never query grants");
  System.out.println("PASS: "+n+" session admission assertions (verifier stub, NO OIDC signature test)");
 }
}
