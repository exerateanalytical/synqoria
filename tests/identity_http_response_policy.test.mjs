import test from "node:test";
import assert from "node:assert/strict";
import {assertSafeStaffAuthResponse,IdentityHttpResponseError}
 from "../apps/web/lib/identity-http-response-policy.mjs";
const issuer="https://identity.hospital.org/realms/main";
const BASE={
 "Cache-Control":"no-store, private","Pragma":"no-cache",
 "Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff",
 "Content-Security-Policy":"default-src 'none'; frame-ancestors 'none'"
};
const flow="__Host-synqoria-oidc-flow=v1."+("A".repeat(43))+"."+("B".repeat(43))+"."+("C".repeat(43));
const sess="__Host-synqoria-session="+("Z".repeat(43));
const clearFlow="__Host-synqoria-oidc-flow=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict";
const clearSession="__Host-synqoria-session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict";
function sample(operation,status,changes={}){
 const h=new Headers({...BASE,...changes});
 if(status===303&&operation==="start"){
  h.set("Location",issuer+"/protocol/openid-connect/auth?response_type=code&code_challenge_method=S256&state=abc&nonce=def");
  h.append("Set-Cookie",flow+"; Path=/; Max-Age=300; Secure; HttpOnly; SameSite=Lax");
 }else if(status===303&&operation==="callback"){
  h.set("Location","/workforce");
  h.append("Set-Cookie",sess+"; Path=/; Max-Age=600; Secure; HttpOnly; SameSite=Strict");
  h.append("Set-Cookie",clearFlow);
 }else if(status===204||operation==="logout"&&status===503)h.append("Set-Cookie",clearSession);
 else if(operation==="callback"&&status===401)h.append("Set-Cookie",clearFlow);
 return new Response(null,{status,headers:h});
}
const allow=(response,operation)=>assert.equal(
 assertSafeStaffAuthResponse(response,{operation,issuer}),true);
const refuse=(response,operation)=>assert.throws(
 ()=>assertSafeStaffAuthResponse(response,{operation,issuer}),IdentityHttpResponseError);
test("B0086 never accept arbitrary body/status or JSON token responses",()=>{
 allow(sample("start",303),"start");allow(sample("callback",303),"callback");
 allow(sample("logout",204),"logout");allow(sample("callback",401),"callback");
 allow(sample("logout",503),"logout");
 refuse(new Response(JSON.stringify({token:"Bearer secret"}),{status:200,headers:BASE}),"start");
 refuse(new Response("secret",{status:401,headers:BASE}),"callback");
 refuse(sample("start",204),"start");
});
test("B0087 callback redirect must be internal and OIDC redirect issuer-pinned with S256",()=>{
 const a=sample("start",303);
 a.headers.set("Location","https://evil.org/realms/main/protocol/openid-connect/auth?response_type=code&code_challenge_method=S256&state=abc&nonce=def");
 refuse(a,"start");
 const b=sample("start",303);
 b.headers.set("Location",issuer+"/protocol/openid-connect/auth?response_type=token&code_challenge_method=S256&state=abc&nonce=def");
 refuse(b,"start");
 const c=sample("callback",303);c.headers.set("Location","https://evil.org/");
 refuse(c,"callback");
 const d=sample("callback",303);d.headers.set("Location","//evil.org");
 refuse(d,"callback");
});
test("B0088 host-only Secure HttpOnly SameSite and required cookie expiry",()=>{
 for(const value of [
  sess+"; Domain=evil.org; Path=/; Max-Age=600; Secure; HttpOnly; SameSite=Strict",
  sess+"; Path=/; Max-Age=600; HttpOnly; SameSite=Strict",
  sess+"; Path=/; Max-Age=600; Secure; HttpOnly; SameSite=None",
  sess+"; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"
 ]){
  const h=new Headers(BASE);h.set("Location","/workforce");
  h.append("Set-Cookie",value);h.append("Set-Cookie",clearFlow);
  refuse(new Response(null,{status:303,headers:h}),"callback");
 }
 const bad=sample("logout",204);
 bad.headers.set("Set-Cookie","__Host-synqoria-session=; Path=/; Max-Age=600; Secure; HttpOnly; SameSite=Strict");
 refuse(bad,"logout");
});
test("B0089 sensitive responses reject permissive CORS, cache and missing headers",()=>{
 for(const change of [
  {"Access-Control-Allow-Origin":"*"},{"Access-Control-Allow-Credentials":"true"},
  {"Cache-Control":"public, max-age=3600"},
  {"Referrer-Policy":"unsafe-url"},{"Content-Type":"application/json"},
  {"Content-Security-Policy":"default-src *"}
 ]){
  refuse(sample("start",303,change),"start");
 }
 allow(sample("start",403),"start");
 allow(sample("logout",405),"logout");
 refuse(sample("start",401),"start");
});
