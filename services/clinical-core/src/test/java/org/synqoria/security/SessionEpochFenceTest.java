package org.synqoria.security;
import java.time.*;import java.util.*;
public final class SessionEpochFenceTest{
 static int n=0;static void ok(boolean x){if(!x)throw new AssertionError("fence "+n);n++;}
 public static void main(String[] args){
  var now=Instant.parse("2026-10-30T00:00:00Z");UUID a=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID();
  var identity=new TrustedSessionAdmission.VerifiedIdentity("issuer","audience",a,t,f,now.minusSeconds(10),now.plusSeconds(200),true,false);
  TrustedSessionAdmission.IdentityVerifier valid=token->Optional.of(identity);
  TrustedSessionAdmission.PrivilegeSource grants=id->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read"),Set.of(),true,true));
  var down=new TrustedSessionAdmission(valid,grants,"issuer","audience",SessionEpochFence.unavailable());
  ok(down.admit("EXAMPLE_MOCK_TOKEN__",now).isEmpty());
  var revoked=new TrustedSessionAdmission(valid,grants,"issuer","audience",(id,at)->false);
  ok(revoked.admit("EXAMPLE_MOCK_TOKEN__",now).isEmpty());
  var current=new TrustedSessionAdmission(valid,grants,"issuer","audience",(id,at)->id.actor().equals(a)&&at.equals(now));
  ok(current.admit("EXAMPLE_MOCK_TOKEN__",now).isPresent());ok(current.admit("EXAMPLE_MOCK_TOKEN__",now.plusSeconds(1)).isEmpty());
  var throwsFence=new TrustedSessionAdmission(valid,grants,"issuer","audience",(id,at)->{throw new IllegalStateException("revocation store down");});
  ok(throwsFence.admit("EXAMPLE_MOCK_TOKEN__",now).isEmpty());
  System.out.println("PASS P03-B0012: "+n+" session freshness fence assertions (test claims only)");
 }
}
