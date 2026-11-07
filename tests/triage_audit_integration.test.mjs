import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {sealStaffSession,STAFF_COOKIE} from "../apps/web/lib/clinical-session.mjs";
import {readTriageAudit,validateTriageAuditPage} from "../apps/web/lib/clinical-cell.mjs";

const actor="11111111-1111-4111-8111-111111111111",
 tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",
 patient="44444444-4444-4444-8444-444444444444",
 caseId="55555555-5555-4555-8555-555555555555",
 outside="66666666-6666-4666-8666-666666666666";
const env={SYNQORIA_STAFF_COOKIE_KEY:randomBytes(32).toString("hex"),SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.examplehospital.cm/"};
const now=Date.now(),bearer="VerifiedOpaqueToken1234567890";
function makeCookie(capabilities=["triage:assess","patient:view"]){
 return sealStaffSession({issuer:"SYNQORIA_OIDC_VERIFIED_V1",actorId:actor,tenantId:tenant,facilityId:facility,bearer,issuedAt:now-3000,expiresAt:now+720000,capabilities},{env,now});
}
function request({patientId=patient,triageId=caseId,cursor="0",cookie=makeCookie(),extra=""}={}){
 return new Request("https://app.examplehospital.cm/api/clinical/triage/"+patientId+"/"+triageId+"/history?afterRevision="+cursor+extra,{headers:{Cookie:STAFF_COOKIE+"="+cookie,Accept:"application/json"}});
}
const fixture={
 facility:{facilityId:facility,revision:5},
 patient:{patientId:patient,identityStatus:"verified",revision:14},
 case:{case:{caseId,stage:"ASSESSED",revision:4,station:null,assignee:null,arrivedAt:"2026-11-05T09:30:00Z",paused:false,safetyHold:false,manualEscalation:false}}
};
const event=(n,overrides={})=>({revision:n,operation:"ASSESSMENT_BEGIN",occurredAt:"2026-11-05T09:31:00Z",digest:"a".repeat(64),...overrides});
const goodHistory={history:[event(1),event(3)],nextAfterRevision:null};
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json","Cache-Control":"no-store, private"}});
function upstream({history=goodHistory,caseBody=fixture.case,patientBody=fixture.patient,facilityBody=fixture.facility,historyStatus=200,onGet=()=>{}}={}){
 return async (url,init)=>{
  onGet(url,init);
  if(url.endsWith("/api/v1/facility/context"))return json(facilityBody);
  if(url.endsWith("/api/v1/patients/"+patient))return json(patientBody);
  if(url.endsWith("/api/v1/triage/patients/"+patient+"/cases/"+caseId))return json(caseBody);
  if(url.includes("/cases/"+caseId+"/history?afterRevision="))return json(history,historyStatus);
  return json({status:404,code:"not_found"},404);
 };
}
test("accepts strictly increasing patient-scoped audit records with cursor absent",()=>{
 assert.deepEqual(validateTriageAuditPage(goodHistory,0),goodHistory);
 assert.deepEqual(validateTriageAuditPage({history:[],nextAfterRevision:null},0),{history:[],nextAfterRevision:null});
});
test("rejects duplicate revision and decreasing revision",()=>{
 assert.equal(validateTriageAuditPage({history:[event(1),event(1)],nextAfterRevision:null},0),null);
 assert.equal(validateTriageAuditPage({history:[event(2),event(1)],nextAfterRevision:null},0),null);
});
test("rejects source fields not in explicit audit projection",()=>{
 assert.equal(validateTriageAuditPage({history:[{...event(1),freeText:"PHI"}],nextAfterRevision:null},0),null);
 assert.equal(validateTriageAuditPage({...goodHistory,bearer},0),null);
 assert.equal(validateTriageAuditPage({history:"notarray",nextAfterRevision:null},0),null);
});
test("rejects malformed digest, operation, revision and timestamp",()=>{
 for(const bad of [event(1,{digest:"wrong"}),event(1,{operation:"<script>"}),event(1,{revision:-1}),event(1,{occurredAt:"yesterday"})])
  assert.equal(validateTriageAuditPage({history:[bad],nextAfterRevision:null},0),null);
});
test("enforces bounded 100-event page, monotonic cursor and valid continuation",()=>{
 const rows=Array.from({length:100},(_,i)=>event(i+1));
 assert.deepEqual(validateTriageAuditPage({history:rows,nextAfterRevision:100},0),{history:rows,nextAfterRevision:100});
 assert.equal(validateTriageAuditPage({history:rows,nextAfterRevision:99},0),null);
 assert.equal(validateTriageAuditPage({history:[event(1)],nextAfterRevision:1},0),null);
 assert.equal(validateTriageAuditPage({history:[...rows,event(101)],nextAfterRevision:101},0),null);
 assert.equal(validateTriageAuditPage({history:[event(2)],nextAfterRevision:2},2),null);
});
test("requires canonical cursor, one query key and patient/case UUIDs",async()=>{
 const fetcher=upstream();
 for(const cursor of ["-1","01","2147483648","notnum","1.5"]){
  const response=await readTriageAudit(request({cursor}),patient,caseId,{env,fetcher});
  assert.equal(response.status,400,cursor);
 }
 assert.equal((await readTriageAudit(request({extra:"&afterRevision=2"}),patient,caseId,{env,fetcher})).status,400);
 assert.equal((await readTriageAudit(request({extra:"&email=abc"}),patient,caseId,{env,fetcher})).status,400);
 assert.equal((await readTriageAudit(request(),outside,caseId,{env,fetcher})).status,404);
 assert.equal((await readTriageAudit(request({patientId:"not-uuid"}),"not-uuid",caseId,{env,fetcher})).status,400);
});
test("authorizes current staff, facility, patient and case before fetching events",async()=>{
 const calls=[];
 const result=await readTriageAudit(request(),patient,caseId,{env,fetcher:upstream({onGet:(url,init)=>calls.push({url,method:init.method,credentials:init.credentials,headers:init.headers})})});
 assert.equal(result.status,200);
 assert.equal(result.headers.get("Cache-Control"),"no-store, private");
 assert.deepEqual(await result.json(),{patientId:patient,caseId,facilityId:facility,currentCaseRevision:4,...goodHistory});
 assert.equal(calls.length,4);
 assert.ok(calls.every(x=>x.method==="GET"&&x.credentials==="omit"&&x.headers.Authorization==="Bearer "+bearer));
 assert.ok(calls[3].url.endsWith("/history?afterRevision=0"));
});
test("no session or insufficient role prevents any upstream access",async()=>{
 let called=0;const count=()=>called++;
 let r=await readTriageAudit(new Request("https://app.examplehospital.cm/api/clinical/triage/"+patient+"/"+caseId+"/history"),patient,caseId,{env,fetcher:upstream({onGet:count})});
 assert.equal(r.status,401);
 r=await readTriageAudit(request({cookie:makeCookie(["patient:view"])}),patient,caseId,{env,fetcher:upstream({onGet:count})});
 assert.equal(r.status,404);
 r=await readTriageAudit(request({cookie:makeCookie(["triage:assess"])}),patient,caseId,{env,fetcher:upstream({onGet:count})});
 assert.equal(r.status,404);
 assert.equal(called,0);
});
test("prevents cross-facility and patient-existence disclosure",async()=>{
 const mismatch=upstream({facilityBody:{facilityId:outside,revision:5}});
 const wrong=await readTriageAudit(request(),patient,caseId,{env,fetcher:mismatch});
 assert.equal(wrong.status,404);
 const denied=upstream({patientBody:{status:404,code:"not_found"}});
 const r=await readTriageAudit(request(),patient,caseId,{env,fetcher:denied});
 assert.equal(r.status,503);
 const calls=[];const hidden=async (url,init)=>{calls.push(url);return url.endsWith("/api/v1/facility/context")?json(fixture.facility):json({status:404,code:"not_found"},404);};
 const response=await readTriageAudit(request(),patient,caseId,{env,fetcher:hidden});
 assert.equal(response.status,404);
 assert.equal(calls.length,2);
});
test("rejects case ID mismatches and events newer than verified case revision",async()=>{
 let r=await readTriageAudit(request(),patient,caseId,{env,fetcher:upstream({caseBody:{case:{...fixture.case.case,caseId:outside}}})});
 assert.equal(r.status,503);
 r=await readTriageAudit(request(),patient,caseId,{env,fetcher:upstream({history:{history:[event(5)],nextAfterRevision:null}})});
 assert.equal(r.status,503);
});
test("rejects malformed upstream audit payload rather than leaking unknown data",async()=>{
 const malicious={history:[{...event(2),sensitiveMedicalNote:"do-not-leak"}],nextAfterRevision:null};
 const r=await readTriageAudit(request(),patient,caseId,{env,fetcher:upstream({history:malicious})});
 assert.equal(r.status,503);
 const text=await r.text();
 assert.ok(!text.includes("sensitiveMedicalNote")&&!text.includes(bearer));
});
test("unconfigured upstream cell fails closed, without pretending audit completeness",async()=>{
 const r=await readTriageAudit(request(),patient,caseId,{env:{SYNQORIA_STAFF_COOKIE_KEY:env.SYNQORIA_STAFF_COOKIE_KEY},fetcher:upstream()});
 assert.equal(r.status,503);
 assert.deepEqual(await r.json(),{status:503,code:"cell_not_integrated"});
});
