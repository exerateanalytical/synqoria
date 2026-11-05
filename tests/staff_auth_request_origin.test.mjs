import test from "node:test";
import assert from "node:assert/strict";
import {requireCanonicalStaffAuthRequest,StaffAuthRequestOriginError} from "../apps/web/lib/staff-auth-request-origin.mjs";
import {createStaffAuthHttpHandlers} from "../apps/web/lib/staff-auth-http-handlers.mjs";
const origin="https://staff.synqoria.org/";
const issuer="https://identity.synqoria.org/realms/hospital";
const authUrl=issuer+"/protocol/openid-connect/auth?response_type=code&code_challenge_method=S256&state="+"s".repeat(43);
const flowCookie={name:"__Host-synqoria-oidc-flow",
 value:"v1.abcdeabcdeabcde.ABCDEFGHIJKLMNOPQRSTUVWXYZ123456.abcdefghijklmnopqrstuv",
 options:{httpOnly:true,secure:true,path:"/",sameSite:"lax",maxAge:300}};
const make=(path,method="GET",headers={})=>new Request(origin.replace(/\/$/,"")+path,{method,headers});
const guard=(request,operation)=>requireCanonicalStaffAuthRequest(request,{publicOrigin:origin,operation});
const deny=fn=>assert.throws(fn,StaffAuthRequestOriginError);
test("P03-B0051 canonical HTTPS auth origin and exact route accepted",()=>{
 const start=guard(make("/api/auth/oidc/start","GET",{"Sec-Fetch-Site":"same-origin"}),"start");
 assert.deepEqual(start,{operation:"start",origin:"https://staff.synqoria.org"});
 assert.equal(Object.isFrozen(start),true);
 const callback=guard(make("/api/auth/oidc/callback?code=opaque&state=opaque","GET",
  {"Sec-Fetch-Site":"cross-site"}),"callback");
 assert.equal(callback.operation,"callback");
 const logout=guard(make("/api/auth/logout","POST",{"origin":"https://staff.synqoria.org",
  "sec-fetch-site":"same-origin"}),"logout");
 assert.equal(logout.operation,"logout");
});
test("P03-B0051 wrong Host, HTTP, path, method, browser site and logout origin deny",()=>{
 for(const [request,operation] of [
  [make("/api/auth/oidc/start","GET",{host:"evil.example"}),"start"],
  [new Request("http://staff.synqoria.org/api/auth/oidc/start"),"start"],
  [make("/api/auth/oidc/start?next=https://evil.example"),"start"],
  [make("/api/auth/oidc/%73tart"),"start"],
  [make("/api/auth/oidc/start","POST"),"start"],
  [make("/api/auth/oidc/start","GET",{"sec-fetch-site":"cross-site"}),"start"],
  [make("/api/auth/logout","POST",{origin:"https://attacker.example"}),"logout"],
  [make("/api/auth/logout","POST",{"sec-fetch-site":"same-site",origin:"https://staff.synqoria.org"}),"logout"],
  [make("/api/auth/oidc/callback?state=x#fragment"),"callback"]
 ])deny(()=>guard(request,operation));
});
test("P03-B0051 optional HTTP integration validates canonical origin BEFORE touching the IdP",async()=>{
 const calls=[];
 const service={
  begin:async()=>{calls.push("begin");return {authorizationUrl:authUrl,flowCookie};},
  complete:async()=>{calls.push("complete");throw Error("no real IdP in this test");},
  logout:async()=>{calls.push("logout");throw Error("no real DB in this test");}
 };
 const app=createStaffAuthHttpHandlers(service,{issuer,publicOrigin:origin});
 const cross=await app.start(make("/api/auth/oidc/start","GET",{host:"evil.example"}));
 assert.equal(cross.status,403);assert.deepEqual(calls,[]);
 const allowed=await app.start(make("/api/auth/oidc/start","GET",{host:"staff.synqoria.org"}));
 assert.equal(allowed.status,303);assert.deepEqual(calls,["begin"]);
 assert.equal(allowed.headers.get("location"),authUrl);
 const logout=await app.logout(make("/api/auth/logout","POST",{origin:"https://evil.example"}));
 assert.equal(logout.status,403);assert.deepEqual(calls,["begin"]);
});
