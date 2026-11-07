import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {readPatient} from "../apps/web/lib/clinical-cell.mjs";
import {authorizeAuditedPatientDisclosure,AuditedPatientDisclosureError} from "../apps/web/lib/audited-patient-disclosure.mjs";

const now=Date.now();
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444";
const bearer="VerifiedOpaqueToken98765432101234567890";
const staff={actorId:actor,tenantId:tenant,facilityId:facility,bearer,
 capabilities:["patient:view","facility:view"],sessionExpiresAt:now+300000};
const env={SYNQORIA_TRUSTED_CELL_ORIGIN:"https://cell.synqoria-clinical.org/",
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE:"1"};
const request=new Request("https://staff.synqoria.org/api/clinical/patients/"+patient);
const policy={actorId:actor,tenantId:tenant,facilityId:facility,patientId:patient,
 resource:"patient.demographics",decision:"ALLOW",revision:5,expiresAt:now+30000};
const json=(x,status=200)=>new Response(JSON.stringify(x),{status,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
const build=(decision=policy,{auditFail=false}={})=>{
 const calls=[],events=[];
 const fetcher=async(url)=>{
  calls.push(url);
  if(url.endsWith("/api/v1/facility/context"))return json({facilityId:facility,revision:5});
  if(url.endsWith("/api/v1/security/patient-disclosure"))return json(decision);
  if(url.endsWith("/api/v1/patients/"+patient))return json({patientId:patient,identityStatus:"verified",revision:6});
  return json({code:"not_found"},404);
 };
 const auditWriter={append:async x=>{events.push(x);if(auditFail)throw Error("audit offline");return {eventId:randomUUID()};}};
 return {fetcher,calls,events,auditWriter};
};
const read=(h,extra={})=>readPatient(request,patient,{env,fetcher:h.fetcher,
 sessionResolver:async()=>staff,nowMs:now,auditWriter:h.auditWriter,...extra});
const denied=f=>assert.rejects(f,AuditedPatientDisclosureError);

test("P03-B0047 positive exact policy allows patient read, no denial event",async()=>{
 const h=build(),r=await read(h);
 assert.equal(r.status,200);assert.equal(h.events.length,0);assert.equal(h.calls.length,3);
});
test("P03-B0047 denial blocks PHI GET and records tenant-scoped event without patient ID",async()=>{
 const h=build({...policy,decision:"DENY"}),r=await read(h);
 assert.equal(r.status,404);assert.equal(h.calls.length,2);assert.equal(h.events.length,1);
 assert.deepEqual(Object.keys(h.events[0]).sort(),["actorId","correlationId","kind","outcome","tenantId"]);
 assert.equal(h.events[0].kind,"PATIENT_DISCLOSURE_DENIED");
 assert.equal(h.events[0].outcome,"DENIED");
 assert.ok(!JSON.stringify(h.events).includes(patient));
 assert.ok(!JSON.stringify(h.events).includes(bearer));
});
test("P03-B0047 missing audit and audit outage cannot silently grant a denied read",async()=>{
 const h=build({...policy,decision:"DENY"},{auditFail:true});
 assert.equal((await read(h)).status,404);
 assert.equal(h.calls.length,2);
 const absent=build({...policy,decision:"DENY"});
 assert.equal((await read(absent,{auditWriter:null})).status,404);
 assert.equal(absent.calls.length,1,"audit writer must be required before patient policy is fetched");
});
test("P03-B0047 no policy gate but audited strict flag must fail closed",async()=>{
 const h=build();
 const r=await read(h,{env:{...env,SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"0"}});
 assert.equal(r.status,503);
 assert.equal(h.calls.length,1);
});
test("P03-B0047 missing actor, wrong correlation or malformed policy cannot create false permit",async()=>{
 const h=build({...policy,patientId:actor});
 await denied(()=>authorizeAuditedPatientDisclosure(staff,patient,"patient.demographics",{
  origin:"https://cell.synqoria-clinical.org",nowMs:now,fetcher:h.fetcher,auditWriter:h.auditWriter,
  createCorrelationId:()=> "bad"
 }));
 assert.equal(h.events.length,0);
 await denied(()=>authorizeAuditedPatientDisclosure({...staff,actorId:"bad"},patient,
  "patient.demographics",{origin:"https://cell.synqoria-clinical.org",fetcher:h.fetcher,auditWriter:h.auditWriter}));
});
