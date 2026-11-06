import test from "node:test";
import assert from "node:assert/strict";
import {createRestrictedIdentityTransport,IdentityTransportError}
 from "../apps/web/lib/identity-restricted-transport.mjs";
const issuer="https://id.hospital.org/realms/a",secret="VaultSecret"+"x".repeat(50);
const config={
 oidc:{issuer,clientId:"synqoria-staff"},
 token:{tokenEndpoint:issuer+"/protocol/openid-connect/token",clientId:"synqoria-staff",redirectUri:"https://staff.hospital.org/api/auth/oidc/callback"},
 introspection:{introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect",
  clientId:"synqoria-staff",clientSecret:secret},
 workforce:{workforceOrigin:"https://staffing.hospital.org/",issuer}
};
const jwt="eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzdGFmZiJ9."+"A".repeat(43);
const urls=[
 config.token.tokenEndpoint,config.introspection.introspectionEndpoint,
 "https://staffing.hospital.org/internal/v1/staff/grants/active"
];
const basic="Basic "+Buffer.from("synqoria-staff:"+secret,"ascii").toString("base64");
const valid=()=>{
 const base={method:"POST",redirect:"error",cache:"no-store",
  credentials:"omit",signal:new AbortController().signal};
 return [
  {...base,headers:{Accept:"application/json","Content-Type":"application/x-www-form-urlencoded"},
   body:new URLSearchParams({grant_type:"authorization_code",client_id:"synqoria-staff",
    code:"OpaqueCode0123456",redirect_uri:config.token.redirectUri,code_verifier:"A".repeat(64)}).toString()},
  {...base,headers:{Accept:"application/json","Content-Type":"application/x-www-form-urlencoded",
    Authorization:basic},
   body:new URLSearchParams({token:jwt,token_type_hint:"access_token"}).toString()},
  {...base,headers:{Accept:"application/json","Content-Type":"application/json",
    Authorization:"Bearer "+jwt},
   body:JSON.stringify({issuer,subject:"staff-001"})}
 ];
};
const safe=()=>new Response("{}",{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
test("B0082-85 token exchange, introspection and workforce have distinct protocol credentials",async()=>{
 const calls=[],adapter=createRestrictedIdentityTransport(config,{fetcher:async(url,opts)=>{
  calls.push({url,opts});return safe();
 }});
 const examples=valid();
 for(let i=0;i<3;i++)assert.equal((await adapter(urls[i],examples[i])).status,200);
 assert.deepEqual(calls.map(x=>x.url),urls);
 assert.equal(calls[0].opts.headers.Authorization,undefined);
 assert.equal(calls[1].opts.headers.Authorization,basic);
 assert.equal(calls[2].opts.headers.Authorization,"Bearer "+jwt);
});
test("B0082 exchange rejects altered PKCE proof or client and unauthorized Basic/Bearer headers",async()=>{
 let reached=0,adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{reached++;return safe();}});
 const original=valid()[0];
 const p=new URLSearchParams(original.body);p.set("grant_type","password");
 const q=new URLSearchParams(original.body);q.set("code_verifier","x");
 const k=new URLSearchParams(original.body);k.append("code","duplicate");
 for(const opts of [
  {...original,body:p.toString()},{...original,body:q.toString()},
  {...original,body:k.toString()},
  {...original,headers:{...original.headers,Authorization:basic}},
  {...original,headers:{...original.headers,Authorization:"Bearer "+jwt}}
 ]){
  await assert.rejects(()=>adapter(urls[0],opts),IdentityTransportError);
 }
 assert.equal(reached,0);
});
test("B0083 introspection rejects absent, forged or leaked confidential client credential",async()=>{
 let reached=0,adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{reached++;return safe();}});
 const original=valid()[1];
 for(const opts of [
  {...original,headers:{...original.headers,Authorization:"Basic invalid"}},
  {...original,headers:{Accept:"application/json","Content-Type":"application/x-www-form-urlencoded"}},
  {...original,headers:{...original.headers,Authorization:"Bearer "+jwt}},
  {...original,body:"token="+jwt+"&token_type_hint=refresh_token"}
 ]){
  await assert.rejects(()=>adapter(urls[1],opts),IdentityTransportError);
 }
 assert.equal(reached,0);
});
test("B0084 workforce grant rejects wrong issuer, subject, and IdP Basic credentials",async()=>{
 let reached=0,adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{reached++;return safe();}});
 const original=valid()[2];
 for(const opts of [
  {...original,body:JSON.stringify({issuer:"https://evil.org",subject:"staff-001"})},
  {...original,body:JSON.stringify({issuer,subject:"\u0000"})},
  {...original,body:JSON.stringify({issuer,subject:"staff-001",tenantId:"untrusted"})},
  {...original,headers:{...original.headers,Authorization:basic}}
 ]){
  await assert.rejects(()=>adapter(urls[2],opts),IdentityTransportError);
 }
 assert.equal(reached,0);
});
test("B0085 deny SSRF, forwarding headers, redirects and cacheable upstream response",async()=>{
 let reached=0,adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{
  reached++;return safe();
 }});
 const original=valid()[0];
 for(const url of [
  urls[0]+"?redirect=https://evil.org",
  "https://id.hospital.org.evil.org/realms/a/protocol/openid-connect/token",
  "https://staffing.hospital.org/admin"
 ])await assert.rejects(()=>adapter(url,original),IdentityTransportError);
 for(const opts of [
  {...original,headers:{...original.headers,Cookie:"secret=1"}},
  {...original,headers:{...original.headers,"X-Forwarded-Host":"evil.org"}},
  {...original,redirect:"follow"},{...original,cache:"force-cache"},
  {...original,credentials:"include"},{...original,body:"A".repeat(32769)}
 ])await assert.rejects(()=>adapter(urls[0],opts),IdentityTransportError);
 assert.equal(reached,0);
 for(const upstream of [
  new Response("{}",{status:200,headers:{"Content-Type":"application/json"}}),
  new Response("",{status:302,headers:{Location:"https://evil.org"}}),
  new Response("html",{status:200,headers:{"Content-Type":"text/html","Cache-Control":"no-store"}})
 ]){
  const denyAdapter=createRestrictedIdentityTransport(config,{fetcher:async()=>upstream});
  await assert.rejects(()=>denyAdapter(urls[0],original),IdentityTransportError);
 }
});
test("B0085 upstream errors are sanitized and do not reveal confidential credentials",async()=>{
 const adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{throw Error(secret);}});
 await assert.rejects(()=>adapter(urls[1],valid()[1]),e=>{
  assert.equal(e.message,"identity_transport_denied");
  assert.doesNotMatch(e.message,/VaultSecret/);return true;
 });
});
