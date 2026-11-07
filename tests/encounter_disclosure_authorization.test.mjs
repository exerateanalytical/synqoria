import test from "node:test";
import assert from "node:assert/strict";
import {readLongitudinal} from "../apps/web/lib/longitudinal-cell.mjs";
import {authorizeEncounterDisclosure,EncounterDisclosureError} from "../apps/web/lib/encounter-disclosure-authorization.mjs";

const now=Date.now(),actor="11111111-1111-4111-8111-111111111111",
 tenant="22222222-2222-4222-8222-222222222222",facility="33333333-3333-4333-8333-333333333333",
 patient="44444444-4444-4444-8444-444444444444",encounter="55555555-5555-4555-8555-555555555555";
const token="VerifiedOpaqueToken1234567890",origin="https://clinical.examplehospital.cm";
const staff={actorId:actor,tenantId:tenant,facilityId:facility,bearer:token,
 capabilities:["patient:view","encounter.read","encounter.audit.read","note.read","observation.read"],
 sessionExpiresAt:now+300000};
const env={SYNQORIA_TRUSTED_CELL_ORIGIN:origin+"/",SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"1"};
const request=new Request("https://staff.examplehospital.cm/api/clinical/longitudinal/"+
 patient+"/"+encounter+"/summary");
const policy={actorId:actor,tenantId:tenant,facilityId:facility,patientId:patient,
 encounterId:encounter,resource:"encounter.summary",decision:"ALLOW",revision:22,expiresAt:now+30000};
const summary={encounterId:encounter,revision:12,stage:"ACTIVE",onLeave:false,notes:2,
 observations:1,auditHead:"a".repeat(64)};
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{
 "Content-Type":"application/json","Cache-Control":"no-store"}});
function upstream(reply=policy){
 const calls=[];
 const fetcher=async(url,init)=>{
  calls.push({url,init});
  if(url.endsWith("/api/v1/facility/context"))return json({facilityId:facility,revision:4});
  if(url.endsWith("/api/v1/security/encounter-disclosure"))return json(reply);
  if(url.endsWith("/api/v1/patients/"+patient))return json({patientId:patient,identityStatus:"verified",revision:9});
  if(url.endsWith("/summary"))return json(summary);
  return json({code:"not_found"},404);
 };
 return {calls,fetcher};
}
const attempt=(s,opts={})=>readLongitudinal(request,patient,encounter,"summary",null,{
 env,fetcher:s.fetcher,sessionResolver:async()=>staff,nowMs:now,...opts
});
const denied=f=>assert.rejects(f,EncounterDisclosureError);
test("P03-B0042 verified grant binds exact patient, encounter, actor and resource before chart read",async()=>{
 const s=upstream(),r=await attempt(s);
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{resource:"summary",patientId:patient,encounterId:encounter,
  facilityId:facility,data:summary});
 assert.deepEqual(s.calls.map(x=>new URL(x.url).pathname),[
  "/api/v1/facility/context","/api/v1/security/encounter-disclosure",
  "/api/v1/patients/"+patient,"/api/v1/clinical/patients/"+patient+"/encounters/"+encounter+"/summary"
 ]);
 assert.equal(s.calls[1].init.method,"POST");
 assert.deepEqual(JSON.parse(s.calls[1].init.body),{actorId:actor,tenantId:tenant,facilityId:facility,
  patientId:patient,encounterId:encounter,resource:"encounter.summary",purpose:"TREATMENT"});
});
test("P03-B0042 wrong encounter, tenant, patient, resource or expired decision blocks even patient metadata",async()=>{
 for(const reply of [{...policy,encounterId:patient},{...policy,tenantId:actor},
  {...policy,patientId:actor},{...policy,resource:"note.read"},
  {...policy,decision:"DENY"},{...policy,expiresAt:now-1},
  {...policy,expiresAt:now+120000},{...policy,patientName:"unapproved"}]){
  const s=upstream(reply),r=await attempt(s);
  assert.equal(r.status,404);
  assert.equal(s.calls.length,2);
 }
});
test("P03-B0042 missing source endpoint fails closed, no clinical reads",async()=>{
 const calls=[];
 const fetcher=async(url)=>{
  calls.push(url);
  if(url.endsWith("/facility/context"))return json({facilityId:facility,revision:4});
  return json({code:"not_found"},404);
 };
 const r=await readLongitudinal(request,patient,encounter,"summary",null,{
  env,fetcher,sessionResolver:async()=>staff,nowMs:now
 });
 assert.equal(r.status,404);
 assert.equal(calls.length,2);
});
test("P03-B0042 note/audit/observation require distinct resource permits",async()=>{
 for(const [operation,resource] of [["audit","encounter.audit"],["note-index","note.read"],
  ["note","note.read"],["observations","observation.read"],["lineage","observation.read"]]){
  const calls=[];
  const result=await authorizeEncounterDisclosure(staff,patient,encounter,operation,{
   origin,nowMs:now,fetcher:async(url,init)=>{calls.push(JSON.parse(init.body));return json({...policy,resource});}
  });
  assert.equal(result.resource,resource);
  assert.equal(calls[0].resource,resource);
 }
});
test("P03-B0042 missing patient:view or wrong source origin denies before any outbound call",async()=>{
 let called=0,fetcher=async()=>{called++;return json(policy);};
 await denied(()=>authorizeEncounterDisclosure({...staff,capabilities:["encounter.read"]},
  patient,encounter,"summary",{origin,nowMs:now,fetcher}));
 await denied(()=>authorizeEncounterDisclosure(staff,patient,encounter,"summary",{
  origin:"http://clinical.examplehospital.cm",nowMs:now,fetcher}));
 assert.equal(called,0);
});
