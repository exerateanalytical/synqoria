import test from "node:test";
import assert from "node:assert/strict";
import {readPatient} from "../apps/web/lib/clinical-cell.mjs";
import {authorizePatientDisclosure,PatientScopeAuthorizationError} from "../apps/web/lib/patient-disclosure-authorization.mjs";

const now=Date.now();
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444";
const token="VerifiedOpaqueToken0123456789012345";
const staff={actorId:actor,tenantId:tenant,facilityId:facility,bearer:token,
 capabilities:["patient:view","facility:view"],sessionExpiresAt:now+300000};
const origin="https://cell.synqoria-clinical.org";
const env={SYNQORIA_TRUSTED_CELL_ORIGIN:origin+"/",SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"1"};
const req=new Request("https://staff.synqoria.org/api/clinical/patients/"+patient);
const policy={actorId:actor,tenantId:tenant,facilityId:facility,patientId:patient,
 resource:"patient.demographics",decision:"ALLOW",revision:11,expiresAt:now+30000};
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{
 "Content-Type":"application/json","Cache-Control":"no-store"}});
function source(policyResult=policy){
 const calls=[];
 const fetcher=async(url,init)=>{
  calls.push({url,init});
  if(url.endsWith("/api/v1/facility/context"))return json({facilityId:facility,revision:3});
  if(url.endsWith("/api/v1/security/patient-disclosure"))return json(policyResult);
  if(url.endsWith("/api/v1/patients/"+patient))return json({patientId:patient,identityStatus:"verified",revision:9});
  return json({code:"not_found"},404);
 };
 return {calls,fetcher};
}
const attempt=(s,staffContext=staff,settings=env)=>readPatient(req,patient,{
 env:settings,nowMs:now,fetcher:s.fetcher,sessionResolver:async()=>staffContext
});
const rejected=fn=>assert.rejects(fn,PatientScopeAuthorizationError);
test("P03-B0041 facility lookup must be followed by patient disclosure permit before record read",async()=>{
 const s=source(),r=await attempt(s);
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{patientId:patient,identityStatus:"verified",revision:9,facilityId:facility});
 assert.equal(s.calls.length,3);
 assert.deepEqual(s.calls.map(x=>new URL(x.url).pathname),[
  "/api/v1/facility/context","/api/v1/security/patient-disclosure","/api/v1/patients/"+patient
 ]);
 const security=s.calls[1];
 assert.equal(security.init.method,"POST");
 assert.equal(security.init.headers.Authorization,"Bearer "+token);
 assert.equal(security.init.cache,"no-store");
 assert.equal(security.init.redirect,"error");
 assert.deepEqual(JSON.parse(security.init.body),{actorId:actor,tenantId:tenant,facilityId:facility,
  patientId:patient,resource:"patient.demographics",purpose:"TREATMENT"});
});
test("P03-B0041 denied, cross-patient, cross-tenant, wrong-resource and expired permits stop patient GET",async()=>{
 const bad=[
  {...policy,decision:"DENY"},{...policy,patientId:actor},
  {...policy,tenantId:actor},{...policy,actorId:tenant},
  {...policy,resource:"note.read"},{...policy,revision:0},
  {...policy,expiresAt:now-1},{...policy,expiresAt:now+120000},
  {...policy,patientName:"unapproved PHI"}
 ];
 for(const record of bad){
  const s=source(record),r=await attempt(s);
  assert.equal(r.status,404);
  assert.equal(s.calls.length,2,"patient source must not be called without positive disclosure permit");
 }
});
test("P03-B0041 absent policy endpoint or source outage stops patient lookup",async()=>{
 const calls=[];
 const fx=async(url)=>{
  calls.push(url);
  if(url.endsWith("/facility/context"))return json({facilityId:facility,revision:3});
  return json({code:"not_found"},404);
 };
 const r=await readPatient(req,patient,{env,fetcher:fx,nowMs:now,sessionResolver:async()=>staff});
 assert.equal(r.status,404);
 assert.equal(calls.length,2);
});
test("P03-B0041 untrusted source origin, unknown scope and missing staff capability deny directly",async()=>{
 await rejected(()=>authorizePatientDisclosure(staff,patient,"patient.demographics",{
  origin:"http://cell.synqoria-clinical.org",nowMs:now,fetcher:source().fetcher
 }));
 await rejected(()=>authorizePatientDisclosure(staff,patient,"clinical:write",{
  origin,nowMs:now,fetcher:source().fetcher
 }));
 await rejected(()=>authorizePatientDisclosure({...staff,capabilities:["facility:view"]},patient,"patient.demographics",{
  origin,nowMs:now,fetcher:source().fetcher
 }));
});
test("P03-B0041 legacy deployments remain explicitly feature-gated, not silently patient-authorized",async()=>{
 const s=source(),r=await attempt(s,staff,{
  SYNQORIA_TRUSTED_CELL_ORIGIN:origin+"/",SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1"
 });
 assert.equal(r.status,200);
 assert.deepEqual(s.calls.map(x=>new URL(x.url).pathname),[
  "/api/v1/facility/context","/api/v1/patients/"+patient
 ]);
});
