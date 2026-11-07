import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {readLongitudinal} from "../apps/web/lib/longitudinal-cell.mjs";
import {authorizeAuditedEncounterDisclosure,AuditedEncounterDisclosureError} from "../apps/web/lib/audited-encounter-disclosure.mjs";

const now=Date.now();
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444",
 encounter="55555555-5555-4555-8555-555555555555";
const bearer="VerifiedOpaqueToken1234567890";
const staff={actorId:actor,tenantId:tenant,facilityId:facility,bearer,
 capabilities:["patient:view","encounter.read"],sessionExpiresAt:now+300000};
const origin="https://clinical.examplehospital.cm";
const env={SYNQORIA_TRUSTED_CELL_ORIGIN:origin+"/",
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE:"1"};
const request=new Request("https://staff.examplehospital.cm/api/clinical/longitudinal/"+patient+"/"+encounter+"/summary");
const policy={actorId:actor,tenantId:tenant,facilityId:facility,
 patientId:patient,encounterId:encounter,resource:"encounter.summary",decision:"ALLOW",
 expiresAt:now+30000,revision:11};
const summary={encounterId:encounter,revision:12,stage:"ACTIVE",onLeave:false,notes:2,
 observations:1,auditHead:"a".repeat(64)};
const json=(x,status=200)=>new Response(JSON.stringify(x),{status,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
function harness(rule=policy,{failAudit=false}={}){
 const calls=[],events=[];
 const fetcher=async url=>{
  calls.push(url);
  if(url.endsWith("/api/v1/facility/context"))return json({facilityId:facility,revision:4});
  if(url.endsWith("/api/v1/security/encounter-disclosure"))return json(rule);
  if(url.endsWith("/api/v1/patients/"+patient))return json({patientId:patient,identityStatus:"verified",revision:9});
  if(url.endsWith("/summary"))return json(summary);
  return json({code:"not_found"},404);
 };
 const auditWriter={append:async x=>{events.push(x);if(failAudit)throw Error("audit down");return {eventId:randomUUID()};}};
 return {fetcher,calls,events,auditWriter};
}
const call=(h,overrides={})=>readLongitudinal(request,patient,encounter,"summary",null,{
 env,nowMs:now,fetcher:h.fetcher,sessionResolver:async()=>staff,auditWriter:h.auditWriter,...overrides
});
const denied=f=>assert.rejects(f,AuditedEncounterDisclosureError);

test("P03-B0048 approved exact encounter policy returns clinical read with no denied audit",async()=>{
 const h=harness(),r=await call(h);
 assert.equal(r.status,200);
 assert.equal(h.events.length,0);assert.equal(h.calls.length,4);
 const data=await r.json();
 assert.equal(data.encounterId,encounter);
});
test("P03-B0048 denied encounter never calls patient data or note endpoints",async()=>{
 const h=harness({...policy,decision:"DENY"}),r=await call(h);
 assert.equal(r.status,404);assert.equal(h.calls.length,2);
 assert.equal(h.events.length,1);
 assert.deepEqual(Object.keys(h.events[0]).sort(),["actorId","correlationId","kind","outcome","tenantId"]);
 assert.equal(h.events[0].kind,"ENCOUNTER_DISCLOSURE_DENIED");
 assert.ok(!JSON.stringify(h.events).includes(patient));
 assert.ok(!JSON.stringify(h.events).includes(encounter));
 assert.ok(!JSON.stringify(h.events).includes(bearer));
});
test("P03-B0048 failed audit, missing writer or nonexistent encounter policy fail closed",async()=>{
 for(const h of [harness({...policy,encounterId:actor}),harness({...policy,decision:"DENY"},{failAudit:true})]){
  const r=await call(h);
  assert.equal(r.status,404);assert.equal(h.calls.length,2);
 }
 const missing=harness({...policy,decision:"DENY"});
 assert.equal((await call(missing,{auditWriter:null})).status,404);
 assert.equal(missing.calls.length,1);
});
test("P03-B0048 audited strict flag without encounter policy must deny even before lookup",async()=>{
 const h=harness(),r=await call(h,{env:{...env,SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"0"}});
 assert.equal(r.status,503);assert.equal(h.calls.length,1);
});
test("P03-B0048 invalid correlation or actor fails without logging PHI",async()=>{
 const h=harness({...policy,decision:"DENY"});
 await denied(()=>authorizeAuditedEncounterDisclosure(staff,patient,encounter,"summary",{
  origin,nowMs:now,fetcher:h.fetcher,auditWriter:h.auditWriter,createCorrelationId:()=> "forged"
 }));
 assert.equal(h.events.length,0);
 await denied(()=>authorizeAuditedEncounterDisclosure({...staff,tenantId:"bad"},patient,encounter,"summary",{
  origin,nowMs:now,fetcher:h.fetcher,auditWriter:h.auditWriter
 }));
});
