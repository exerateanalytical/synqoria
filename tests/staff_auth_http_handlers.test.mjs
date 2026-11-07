import test from "node:test";
import assert from "node:assert/strict";
import {createStaffAuthHttpHandlers,StaffAuthHttpError} from "../apps/web/lib/staff-auth-http-handlers.mjs";
const issuer="https://auth.synqoria.org/realms/hospital";
const origin="https://staff.synqoria.org";
const auth=issuer+"/protocol/openid-connect/auth?response_type=code&"+
 "code_challenge_method=S256&state="+"a".repeat(43);
const flowCookie={name:"__Host-synqoria-oidc-flow",value:"v1.abcdefghijk.ABCDEFGHIJKLMNOPQRSTUVWX0123456789.abcdefghijklmnopqrstuv",
 options:{httpOnly:true,secure:true,sameSite:"lax",path:"/",maxAge:300}};
const sessionCookie={name:"__Host-synqoria-session",value:"b".repeat(43),
 options:{httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:300}};
const clearFlowCookie={name:flowCookie.name,value:"",options:{httpOnly:true,secure:true,path:"/",maxAge:0}};
const clearSessionCookie={name:sessionCookie.name,value:"",options:{httpOnly:true,secure:true,path:"/",maxAge:0}};
const service={
 begin:async()=>({authorizationUrl:auth,flowCookie}),
 complete:async()=>({sessionCookie,clearFlowCookie}),
 logout:async()=>({revoked:true,clearSessionCookie})
};
const handlers=()=>createStaffAuthHttpHandlers(service,{issuer});
const request=(method="GET",path="/api/auth/oidc/start")=>new Request(origin+path,{method});
const cookies=res=>res.headers.getSetCookie();
test("P03-B0049 OIDC login start returns 303 pinned IdP redirect and sealed Host-only cookie",async()=>{
 const res=await handlers().start(request());
 assert.equal(res.status,303);assert.equal(res.headers.get("location"),auth);
 assert.equal(res.headers.get("cache-control"),"no-store, private");
 assert.equal(res.headers.get("referrer-policy"),"no-referrer");
 assert.match(cookies(res)[0],/^__Host-synqoria-oidc-flow=/);
 assert.match(cookies(res)[0],/Max-Age=300; Secure; HttpOnly; SameSite=Lax/);
 assert.equal(res.headers.get("content-type"),null);
});
test("P03-B0049 qualified callback releases opaque staff cookie and clears PKCE flow cookie",async()=>{
 const res=await handlers().callback(request("GET","/api/auth/oidc/callback"));
 assert.equal(res.status,303);assert.equal(res.headers.get("location"),"/workforce");
 assert.equal(cookies(res).length,2);
 assert.match(cookies(res)[0],/^__Host-synqoria-session=/);
 assert.match(cookies(res)[0],/SameSite=Strict/);
 assert.match(cookies(res)[1],/^__Host-synqoria-oidc-flow=; Path=\/; Max-Age=0;/);
 assert.ok(!JSON.stringify([...res.headers]).includes("token"));
});
test("P03-B0049 logout revocation and error modes are separate and always clear cookie",async()=>{
 const ok=await handlers().logout(request("POST","/api/auth/logout"));
 assert.equal(ok.status,204);
 assert.match(cookies(ok)[0],/^__Host-synqoria-session=; Path=\/; Max-Age=0;/);
 const fail=createStaffAuthHttpHandlers({...service,logout:async()=>{throw Error("storage offline");}},{issuer});
 const denied=await fail.logout(request("POST","/api/auth/logout"));
 assert.equal(denied.status,503);
 assert.match(cookies(denied)[0],/^__Host-synqoria-session=; Path=\/; Max-Age=0;/);
});
test("P03-B0049 method confusion, auth redirects, header injection and unsafe query rejected",async()=>{
 assert.equal((await handlers().start(request("POST"))).status,405);
 assert.equal((await handlers().callback(request("POST"))).status,405);
 assert.equal((await handlers().logout(request("GET"))).status,405);
 for(const authorizationUrl of [
  "http://auth.synqoria.org/realms/hospital/protocol/openid-connect/auth?response_type=code&code_challenge_method=S256&state=a",
  "https://attacker.example/login?response_type=code&code_challenge_method=S256&state=a",
  issuer+"/protocol/openid-connect/auth?response_type=token&code_challenge_method=S256&state=a",
  issuer+"/protocol/openid-connect/auth?response_type=code&code_challenge_method=plain&state=a"
 ]){
  const handler=createStaffAuthHttpHandlers({...service,begin:async()=>({authorizationUrl,flowCookie})},{issuer});
  assert.equal((await handler.start(request())).status,503);
 }
 const injection=createStaffAuthHttpHandlers({...service,complete:async()=>({
  sessionCookie:{...sessionCookie,value:"evil; Path=/; Secure"},clearFlowCookie
 })},{issuer});
 assert.equal((await injection.callback(request())).status,401);
});
test("P03-B0049 missing service, untrusted redirectPath and broken source fail closed",async()=>{
 assert.throws(()=>createStaffAuthHttpHandlers(null,{issuer}),StaffAuthHttpError);
 assert.throws(()=>createStaffAuthHttpHandlers(service,{issuer,redirectPath:"https://evil.example"}),StaffAuthHttpError);
 assert.equal((await createStaffAuthHttpHandlers({...service,complete:async()=>{throw Error("IdP revoked");}},{issuer})
  .callback(request())).status,401);
});
