package org.synqoria.security;
import java.security.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Six individually invoked P03 batches; local cryptographic primitives only, not production OIDC. */
public final class V170IdentityFoundationTest {
 static int n=0;static void ok(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException e){n++;}}
 static final Instant NOW=Instant.parse("2026-10-30T03:00:00Z");
 static void b17()throws Exception{
  var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var pair=gen.generateKeyPair();
  byte[] message="synthetic.signing.input".getBytes(StandardCharsets.US_ASCII);
  var signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(message);byte[] sig=signer.sign();
  ok(OidcRs256Primitive.verify(message,sig,pair.getPublic()));
  ok(!OidcRs256Primitive.verify("modified".getBytes(),sig,pair.getPublic()));
  byte[] changed=sig.clone();changed[0]^=1;ok(!OidcRs256Primitive.verify(message,changed,pair.getPublic()));
  ok(!OidcRs256Primitive.verify(message,new byte[32],pair.getPublic()));
  var ec=KeyPairGenerator.getInstance("EC");ec.initialize(256);ok(!OidcRs256Primitive.verify(message,sig,ec.generateKeyPair().getPublic()));
  var weak=KeyPairGenerator.getInstance("RSA");weak.initialize(1024);ok(!OidcRs256Primitive.verify(message,sig,weak.generateKeyPair().getPublic()));
  ok(!OidcRs256Primitive.verify(message,sig,null));ok(!OidcRs256Primitive.verify(new byte[0],sig,pair.getPublic()));
 }
 static void b18(){
  String h=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
  String p=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8));
  String sig=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[256]);
  String token=h+"."+p+"."+sig;var result=OidcCompactEnvelope.parse(token);
  ok(result.isPresent());ok(Arrays.equals(result.get().signingInput(),(h+"."+p).getBytes(StandardCharsets.US_ASCII)));
  byte[] copy=result.get().payload();copy[0]=0;ok(result.get().payload()[0]!='\0');
  for(String bad:new String[]{token+"=",token+".extra","."+p+"."+sig,h+".."+sig,"a.b.c","hello",token.replace('.', '+')})ok(OidcCompactEnvelope.parse(bad).isEmpty());
 }
 static void b19(){
  var pins=new OidcTrustPins("https://identity.example.org/realms/synqoria","synqoria-clinical-api","synqoria-workforce");
  ok(pins.matches("https://identity.example.org/realms/synqoria",Set.of("synqoria-clinical-api"),"synqoria-workforce"));
  ok(!pins.matches("https://evil.example.org/realms/synqoria",Set.of("synqoria-clinical-api"),"synqoria-workforce"));
  ok(!pins.matches(pins.issuer(),Set.of("other-api"),pins.authorizedParty()));
  ok(!pins.matches(pins.issuer(),Set.of("synqoria-clinical-api"),"other-client"));
  bad(()->new OidcTrustPins("http://identity.example.org","audience","client"));
  bad(()->new OidcTrustPins("https://user@identity.example.org","audience","client"));
  bad(()->new OidcTrustPins("https://identity.example.org/?x=1","audience","client"));
 }
 static void b20(){
  ok(OidcTemporalFence.current(NOW.minusSeconds(60),NOW.minusSeconds(60),NOW.plusSeconds(60),NOW.minusSeconds(70),NOW));
  ok(!OidcTemporalFence.current(NOW.minusSeconds(3600),NOW.minusSeconds(3600),NOW.plusSeconds(3600),NOW.minusSeconds(3610),NOW));
  ok(!OidcTemporalFence.current(NOW.plusSeconds(40),NOW.plusSeconds(40),NOW.plusSeconds(300),NOW,NOW));
  ok(!OidcTemporalFence.current(NOW.minusSeconds(30),NOW.plusSeconds(40),NOW.plusSeconds(60),NOW.minusSeconds(40),NOW));
  ok(!OidcTemporalFence.current(NOW.minusSeconds(60),NOW.minusSeconds(60),NOW,NOW.minusSeconds(70),NOW));
  ok(!OidcTemporalFence.current(NOW.minusSeconds(60),NOW.minusSeconds(60),NOW.plusSeconds(60),NOW.minusSeconds(301),NOW));
  ok(!OidcTemporalFence.current(null,NOW,NOW,NOW,NOW));
 }
 static void b21(){
  var old=new OidcKeyRotationFence.KeyRecord("trustedKey0001",NOW.minusSeconds(3600),NOW.plusSeconds(10),false);
  var next=new OidcKeyRotationFence.KeyRecord("trustedKey0002",NOW.minusSeconds(10),NOW.plusSeconds(3600),false);
  var fence=new OidcKeyRotationFence(List.of(old,next));
  ok(fence.active("trustedKey0001",NOW));ok(fence.active("trustedKey0002",NOW));
  ok(!fence.active("token-supplied-kid",NOW));ok(!fence.active("trustedKey0001",NOW.plusSeconds(11)));
  var revoked=new OidcKeyRotationFence(List.of(new OidcKeyRotationFence.KeyRecord("trustedKey0001",NOW.minusSeconds(60),NOW.plusSeconds(60),true)));
  ok(!revoked.active("trustedKey0001",NOW));bad(()->new OidcKeyRotationFence(List.of(old,old)));
 }
 static void b22(){
  UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),actor=UUID.randomUUID(),session=UUID.randomUUID();
  var grant=new FacilitySessionGrantFence.Grant(tenant,facility,actor,session,Set.of("patient:identity-review:approve"),8,NOW.minusSeconds(1),NOW.plusSeconds(40),true,false);
  ok(FacilitySessionGrantFence.allows(grant,tenant,facility,actor,session,"patient:identity-review:approve",8,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,UUID.randomUUID(),facility,actor,session,"patient:identity-review:approve",8,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,tenant,UUID.randomUUID(),actor,session,"patient:identity-review:approve",8,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,tenant,facility,actor,UUID.randomUUID(),"patient:identity-review:approve",8,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,tenant,facility,actor,session,"patient:register",8,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,tenant,facility,actor,session,"patient:identity-review:approve",9,NOW));
  ok(!FacilitySessionGrantFence.allows(grant,tenant,facility,actor,session,"patient:identity-review:approve",8,NOW.plusSeconds(41)));
  var off=new FacilitySessionGrantFence.Grant(tenant,facility,actor,session,grant.capabilities(),8,NOW.minusSeconds(1),NOW.plusSeconds(40),false,false);
  ok(!FacilitySessionGrantFence.allows(off,tenant,facility,actor,session,"patient:identity-review:approve",8,NOW));
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("batch id required");
  switch(args[0]){case "P03-B0018"->b17();case "P03-B0019"->b18();case "P03-B0020"->b19();case "P03-B0021"->b20();case "P03-B0022"->b21();case "P03-B0023"->b22();default->throw new IllegalArgumentException("unknown batch");}
  System.out.println("PASS "+args[0]+" "+n+" assertions; production OIDC NOT CONNECTED");
 }
}
