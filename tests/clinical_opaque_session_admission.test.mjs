import test from "node:test";
import assert from "node:assert/strict";
import {readPatient,admission} from "../apps/web/lib/clinical-cell.mjs";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444";
const bearer="TrustedIssuerToken1234567890123456";
const env={SYNQORIA_TRUSTED_CELL_ORIGIN:"https://cell.synqoria-clinical.org/",
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1"};
const staff=Object.freeze({actorId:actor,tenantId:tenant,facilityId:facility,
 bearer,capabilities:["patient:view","facility:view"],
 sessionExpiresAt:Date.now()+300000});
const req=new Request("https://staff.synqoria.org/api/clinical/patients/"+patient,{headers:{
 Cookie:"__Host-synqoria-session=opaqueTicketRef0123456789012345678901234567"
}});
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
function fetcher(out={},calls=[]){
 return async(url,init)=>{
  calls.push({url,init});
  if(url.endsWith("/api/v1/facility/context"))return json(out.facility??{facilityId:facility,revision:3});
  if(url.endsWith("/api/v1/patients/"+patient))return json(out.patient??{patientId:patient,identityStatus:"verified",revision:9});
  return json({status:404,code:"not_found"},404);
 };
}
test("P03-B0040 verified opaque resolver authorizes existing clinical read BFF and preserves backend scope",async()=>{
 const calls=[],r=await readPatient(req,patient,{env,fetcher:fetcher({},calls),sessionResolver:async()=>staff});
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{patientId:patient,identityStatus:"verified",revision:9,facilityId:facility});
 assert.equal(calls.length,2);
 assert.ok(calls.every(x=>x.init.headers.Authorization==="Bearer "+bearer));
 assert.ok(calls.every(x=>x.init.redirect==="error"&&x.init.credentials==="omit"&&x.init.cache==="no-store"));
 assert.equal(r.headers.get("cache-control"),"no-store, private");
 assert.equal((await r.clone().text()).includes(bearer),false);
});
test("P03-B0040 production opaque-only policy denies old sealed-cookie path and missing resolver before network",async()=>{
 let calls=0,fx=async()=>{calls++;return json({facilityId:facility,revision:1});};
 const missing=await readPatient(req,patient,{env,fetcher:fx});
 assert.equal(missing.status,503);
 assert.deepEqual(await missing.json(),{status:503,code:"opaque_session_not_integrated"});
 assert.equal(calls,0);
});
test("P03-B0040 resolver errors, invalid, expired or duplicate capabilities fail before cell access",async()=>{
 const cells=[
 async()=>{throw Error("IdP revoked");},
 async()=>null,
 async()=>({...staff,actorId:"not-a-uuid"}),
 async()=>({...staff,sessionExpiresAt:Date.now()-1000}),
 async()=>({...staff,capabilities:["patient:view","patient:view"]}),
 async()=>({...staff,bearer:"invalid"})
 ];
 let calls=0,fx=async()=>{calls++;return json({facilityId:facility,revision:1});};
 for(const resolver of cells){
  const r=await readPatient(req,patient,{env,fetcher:fx,sessionResolver:resolver});
  assert.equal(r.status,401);
 }
 assert.equal(calls,0);
});
test("P03-B0040 capabilities, facility context and patient source remain fail-closed even after staff verification",async()=>{
 const calls=[];
 let r=await readPatient(req,patient,{env,fetcher:fetcher({},calls),
  sessionResolver:async()=>({...staff,capabilities:["facility:view"]})});
 assert.equal(r.status,404);assert.equal(calls.length,0);
 r=await readPatient(req,patient,{env,fetcher:fetcher({facility:{facilityId:actor,revision:1}},calls),
  sessionResolver:async()=>staff});
 assert.equal(r.status,404);
 r=await readPatient(req,patient,{env,fetcher:fetcher({patient:{patientId:actor,identityStatus:"verified",revision:9}},calls),
  sessionResolver:async()=>staff});
 assert.equal(r.status,503);
});
test("P03-B0040 clinical admission never accepts a client-supplied extra capability in resolver configuration",async()=>{
 const unauthorized=await admission(req,{env,fetcher:fetcher(),
  sessionResolver:async()=>staff,capability:"clinical:sign",additionalCapabilities:["patient:view"]});
 assert.equal(unauthorized.error?.status,404);
});
