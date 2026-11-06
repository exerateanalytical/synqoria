import test from "node:test";
import assert from "node:assert/strict";
import {createRestrictedIdentityTransport,IdentityTransportError}
 from "../apps/web/lib/identity-restricted-transport.mjs";
const config={
 token:{tokenEndpoint:"https://id.hospital.org/realms/a/protocol/openid-connect/token"},
 introspection:{introspectionEndpoint:"https://id.hospital.org/realms/a/protocol/openid-connect/token/introspect"},
 workforce:{workforceOrigin:"https://staffing.hospital.org/"}
};
const options=()=>({method:"POST",redirect:"error",cache:"no-store",credentials:"omit",
 headers:{"Content-Type":"application/x-www-form-urlencoded"},
 body:"code=opaque",signal:new AbortController().signal});
test("B0070 exact pinned token/introspection/workforce paths; no ambient credentials",async()=>{
 const calls=[];
 const adapter=createRestrictedIdentityTransport(config,{fetcher:async(url,opts)=>{
  calls.push({url,opts});return new Response("ok",{status:200});
 }});
 const endpoints=[
  config.token.tokenEndpoint,config.introspection.introspectionEndpoint,
  "https://staffing.hospital.org/internal/v1/staff/grants/active"
 ];
 for(const u of endpoints)assert.equal((await adapter(u,options())).status,200);
 assert.deepEqual(calls.map(x=>x.url),endpoints);
 for(const call of calls){
  assert.equal(call.opts.redirect,"error");
  assert.equal(call.opts.credentials,"omit");
  assert.equal(call.opts.cache,"no-store");
  assert.equal(call.opts.headers.cookie,undefined);
 }
});
test("B0070 adversarial SSRF, path, redirect, browser cookies and unbounded body denied before fetch",async()=>{
 let count=0;
 const adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{count++;return new Response();}});
 const endpoint=config.token.tokenEndpoint;
 for(const url of [
  "https://internal.hospital.org/admin",endpoint+"?next=https://evil.org",endpoint+"/other",
  "http://id.hospital.org/realms/a/protocol/openid-connect/token",
  "https://id.hospital.org.evil.org/realms/a/protocol/openid-connect/token",
  "https://staffing.hospital.org/internal/v1/staff/grants/active/../../admin"
 ]){
  await assert.rejects(()=>adapter(url,options()),IdentityTransportError);
 }
 for(const extra of [
  {redirect:"follow"},{cache:"force-cache"},{credentials:"include"},
  {method:"GET"},{headers:{Cookie:"session=secret","Content-Type":"application/x-www-form-urlencoded"}},
  {headers:{"X-Forwarded-Host":"evil.org","Content-Type":"application/json"}},
  {body:"x".repeat(32769)},{signal:null}
 ]){
  await assert.rejects(()=>adapter(endpoint,{...options(),...extra}),IdentityTransportError);
 }
 assert.equal(count,0);
});
test("B0070 transport failure gives constant denial, not upstream exception text",async()=>{
 const adapter=createRestrictedIdentityTransport(config,{fetcher:async()=>{throw Error("secret=should-not-leak");}});
 await assert.rejects(()=>adapter(config.token.tokenEndpoint,options()),e=>{
  assert.equal(e.message,"identity_transport_denied");
  assert.doesNotMatch(e.message,/secret/);return true;
 });
});
