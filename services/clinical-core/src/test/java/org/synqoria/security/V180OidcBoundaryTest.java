package org.synqoria.security;
import java.security.*;import java.security.interfaces.*;import java.math.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Five independently executed, synthetic-data security foundation checks; no production IdP/JWKS parser. */
public final class V180OidcBoundaryTest {
 static int n=0;static void ok(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("expected invalid input");}catch(IllegalArgumentException e){n++;}}
 static final Instant NOW=Instant.parse("2026-10-30T04:00:00Z");
 static KeyPair pair;static OidcTrustedRsaJwk trusted;
 static String thumb(RSAPublicKey key)throws Exception{
  byte[] e=key.getPublicExponent().toByteArray(),m=key.getModulus().toByteArray();
  if(m[0]==0)m=Arrays.copyOfRange(m,1,m.length);
  String en=Base64.getUrlEncoder().withoutPadding().encodeToString(e),mn=Base64.getUrlEncoder().withoutPadding().encodeToString(m);
  String s="{\"e\":\""+en+"\",\"kty\":\"RSA\",\"n\":\""+mn+"\"}";
  return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
 }
 static void setup()throws Exception{
  var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);pair=g.generateKeyPair();var pub=(RSAPublicKey)pair.getPublic();
  trusted=new OidcTrustedRsaJwk("operatorKey0001",pub.getModulus(),pub.getPublicExponent(),thumb(pub));
 }
 static void b24()throws Exception{
  ok(trusted.kid().equals("operatorKey0001"));ok(trusted.thumbprint().equals(thumb((RSAPublicKey)pair.getPublic())));
  ok(trusted.key().getModulus().equals(((RSAPublicKey)pair.getPublic()).getModulus()));
  var pub=(RSAPublicKey)pair.getPublic();
  bad(()->new OidcTrustedRsaJwk("operatorKey0001",pub.getModulus(),pub.getPublicExponent(),"A".repeat(43)));
  bad(()->new OidcTrustedRsaJwk("operatorKey0001",pub.getModulus(),BigInteger.valueOf(3),trusted.thumbprint()));
  bad(()->new OidcTrustedRsaJwk("x",pub.getModulus(),pub.getPublicExponent(),trusted.thumbprint()));
  bad(()->new OidcTrustedRsaJwk("operatorKey0001",BigInteger.valueOf(123),pub.getPublicExponent(),trusted.thumbprint()));
 }
 static void b25(){
  Map<String,Object> h=Map.of("alg","RS256","typ","at+jwt","kid","operatorKey0001");
  ok(OidcProtectedHeaderFence.allows(h,"operatorKey0001"));
  for(var change:List.of(Map.of("alg","none","typ","at+jwt","kid","operatorKey0001"),
   Map.of("alg","HS256","typ","at+jwt","kid","operatorKey0001"),
   Map.of("alg","RS256","typ","JWT","kid","operatorKey0001"),
   Map.of("alg","RS256","typ","at+jwt","kid","attackerKid0001"),
   Map.of("alg","RS256","typ","at+jwt","kid","operatorKey0001","jku","https://evil.test/key"),
   Map.of("alg","RS256","typ","at+jwt","kid","operatorKey0001","crit",List.of("b64"))))ok(!OidcProtectedHeaderFence.allows(change,"operatorKey0001"));
  ok(!OidcProtectedHeaderFence.allows(null,"operatorKey0001"));
 }
 static void b26()throws Exception{
  String header="{\"alg\":\"RS256\",\"typ\":\"at+jwt\",\"kid\":\"operatorKey0001\"}";
  String payload="{\"sub\":\"synthetic_patientless_user\"}";
  String signing=Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8))+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  var signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(signing.getBytes(StandardCharsets.US_ASCII));
  String token=signing+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
  var rotation=new OidcKeyRotationFence(List.of(new OidcKeyRotationFence.KeyRecord("operatorKey0001",NOW.minusSeconds(30),NOW.plusSeconds(30),false)));
  Map<String,Object> h=Map.of("alg","RS256","typ","at+jwt","kid","operatorKey0001");
  var result=OidcSignedPayload.verify(token,header.getBytes(StandardCharsets.UTF_8),h,trusted,rotation,NOW);
  ok(result.isPresent());ok(new String(result.get(),StandardCharsets.UTF_8).equals(payload));
  result.get()[0]=0;ok(OidcSignedPayload.verify(token,header.getBytes(StandardCharsets.UTF_8),h,trusted,rotation,NOW).isPresent());
  ok(OidcSignedPayload.verify(token,"{}".getBytes(),h,trusted,rotation,NOW).isEmpty());
  ok(OidcSignedPayload.verify(token,header.getBytes(),Map.of("alg","none","typ","at+jwt","kid","operatorKey0001"),trusted,rotation,NOW).isEmpty());
  ok(OidcSignedPayload.verify(token.replace('a','b'),header.getBytes(),h,trusted,rotation,NOW).isEmpty());
  ok(OidcSignedPayload.verify(token,header.getBytes(),h,trusted,rotation,NOW.plusSeconds(31)).isEmpty());
  var revoked=new OidcKeyRotationFence(List.of(new OidcKeyRotationFence.KeyRecord("operatorKey0001",NOW.minusSeconds(30),NOW.plusSeconds(30),true)));
  ok(OidcSignedPayload.verify(token,header.getBytes(),h,trusted,revoked,NOW).isEmpty());
 }
 static void b27(){
  var pins=new OidcTrustPins("https://id.example.org/realm","synqoria-api","synqoria-staff");
  var good=new OidcTrustedClaimPolicy.Claims(pins.issuer(),Set.of(pins.audience()),pins.authorizedParty(),"staff_user_123","session_12345","token_123456789",NOW.minusSeconds(30),NOW.minusSeconds(30),NOW.plusSeconds(120),NOW.minusSeconds(40));
  ok(OidcTrustedClaimPolicy.structurallyValid(good,pins,NOW));
  ok(!OidcTrustedClaimPolicy.structurallyValid(new OidcTrustedClaimPolicy.Claims("https://evil.example",good.audience(),good.authorizedParty(),good.subject(),good.sessionId(),good.tokenId(),good.issuedAt(),good.notBefore(),good.expiresAt(),good.authenticatedAt()),pins,NOW));
  ok(!OidcTrustedClaimPolicy.structurallyValid(new OidcTrustedClaimPolicy.Claims(good.issuer(),Set.of("other"),good.authorizedParty(),good.subject(),good.sessionId(),good.tokenId(),good.issuedAt(),good.notBefore(),good.expiresAt(),good.authenticatedAt()),pins,NOW));
  ok(!OidcTrustedClaimPolicy.structurallyValid(new OidcTrustedClaimPolicy.Claims(good.issuer(),good.audience(),good.authorizedParty(),"",good.sessionId(),good.tokenId(),good.issuedAt(),good.notBefore(),good.expiresAt(),good.authenticatedAt()),pins,NOW));
  ok(!OidcTrustedClaimPolicy.structurallyValid(new OidcTrustedClaimPolicy.Claims(good.issuer(),good.audience(),good.authorizedParty(),good.subject(),good.sessionId(),"bad token",good.issuedAt(),good.notBefore(),good.expiresAt(),good.authenticatedAt()),pins,NOW));
  ok(!OidcTrustedClaimPolicy.structurallyValid(good,pins,NOW.plusSeconds(121)));
  ok(!OidcTrustedClaimPolicy.structurallyValid(null,pins,NOW));
 }
 static void b28(){
  byte[] secret=new byte[32];Arrays.fill(secret,(byte)17);
  var event=OidcSecurityAuditRecord.denied("TOKEN_REJECTED",NOW,"session_secret_123",secret);
  ok(event.safeLine().contains("TOKEN_REJECTED"));ok(!event.safeLine().contains("session_secret_123"));
  ok(event.sessionReference().length()==43);
  ok(event.sessionReference().equals(OidcSecurityAuditRecord.denied("TOKEN_REJECTED",NOW,"session_secret_123",secret).sessionReference()));
  ok(!event.sessionReference().equals(OidcSecurityAuditRecord.denied("TOKEN_REJECTED",NOW,"session_secret_456",secret).sessionReference()));
  bad(()->OidcSecurityAuditRecord.denied("PATIENT_READ",NOW,"session_secret_123",secret));
  bad(()->OidcSecurityAuditRecord.denied("TOKEN_REJECTED",NOW,"session_secret_123",new byte[8]));
  bad(()->OidcSecurityAuditRecord.denied("TOKEN_REJECTED",NOW,"session with PHI",secret));
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Batch ID required");setup();
  switch(args[0]){case "P03-B0024"->b24();case "P03-B0025"->b25();case "P03-B0026"->b26();case "P03-B0027"->b27();case "P03-B0028"->b28();default->throw new IllegalArgumentException("Unknown batch");}
  System.out.println("PASS "+args[0]+" "+n+" assertions; clinical/OIDC admission remains LOCKED");
 }
}
