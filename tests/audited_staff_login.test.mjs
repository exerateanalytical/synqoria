import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {requireAuditedStaffLogin,AuditedStaffLoginError} from "../apps/web/lib/audited-staff-login.mjs";

const actor="11111111-1111-4111-8111-111111111111";
const tenant="22222222-2222-4222-8222-222222222222";
const ticket="T".repeat(43);
const cookie={sessionCookie:{name:"__Host-synqoria-session",value:ticket,options:{secure:true,httpOnly:true}}};
const ctx={actorId:actor,tenantId:tenant,expiresAt:Date.now()+300000,bearer:"private.jwt.token"};
const request={url:"https://staff.synqoria.org/api/auth/oidc/callback"};
const denial=fn=>assert.rejects(fn,AuditedStaffLoginError);
const setup=({read=async()=>ctx,append=async()=>({eventId:randomUUID()}),
 revoke=async()=>true,complete=async()=>cookie,correlation=randomUUID}={})=>{
 const events=[],logged=[],revoked=[];
 const svc={complete:async(...args)=>{events.push("issue");return complete(...args);}};
 const store={read:async ref=>{events.push("read");return read(ref);},
  revoke:async ref=>{events.push("revoke");revoked.push(ref);return revoke(ref);}};
 const audit={append:async entry=>{events.push("audit");logged.push(entry);return append(entry);}};
 const flow=requireAuditedStaffLogin(svc,store,audit,{createCorrelationId:correlation});
 return {flow,events,logged,revoked};
};

test("P03-B0044 releases only opaque cookie after durable audit acknowledgement",async()=>{
 const h=setup(),result=await h.flow.complete(request);
 assert.equal(result,cookie);
 assert.deepEqual(h.events,["issue","read","audit"]);
 assert.equal(h.revoked.length,0);
 assert.equal(h.logged.length,1);
 assert.deepEqual(Object.keys(h.logged[0]).sort(),["actorId","correlationId","kind","outcome","tenantId"]);
 assert.equal(h.logged[0].actorId,actor);
 assert.equal(h.logged[0].tenantId,tenant);
 assert.equal(h.logged[0].kind,"LOGIN_GRANTED");
 assert.equal(h.logged[0].outcome,"PERMITTED");
 assert.match(h.logged[0].correlationId,/^[0-9a-f-]{36}$/i);
 assert.ok(!JSON.stringify(result).includes(ctx.bearer));
});
test("P03-B0044 failed audit removes just-issued session and never returns cookie",async()=>{
 for(const append of [async()=>{throw Error("audit DB down");},async()=>({}),async()=>null]){
  const h=setup({append});
  await denial(()=>h.flow.complete(request));
  assert.deepEqual(h.events,["issue","read","audit","revoke"]);
  assert.deepEqual(h.revoked,[ticket]);
 }
});
test("P03-B0044 failed store lookup, staff binding or invalid correlation rejects and revokes",async()=>{
 for(const opts of [
  {read:async()=>null},
  {read:async()=>({...ctx,actorId:"forged"})},
  {read:async()=>({...ctx,expiresAt:Date.now()-1000})},
  {correlation:()=> "not-a-uuid"}
 ]){
  const h=setup(opts);
  await denial(()=>h.flow.complete(request));
  assert.equal(h.revoked[0],ticket);
  assert.ok(!h.events.includes("audit")||opts.correlation!==undefined);
 }
});
test("P03-B0044 trusted login failure never manufactures a session or audit",async()=>{
 const h=setup({complete:async()=>{throw Error("IdP unreachable");}});
 await denial(()=>h.flow.complete(request));
 assert.deepEqual(h.events,["issue"]);
 assert.equal(h.revoked.length,0);
 assert.equal(h.logged.length,0);
});
test("P03-B0044 audit unavailable plus session-store revoke error stays fail-closed",async()=>{
 const h=setup({append:async()=>{throw Error("audit unavailable");},revoke:async()=>{throw Error("storage unavailable");}});
 await denial(()=>h.flow.complete(request));
 assert.deepEqual(h.events,["issue","read","audit","revoke"]);
});
test("P03-B0044 missing trusted dependencies fail configuration",()=>{
 assert.throws(()=>requireAuditedStaffLogin({},null,null),AuditedStaffLoginError);
 assert.throws(()=>requireAuditedStaffLogin({complete:async()=>{}},{read:async()=>{}},{append:async()=>{}}),AuditedStaffLoginError);
});
