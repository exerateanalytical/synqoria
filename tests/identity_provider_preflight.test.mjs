import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync} from "node:crypto";
import {verifyTrustedIdentityProvider,IdentityProviderPreflightError}
 from "../apps/web/lib/identity-provider-preflight.mjs";
const issuer="https://identity.hospital.org/realms/main";
const jwk=generateKeyPairSync("rsa",{modulusLength:2048}).publicKey.export({format:"jwk"});
const pinned={...jwk,alg:"RS256",use:"sig",kid:"current-signing-key"};
const cfg={
 oidc:{issuer,authorizationEndpoint:issuer+"/protocol/openid-connect/auth"},
 token:{tokenEndpoint:issuer+"/protocol/openid-connect/token"},
 introspection:{introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect"},
 jwks:{keys:[pinned]}
};
const metadata=()=>({
 issuer,
 authorization_endpoint:cfg.oidc.authorizationEndpoint,
 token_endpoint:cfg.token.tokenEndpoint,
 introspection_endpoint:cfg.introspection.introspectionEndpoint,
 jwks_uri:issuer+"/protocol/openid-connect/certs",
 response_types_supported:["code"],code_challenge_methods_supported:["S256"],
 grant_types_supported:["authorization_code"]
});
const json=data=>new Response(JSON.stringify(data),{
 status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}
});
function harness({discovery=metadata(),keys=[pinned],brokenResponse=null}={}){
 const calls=[];
 const fetcher=async(url,options)=>{
  calls.push({url,options});
  if(brokenResponse)return brokenResponse;
  if(url===issuer+"/.well-known/openid-configuration")return json(discovery);
  if(url===issuer+"/protocol/openid-connect/certs")return json({keys});
  throw Error("untrusted URL");
 };
 return {fetcher,calls};
}
test("B0078 live OIDC discovery matches exact server issuer and endpoints",async()=>{
 const h=harness();
 const result=await verifyTrustedIdentityProvider(cfg,{fetcher:h.fetcher});
 assert.equal(result.verified,true);
 assert.equal(result.issuer,issuer);
 assert.equal(result.signingKeysVerified,1);
 assert.deepEqual(h.calls.map(x=>x.url),[
  issuer+"/.well-known/openid-configuration",
  issuer+"/protocol/openid-connect/certs"
 ]);
 for(const c of h.calls){
  assert.equal(c.options.redirect,"error");assert.equal(c.options.cache,"no-store");
  assert.equal(c.options.credentials,"omit");assert.equal(c.options.method,"GET");
  assert.ok(c.options.signal);
 }
});
test("B0078 untrusted issuer or endpoint mismatch never fetches signing keys",async()=>{
 for(const changed of [{issuer:"https://evil.hospital.org/realms/main"},
  {token_endpoint:"https://evil.hospital.org/token"},
  {jwks_uri:"https://evil.hospital.org/certs"},
  {authorization_endpoint:issuer+"/wrong"}]){
  const h=harness({discovery:{...metadata(),...changed}});
  await assert.rejects(()=>verifyTrustedIdentityProvider(cfg,{fetcher:h.fetcher}),
   IdentityProviderPreflightError);
  assert.equal(h.calls.length,1);
 }
});
test("B0079 reject missing PKCE S256, code flow and authorization_code grant",async()=>{
 for(const changed of [
  {code_challenge_methods_supported:["plain"]},
  {response_types_supported:["token"]},
  {grant_types_supported:["implicit"]},
  {response_types_supported:null}
 ]){
  const h=harness({discovery:{...metadata(),...changed}});
  await assert.rejects(()=>verifyTrustedIdentityProvider(cfg,{fetcher:h.fetcher}),
   IdentityProviderPreflightError);
  assert.equal(h.calls.length,1);
 }
});
test("B0080 pinned RSA material mismatch, missing key and duplicate kid deny startup",async()=>{
 const changed=[
  [{...pinned,n:"TAMPERED"}],[],
  [pinned,pinned],
  [{...pinned,kid:"replacement"}],
  [{...pinned,alg:"HS256"}],
  [{...pinned,use:"enc"}]
 ];
 for(const keys of changed){
  const h=harness({keys});
  await assert.rejects(()=>verifyTrustedIdentityProvider(cfg,{fetcher:h.fetcher}),
   IdentityProviderPreflightError);
  assert.equal(h.calls.length,2);
 }
});
test("B0081 redirects, HTML responses, unbounded JSON and IdP transport failure deny",async()=>{
 const bad=[
  new Response("redirect",{status:302,headers:{Location:"https://evil.org"}}),
  new Response("<html>bad</html>",{status:200,headers:{"Content-Type":"text/html"}}),
  new Response(JSON.stringify(metadata()),{status:200,headers:{
   "Content-Type":"application/json","Content-Length":"40000"}})
 ];
 for(const response of bad){
  const h=harness({brokenResponse:response});
  await assert.rejects(()=>verifyTrustedIdentityProvider(cfg,{fetcher:h.fetcher}),
   IdentityProviderPreflightError);
 }
 const badFetch=async()=>{throw Error("sensitive upstream network failure");};
 await assert.rejects(()=>verifyTrustedIdentityProvider(cfg,{fetcher:badFetch}),e=>{
  assert.equal(e.message,"identity_provider_preflight_denied");
  assert.doesNotMatch(e.message,/sensitive/);return true;
 });
});
