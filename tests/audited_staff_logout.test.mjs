import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {requireAuditedStaffLogout,AuditedStaffLogoutError} from "../apps/web/lib/audited-staff-logout.mjs";
import {STAFF_TICKET_COOKIE} from "../apps/web/lib/opaque-staff-session.mjs";

const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222";
const ref="B".repeat(43),origin="https://staff.synqoria.org/";
const context={actorId:actor,tenantId:tenant,expiresAt:Date.now()+300000,bearer:"private.jwt.token"};
const clear={name:STAFF_TICKET_COOKIE,value:"",options:{maxAge:0,httpOnly:true,secure:true}};
const request=({method="POST",site=origin.slice(0,-1),ticket=ref}={})=>
 new Request(origin+"api/auth/logout",{method,headers:{
  ...(site===null?{}:{origin:site}),cookie:STAFF_TICKET_COOKIE+"="+ticket
 }});
const fail=fn=>assert.rejects(fn,AuditedStaffLogoutError);
function harness({read=async()=>context,logout=async()=>({revoked:true,clearSessionCookie:clear}),
 append=async()=>({eventId:randomUUID()}),correlation=randomUUID}={}){
 const log=[],written=[];
 const svc={logout:async req=>{log.push("revoke");return logout(req);}};
 const store={read:async ref=>{log.push("read");return read(ref);}};
 const audit={append:async x=>{log.push("audit");written.push(x);return append(x);}};
 return {handle:requireAuditedStaffLogout(svc,store,audit,{publicOrigin:origin,createCorrelationId:correlation}),log,written};
}
test("P03-B0045 reclaims ticket before recording a pseudonymous revocation event",async()=>{
 const h=harness(),result=await h.handle.logout(request());
 assert.equal(result.revoked,true);
 assert.equal(result.clearSessionCookie.options.maxAge,0);
 assert.deepEqual(h.log,["read","revoke","audit"]);
 assert.deepEqual(h.written.map(x=>[x.actorId,x.tenantId,x.kind,x.outcome]),[
  [actor,tenant,"SESSION_REVOKED","PERMITTED"]
 ]);
 assert.equal(Object.keys(h.written[0]).sort().join(","),
  "actorId,correlationId,kind,outcome,tenantId");
 assert.ok(!JSON.stringify(result).includes(context.bearer));
});
test("P03-B0045 forged origin, GET, and cross-site logout cannot touch identity DB",async()=>{
 const h=harness();
 for(const input of [{method:"GET"},{site:"https://evil.example"},{site:null}]){
  await fail(()=>h.handle.logout(request(input)));
 }
 assert.deepEqual(h.log,[]);
});
test("P03-B0045 no session and idempotent logout do not produce audit event",async()=>{
 const h=harness({read:async()=>null,logout:async()=>({revoked:false,clearSessionCookie:clear})});
 const result=await h.handle.logout(request());
 assert.equal(result.revoked,false);
 assert.deepEqual(h.log,["read","revoke"]);
});
test("P03-B0045 inconsistent store, forged identity or bad audit response must fail closed",async()=>{
 for(const overrides of [
  {read:async()=>null},
  {read:async()=>({...context,tenantId:"untrusted"})},
  {append:async()=>{throw Error("audit DB down");}},
  {append:async()=>({})},
  {correlation:()=> "not-uuid"},
  {logout:async()=>{throw Error("revoke DB unavailable");}}
 ]){
  const h=harness(overrides);
  await fail(()=>h.handle.logout(request()));
 }
});
test("P03-B0045 wrong trusted origin or missing audit writer cannot initialize",()=>{
 assert.throws(()=>requireAuditedStaffLogout({},null,null,{publicOrigin:origin}),AuditedStaffLogoutError);
 assert.throws(()=>requireAuditedStaffLogout({logout:async()=>{}},
  {read:async()=>{}},{append:async()=>{}},{publicOrigin:"http://staff.synqoria.org/"}),AuditedStaffLogoutError);
});
