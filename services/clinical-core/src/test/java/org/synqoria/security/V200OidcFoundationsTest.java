package org.synqoria.security;
import java.time.*;import java.util.*;import java.security.*;import java.security.interfaces.*;import java.nio.charset.StandardCharsets;
/** Synthetic isolated checks, NOT production OIDC verification. */
public final class V200OidcFoundationsTest {
 static int n=0;static final Instant NOW=Instant.parse("2026-10-30T06:00:00Z");
 static void ok(boolean value){n++;if(!value)throw new AssertionError("assertion "+n);}
 static void bad(Runnable f){try{f.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException e){n++;}}
 static OidcTrustedRsaJwk key()throws Exception{
  var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pub=(RSAPublicKey)generator.generateKeyPair().getPublic();
  String modulus=Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getModulus().toByteArray()[0]==0?Arrays.copyOfRange(pub.getModulus().toByteArray(),1,pub.getModulus().toByteArray().length):pub.getModulus().toByteArray());
  String exponent=Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getPublicExponent().toByteArray());
  String canonical="{\"e\":\""+exponent+"\",\"kty\":\"RSA\",\"n\":\""+modulus+"\"}";
  String thumb=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
  return new OidcTrustedRsaJwk("operatorKey0001",pub.getModulus(),pub.getPublicExponent(),thumb);
 }
 static void b29(){
  var p=new OidcTrustPins("https://identity.example.org/realm","synqoria-api","synqoria-staff");
  var good=new OidcIssuerDiscoveryFence.Metadata(p.issuer(),"https://identity.example.org/realm/certs","https://identity.example.org/realm/auth","https://identity.example.org/realm/token");
  ok(OidcIssuerDiscoveryFence.valid(p,good));
  for(String bad:List.of("http://identity.example.org/realm/certs","https://evil.example.org/certs","https://identity.example.org/otherrealm/certs","https://identity.example.org:8443/certs","https://identity.example.org/realm/%2e%2e/certs","https://identity.example.org/realm/certs?redirect=evil","https://identity.example.org/realm/../certs","https://user@identity.example.org/certs","https://identity.example.org/certs#x"))ok(!OidcIssuerDiscoveryFence.valid(p,new OidcIssuerDiscoveryFence.Metadata(p.issuer(),bad,good.authorizationEndpoint(),good.tokenEndpoint())));
  ok(!OidcIssuerDiscoveryFence.valid(p,new OidcIssuerDiscoveryFence.Metadata("https://other.example.org",good.jwksUri(),good.authorizationEndpoint(),good.tokenEndpoint())));
 }
 static void b30()throws Exception{
  var k=key();var s=new OidcPinnedKeyset(List.of(k),NOW.minusSeconds(60),NOW.plusSeconds(600));
  ok(s.size()==1);ok(s.resolve(k.kid(),NOW,Set.of()).isPresent());ok(s.resolve(k.kid(),NOW,Set.of(k.kid())).isEmpty());ok(s.resolve("otherKid123",NOW,Set.of()).isEmpty());ok(s.resolve(k.kid(),NOW.plusSeconds(601),Set.of()).isEmpty());
  bad(()->new OidcPinnedKeyset(List.of(k,k),NOW,NOW.plusSeconds(60)));bad(()->new OidcPinnedKeyset(List.of(k),NOW,NOW.plusSeconds(90000)));
 }
 static void b31(){
  var r=new OidcTokenReplayFence(1);String iss="https://identity.example.org/realm";
  ok(r.firstUse(iss,"token_identifier_001",NOW.plusSeconds(60),NOW));ok(!r.firstUse(iss,"token_identifier_001",NOW.plusSeconds(60),NOW));
  ok(!r.firstUse(iss,"token_identifier_002",NOW.plusSeconds(60),NOW));ok(!r.firstUse(iss,"token_identifier_002",NOW.plusSeconds(60),NOW.minusSeconds(1)));
  ok(r.firstUse(iss,"token_identifier_002",NOW.plusSeconds(120),NOW.plusSeconds(61)));ok(r.size()==1);
  ok(!r.firstUse(iss,"token_identifier_003",NOW.plusSeconds(2000),NOW.plusSeconds(62)));
  bad(()->new OidcTokenReplayFence(0));
 }
 static void b32(){
  var pins=new OidcTrustPins("https://identity.example.org/realm","synqoria-api","synqoria-staff");
  var claims=new OidcTrustedClaimPolicy.Claims(pins.issuer(),Set.of(pins.audience()),pins.authorizedParty(),"staff_user_123","session_12345","token_123456789",NOW.minusSeconds(10),NOW.minusSeconds(10),NOW.plusSeconds(90),NOW.minusSeconds(15));
  var g=new OidcFacilitySessionFence.Grant(claims.subject(),claims.sessionId(),"facility_12345678","clinician","shift_12345678",NOW.plusSeconds(30),7,false);
  ok(OidcFacilitySessionFence.permits(claims,pins,g,g.facilityId(),g.role(),g.shiftId(),7,NOW));
  ok(!OidcFacilitySessionFence.permits(claims,pins,g,"facility_99999999",g.role(),g.shiftId(),7,NOW));
  ok(!OidcFacilitySessionFence.permits(claims,pins,g,g.facilityId(),"administrator",g.shiftId(),7,NOW));
  ok(!OidcFacilitySessionFence.permits(claims,pins,g,g.facilityId(),g.role(),g.shiftId(),8,NOW));
  ok(!OidcFacilitySessionFence.permits(claims,pins,g,g.facilityId(),g.role(),g.shiftId(),7,NOW.plusSeconds(31)));
  ok(!OidcFacilitySessionFence.permits(claims,pins,new OidcFacilitySessionFence.Grant(g.subject(),g.sessionId(),g.facilityId(),g.role(),g.shiftId(),g.expiresAt(),7,true),g.facilityId(),g.role(),g.shiftId(),7,NOW));
 }
 static void b33(){
  var f=new OidcSecurityBurstFence(1,2,Duration.ofMinutes(1));String a="A".repeat(43),b="B".repeat(43);
  ok(f.allow(a,NOW));ok(f.allow(a,NOW.plusSeconds(1)));ok(!f.allow(a,NOW.plusSeconds(2)));ok(!f.allow(b,NOW.plusSeconds(3)));
  ok(!f.allow(a,NOW.minusSeconds(1)));ok(f.allow(b,NOW.plusSeconds(61)));ok(!f.allow("session_secret_123",NOW.plusSeconds(62)));
  bad(()->new OidcSecurityBurstFence(0,2,Duration.ofSeconds(1)));
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Batch required");switch(args[0]){
   case "P03-B0029"->b29();case "P03-B0030"->b30();case "P03-B0031"->b31();case "P03-B0032"->b32();case "P03-B0033"->b33();default->throw new IllegalArgumentException("Unknown batch");
  }System.out.println("PASS "+args[0]+" "+n+" assertions; no production identity admission");
 }
}
