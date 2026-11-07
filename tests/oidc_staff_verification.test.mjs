import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {OidcVerificationError,verifyStaffOidcTokens} from "../apps/web/lib/oidc-staff-verification.mjs";

const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const publicJwk=publicKey.export({format:"jwk"});
const jwks={keys:[{...publicJwk,kid:"test-key",use:"sig",alg:"RS256"}]};
const issuer="https://identity.synqoria.org/realms/hospital",clientId="synqoria-staff",
 apiAudience="synqoria-clinical-core",nonce="h-random-server-owned-nonce-0123456789",now=1814980000;
const idClaims={iss:issuer,sub:"staff-directory-ref-001",aud:clientId,nonce,
  iat:now-10,exp:now+300,auth_time:now-20,amr:["pwd","otp"]};
const accessClaims={iss:issuer,sub:idClaims.sub,aud:apiAudience,client_id:clientId,
  iat:now-10,exp:now+300,scope:"openid patient:view facility:view"};
function jwt(claims,options={}){
 const header={alg:options.alg??"RS256",kid:options.kid??"test-key",typ:"JWT",...(options.extraHeader??{})};
 const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const signed=b64(header)+"."+b64(claims);
 return signed+"."+sign("sha256",Buffer.from(signed),options.privateKey??privateKey).toString("base64url");
}
const options=(id=idClaims,access=accessClaims,overrides={})=>({idToken:jwt(id),accessToken:jwt(access),jwks,issuer,clientId,apiAudience,nonce,nowSeconds:now,...overrides});
const rejects=o=>assert.throws(()=>verifyStaffOidcTokens(o),OidcVerificationError);

test("pinned RS256 JWKS and exact OIDC claims verify authentication only",()=>{
 const verified=verifyStaffOidcTokens(options());
 assert.deepEqual(verified,{subject:idClaims.sub,authenticatedAt:now-20,verifiedAt:now,
  methods:["pwd","otp"],accessScopes:["openid","patient:view","facility:view"]});
 assert.equal(Object.isFrozen(verified),true);
 assert.equal(Object.isFrozen(verified.methods),true);
 assert.equal("tenantId" in verified,false);
 assert.equal("bearer" in verified,false);
});
test("invalid signature, absent JWKS, duplicate kid and embedded JWK are denied",()=>{
 const altered=options();altered.idToken=altered.idToken.replace(/.$/,v=>v==="a"?"b":"a");rejects(altered);
 rejects(options(idClaims,accessClaims,{jwks:{keys:[]}}));
 rejects(options(idClaims,accessClaims,{jwks:{keys:[...jwks.keys,...jwks.keys]}}));
 const injected=options();injected.idToken=jwt(idClaims,{extraHeader:{jku:"https://attacker.example/jwks"}});rejects(injected);
 const none=options();none.idToken=jwt(idClaims,{alg:"none"});rejects(none);
});
test("wrong issuer, audience, nonce, subject and unauthorized client are denied",()=>{
 rejects(options({...idClaims,iss:"https://attacker.example"}));
 rejects(options({...idClaims,aud:"mobile-client"}));
 rejects(options({...idClaims,nonce:"different-server-owned-nonce-0123456789"}));
 rejects(options(idClaims,{...accessClaims,sub:"different-sub"}));
 rejects(options({...idClaims,azp:"attacker-client"}));
 rejects(options({...idClaims,aud:[clientId,"other-client"]}));
 rejects(options(idClaims,{...accessClaims,client_id:"other-client"}));
});
test("expired tokens, forged future timestamps, old auth and missing MFA denied",()=>{
 rejects(options({...idClaims,exp:now-1}));
 rejects(options(idClaims,{...accessClaims,exp:now-1}));
 rejects(options({...idClaims,iat:now+120}));
 rejects(options({...idClaims,auth_time:now-901}));
 rejects(options({...idClaims,amr:["pwd"]}));
 rejects(options({...idClaims,amr:["pwd","pwd"]}));
 rejects(options({...idClaims,amr:["pwd","otp"],nbf:now+120}));
});
test("invalid trusted configuration, token type and misleading scopes denied",()=>{
 rejects(options(idClaims,accessClaims,{issuer:"http://identity.synqoria.org/realms/hospital"}));
 rejects(options(idClaims,accessClaims,{nonce:"short"}));
 rejects(options(idClaims,accessClaims,{maxAuthenticationAgeSeconds:86400}));
 rejects(options(idClaims,{...accessClaims,scope:"patient:view patient:view"}));
 rejects(options(idClaims,{...accessClaims,scope:"admin:*"}));
 const truncated=options();truncated.accessToken="garbage";rejects(truncated);
});
test("pinned ES256 P-256 signing works and mismatched curve is denied",()=>{
 const ec=generateKeyPairSync("ec",{namedCurve:"prime256v1"});
 const ecJwk=ec.publicKey.export({format:"jwk"});
 const ecKeys={keys:[{...ecJwk,kid:"p256-key",alg:"ES256",use:"sig"}]};
 function ecJwt(claims){
  const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
  const data=b64({alg:"ES256",kid:"p256-key",typ:"JWT"})+"."+b64(claims);
  return data+"."+sign("sha256",Buffer.from(data),{key:ec.privateKey,dsaEncoding:"ieee-p1363"}).toString("base64url");
 }
 const valid={...options(),idToken:ecJwt(idClaims),accessToken:ecJwt(accessClaims),jwks:ecKeys};
 assert.equal(verifyStaffOidcTokens(valid).subject,idClaims.sub);
 rejects({...valid,jwks:{keys:[{...ecKeys.keys[0],crv:"P-384"}]}});
});
test("1024-bit RSA verification key rejected even with a valid signature",()=>{
 const weak=generateKeyPairSync("rsa",{modulusLength:1024});
 const publicWeak={...weak.publicKey.export({format:"jwk"}),kid:"test-key",alg:"RS256",use:"sig"};
 const one=options();
 const weakSigned={...one,idToken:jwt(idClaims,{privateKey:weak.privateKey}),accessToken:jwt(accessClaims,{privateKey:weak.privateKey}),jwks:{keys:[publicWeak]}};
 rejects(weakSigned);
});
