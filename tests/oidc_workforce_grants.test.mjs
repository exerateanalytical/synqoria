import test from "node:test";
import assert from "node:assert/strict";
import {resolveStaffWorkforceGrant,WorkforceAuthorizationError} from "../apps/web/lib/oidc-workforce-grants.mjs";
const now=Date.parse("2026-11-05T21:50:00Z");
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",shift="44444444-4444-4444-8444-444444444444";
const token="aaa.bbb.ccc",issuer="https://auth.synqoria.org/realms/hospital";
const config={workforceOrigin:"https://workforce.synqoria-clinical.org/",issuer,expectedTenantId:tenant,expectedFacilityId:facility};
const proof=Object.freeze({subject:"staff-identity-001",accessScopes:["openid","patient:view","facility:view","triage:queue"]});
const dto=Object.freeze({actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,
 role:"nurse",status:"ACTIVE",issuer,subject:proof.subject,revision:17,
 shiftStartsAt:"2026-11-05T18:00:00Z",shiftEndsAt:"2026-11-06T02:00:00Z",
 capabilities:["facility:view","patient:view"]});
const json=(body,options={})=>new Response(JSON.stringify(body),{status:options.status??200,headers:{
 "Content-Type":options.type??"application/json","Cache-Control":options.cache??"no-store"
}});
const run=(body=dto,fetcher=async()=>json(body),cfg=config,identity=proof)=>resolveStaffWorkforceGrant(identity,token,cfg,{fetcher,nowMs:now});
const reject=async fn=>assert.rejects(fn,WorkforceAuthorizationError);

test("P03-B0034 authoritative source binds actor, tenant, facility, shift and a least-privilege grant",async()=>{
 const requests=[];
 const result=await run(dto,async(url,init)=>{requests.push({url,init});return json(dto);});
 assert.deepEqual(result,{actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,
  role:"nurse",revision:17,expiresAt:Date.parse(dto.shiftEndsAt),
  capabilities:["facility:view","patient:view"]});
 assert.equal(Object.isFrozen(result),true);
 assert.equal(Object.isFrozen(result.capabilities),true);
 assert.equal(requests.length,1);
 assert.equal(requests[0].url,config.workforceOrigin.replace(/\/$/,"")+"/internal/v1/staff/grants/active");
 assert.equal(requests[0].init.method,"POST");
 assert.equal(requests[0].init.cache,"no-store");
 assert.equal(requests[0].init.credentials,"omit");
 assert.equal(requests[0].init.redirect,"error");
 assert.equal(requests[0].init.headers.Authorization,"Bearer "+token);
 assert.deepEqual(JSON.parse(requests[0].init.body),{issuer,subject:proof.subject});
 assert.equal("bearer" in result,false);
 assert.equal("patientId" in result,false);
});
test("P03-B0034 inactive, wrong subject, tenant, facility, roster revision and shift are denied",async()=>{
 for(const bad of [
  {...dto,status:"SUSPENDED"},{...dto,subject:"different-staff"},
  {...dto,tenantId:actor},{...dto,facilityId:tenant},
  {...dto,revision:0},{...dto,shiftId:"not-a-uuid"},
  {...dto,shiftStartsAt:"2026-11-05T22:00:00Z"},
  {...dto,shiftEndsAt:"2026-11-05T21:49:59Z"},
  {...dto,shiftStartsAt:"2026-11-03T18:00:00Z"},
  {...dto,shiftStartsAt:"2026-02-31T18:00:00Z"},
  {...dto,patientName:"Do not leak patient PHI"}
 ])await reject(()=>run(bad));
});
test("P03-B0034 unapproved and duplicated capabilities are rejected, no self-asserted power",async()=>{
 for(const capabilities of [
 ["clinical:write"],["patient:view","patient:view"],["patient:view","admin:*"],[],["patient:view","patient.change"]
 ])await reject(()=>run({...dto,capabilities}));
 await reject(()=>run(dto,async()=>json(dto),config,{subject:proof.subject,accessScopes:["openid"]}));
});
test("P03-B0034 untrusted or client-selected backend URL denied before any network",async()=>{
 let calls=0;const fx=async()=>{calls++;return json(dto);};
 for(const workforceOrigin of ["http://workforce.synqoria-clinical.org/",
   "https://127.0.0.1/","https://attacker.invalid/","https://workforce.synqoria-clinical.org:8443/",
   "https://workforce.synqoria-clinical.org/evil","https://workforce.synqoria-clinical.org/?redirect=evil"])
   await reject(()=>run(dto,fx,{...config,workforceOrigin}));
 await reject(()=>run(dto,fx,{...config,expectedFacilityId:"not-a-uuid"}));
 assert.equal(calls,0);
});
test("P03-B0034 errors, stale responses, oversized or non-JSON bodies fail closed",async()=>{
 await reject(()=>run(dto,async()=>json(dto,{status:503})));
 await reject(()=>run(dto,async()=>json(dto,{status:403})));
 await reject(()=>run(dto,async()=>json(dto,{cache:"public,max-age=120"})));
 await reject(()=>run(dto,async()=>json(dto,{type:"text/html"})));
 await reject(()=>run(dto,async()=>{throw Error("workforce service down");}));
 await reject(()=>run(dto,async()=>new Response("x".repeat(17000),
  {status:200,headers:{"content-type":"application/json","cache-control":"no-store"}})));
});
