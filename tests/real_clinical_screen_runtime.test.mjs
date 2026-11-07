import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {sealStaffSession,openStaffSession,readSessionCookie,STAFF_COOKIE,secureCookieOptions} from "../apps/web/lib/clinical-session.mjs";
import {readFacility,readPatient,readTriageCase,readTriageWorklist,validateTriageViewRequest,readCellHealth,configuredCellOrigin,verifyPatient,verifyTriageCase,validUuid} from "../apps/web/lib/clinical-cell.mjs";
import {defaultPreferences,encodePreferences,decodePreferences,sanitizePreferences} from "../apps/web/lib/workforce-preferences.mjs";

const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444",caseId="55555555-5555-4555-8555-555555555555";
const key=randomBytes(32).toString("hex");
const env={SYNQORIA_STAFF_COOKIE_KEY:key,SYNQORIA_TRUSTED_CELL_ORIGIN:"https://cell.synqoria-clinical.org/"};
const now=Date.now(),token="VerifiedOpaqueToken1234567890";
const payload={
 issuer:"SYNQORIA_OIDC_VERIFIED_V1",actorId:actor,tenantId:tenant,facilityId:facility,
 bearer:token,issuedAt:now-2000,expiresAt:now+900000,capabilities:["patient:view","facility:view","triage:assess","triage:queue"]
};
const cookie=sealStaffSession(payload,{env,now});
const authorized=()=>new Request("https://app.synqoria-clinical.org/api/clinical/test",{headers:{cookie:STAFF_COOKIE+"="+cookie,Accept:"application/json"}});
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store, private"}});
const facilityDto={facilityId:facility,revision:9};
const patientDto={patientId:patient,identityStatus:"verified",revision:17};
const triageDto={case:{caseId,stage:"ARRIVED",revision:2,station:null,assignee:null,arrivedAt:"2026-11-05T09:30:00Z",paused:false,safetyHold:false,manualEscalation:false}};
const requests=[];
function fetcher(url,init){
 requests.push({url,method:init.method,authorization:init.headers.Authorization||null,credentials:init.credentials,cache:init.cache,redirect:init.redirect});
 if(url.endsWith("/api/v1/facility/context"))return Promise.resolve(json(facilityDto));
 if(url.endsWith("/api/v1/patients/"+patient))return Promise.resolve(json(patientDto));
 if(url.endsWith("/api/v1/triage/patients/"+patient+"/cases/"+caseId))return Promise.resolve(json(triageDto));
 if(url.endsWith("/api/v1/triage/patients/"+patient+"/views/FACILITY_QUEUE"))return Promise.resolve(json({cases:[triageDto.case]}));
 if(url.endsWith("/health/live"))return Promise.resolve(json({process:"alive"}));
 if(url.endsWith("/health/ready"))return Promise.resolve(json({status:"ready"}));
 return Promise.resolve(json({status:404,code:"not_found"},404));
}
test("staff cookies are authenticated, encrypted, secure and expire within an hour",()=>{
 assert.match(cookie,/^v1\./);
 assert.ok(!cookie.includes(token));assert.ok(!cookie.includes(actor));
 assert.deepEqual(openStaffSession(cookie,{env,now}).capabilities,payload.capabilities);
 assert.equal(openStaffSession(cookie.slice(0,-2)+"xx",{env,now}),null);
 assert.equal(openStaffSession(cookie,{env,now:now+1000000}),null);
 assert.equal(openStaffSession(cookie,{env:{},now}),null);
 assert.equal(openStaffSession(cookie,{env:{...env,SYNQORIA_STAFF_COOKIE_KEY:randomBytes(32).toString("hex")},now}),null);
 assert.equal(readSessionCookie(STAFF_COOKIE+"="+cookie+"; "+STAFF_COOKIE+"="+cookie,{env,now}),null);
 assert.equal(readSessionCookie(STAFF_COOKIE+"="+cookie,{env,now}).facilityId,facility);
 assert.deepEqual(secureCookieOptions(),{httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:3600,priority:"high"});
});
test("self-asserted sessions, bad claims and browser bearer header cannot establish trust",()=>{
 const bad={...payload,issuer:"USER_ASSERTED"};
 assert.throws(()=>sealStaffSession(bad,{env,now}),/not_verified/);
 assert.throws(()=>sealStaffSession({...payload,capabilities:["patient:view","patient:view"]},{env,now}),/not_verified/);
 assert.throws(()=>sealStaffSession({...payload,expiresAt:now+3700000},{env,now}),/not_verified/);
 assert.throws(()=>sealStaffSession({...payload,facilityId:"untrusted"},{env,now}),/not_verified/);
 assert.equal(readSessionCookie("",{env,now}),null);
 assert.equal(readSessionCookie("Authorization=Bearer "+token,{env,now}),null);
});
test("untrusted or local backend origin is rejected independent of browser request",()=>{
 for(const value of ["http://cell.synqoria-clinical.org/","https://localhost/","https://127.0.0.1/","https://cell.test/","https://cell.example/","https://cell.invalid/","https://cell.synqoria-clinical.org/patients","https://user:secret@cell.synqoria-clinical.org/","https://cell.synqoria-clinical.org:8443/"]){
  assert.equal(configuredCellOrigin({SYNQORIA_TRUSTED_CELL_ORIGIN:value}),null,value);
 }
 assert.equal(configuredCellOrigin(env),"https://cell.synqoria-clinical.org");
});
test("real authorized facility response verifies server-owned facility and strips credential",async()=>{
 requests.length=0;
 const r=await readFacility(authorized(),{env,fetcher});
 assert.equal(r.status,200,"safe response "+JSON.stringify(await r.clone().json())+" upstreamCalls "+requests.length);
 assert.deepEqual(await r.json(),facilityDto);
 assert.equal(r.headers.get("cache-control"),"no-store, private");
 assert.equal(requests.length,1);
 assert.equal(requests[0].authorization,"Bearer "+token);
 assert.equal(requests[0].credentials,"omit");
 assert.equal(requests[0].redirect,"error");
});
test("missing session and capability never reach the backend",async()=>{
 requests.length=0;
 let r=await readPatient(new Request("https://app.synqoria-clinical.org/api/clinical/patients/"+patient),patient,{env,fetcher});
 assert.equal(r.status,401);
 assert.equal(requests.length,0);
 const limited=sealStaffSession({...payload,capabilities:["facility:view"]},{env,now});
 r=await readPatient(new Request("https://app.synqoria-clinical.org/",{headers:{cookie:STAFF_COOKIE+"="+limited}}),patient,{env,fetcher});
 assert.equal(r.status,404);
 assert.equal(requests.length,0);
});
test("patient identity read validates UUID, facility, response schema, no cache and source revision",async()=>{
 requests.length=0;
 let r=await readPatient(authorized(),"not-uuid",{env,fetcher});
 assert.equal(r.status,400);assert.equal(requests.length,0);
 r=await readPatient(authorized(),patient,{env,fetcher});
 assert.equal(r.status,200);
 const data=await r.json();
 assert.deepEqual(data,{patientId:patient,identityStatus:"verified",revision:17,facilityId:facility});
 assert.ok(!JSON.stringify(data).includes(token));
 assert.equal(requests.length,2);
 assert.ok(requests.every(x=>x.method==="GET"&&x.cache==="no-store"));
 assert.equal(verifyPatient({...patientDto,name:"Patient A"},patient),false);
 assert.equal(verifyPatient({...patientDto,patientId:actor},patient),false);
 assert.equal(validUuid(patient),true);
 const mismatched=async(url,init)=>url.endsWith("/api/v1/facility/context")?json({...facilityDto,facilityId:actor}):json(patientDto);
 r=await readPatient(authorized(),patient,{env,fetcher:mismatched});
 assert.equal(r.status,404);
});
test("404 patient response never distinguishes a missing record from denied access",async()=>{
 const denied=async(url)=>url.endsWith("/api/v1/facility/context")?json(facilityDto):json({status:404,code:"not_found"},404);
 const r=await readPatient(authorized(),patient,{env,fetcher:denied});
 assert.equal(r.status,404);
 assert.deepEqual(await r.json(),{status:404,code:"not_found"});
});
test("triage read requires verified facility, patient disclosure, capabilities and exact case metadata",async()=>{
 requests.length=0;
 const r=await readTriageCase(authorized(),patient,caseId,{env,fetcher});
 assert.equal(r.status,200);
 const dto=await r.json();
 assert.deepEqual(dto,{case:triageDto.case,facilityId:facility});
 assert.equal(requests.length,3);
 assert.ok(requests.every(x=>x.method==="GET"));
 assert.equal(verifyTriageCase(triageDto,caseId),true);
 assert.equal(verifyTriageCase({case:{...triageDto.case,notes:"secret"}},caseId),false);
 assert.equal(verifyTriageCase({case:{...triageDto.case,caseId:actor}},caseId),false);
});
test("cell health needs both live and ready, never claims clinical certification",async()=>{
 let r=await readCellHealth(new Request("https://app.synqoria-clinical.org/api/clinical/health"),{env,fetcher});
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{process:"alive",readiness:"ready",kind:"service_health_only",clinicalCertification:false});
 const notReady=async(url)=>url.endsWith("/health/live")?json({process:"alive"}):json({status:503,code:"not_integrated"},503);
 r=await readCellHealth(authorized(),{env,fetcher:notReady});
 assert.equal(r.status,503);
 r=await readCellHealth(authorized(),{env:{},fetcher});
 assert.equal(r.status,503);
});
test("nonclinical preferences are allowlisted and exclude tokens, patient data and arbitrary HTML",()=>{
 assert.deepEqual(defaultPreferences(),{language:"en",timeFormat:"24h",density:"comfortable",contrast:"standard",motion:"system"});
 const restored=decodePreferences(JSON.stringify({language:"fr",timeFormat:"12h",contrast:"high",patientId:patient,accessToken:token,html:"<script/>"}));
 assert.deepEqual(restored,{language:"fr",timeFormat:"12h",density:"comfortable",contrast:"high",motion:"system"});
 const serialized=encodePreferences({...restored,bearer:token});
 assert.ok(!serialized.includes(token));assert.ok(!serialized.includes(patient));
 assert.deepEqual(sanitizePreferences("bad"),defaultPreferences());
 assert.deepEqual(decodePreferences("{broken"),defaultPreferences());
});

test("triage worklist validates explicit case scope and prevents unrestricted patient search",()=>{
 assert.equal(validateTriageViewRequest({view:"FACILITY_QUEUE",caseIds:[caseId]})?.caseIds.length,1);
 assert.equal(validateTriageViewRequest({view:"FACILITY_QUEUE",caseIds:[]}),null);
 assert.equal(validateTriageViewRequest({view:"FACILITY_QUEUE",caseIds:[caseId,caseId]}),null);
 assert.equal(validateTriageViewRequest({view:"FACILITY_QUEUE",caseIds:[caseId],actorId:actor}),null);
 assert.equal(validateTriageViewRequest({view:"STATION_QUEUE",caseIds:[caseId]}),null);
 assert.equal(validateTriageViewRequest({view:"STATION_QUEUE",caseIds:[caseId],stationOrClinician:facility})?.view,"STATION_QUEUE");
 assert.equal(validateTriageViewRequest({view:"ARRIVALS_SINCE",caseIds:[caseId],after:"not-time"}),null);
});
const worklistRequest=(input,origin="https://app.synqoria-clinical.org",loggedIn=true)=>{
 return new Request("https://app.synqoria-clinical.org/api/clinical/triage/views/"+patient,{method:"POST",headers:{Origin:origin,"Content-Type":"application/json",...(loggedIn?{cookie:STAFF_COOKIE+"="+cookie}:{})},body:JSON.stringify(input)});
};
test("cross-origin POST and malformed requested view denied before upstream read",async()=>{
 requests.length=0;
 const input={view:"FACILITY_QUEUE",caseIds:[caseId]};
 let r=await readTriageWorklist(worklistRequest(input,"https://attacker.example.org"),patient,{env,fetcher});
 assert.equal(r.status,403);assert.equal(requests.length,0);
 r=await readTriageWorklist(worklistRequest({...input,caseIds:[]}),patient,{env,fetcher});
 assert.equal(r.status,400);assert.equal(requests.length,0);
 r=await readTriageWorklist(worklistRequest(input,undefined,false),patient,{env,fetcher});
 assert.equal(r.status,401);assert.equal(requests.length,0);
});
test("authorized triage worklist forwards only permitted explicit case IDs and validates source",async()=>{
 requests.length=0;
 const input={view:"FACILITY_QUEUE",caseIds:[caseId]};
 const r=await readTriageWorklist(worklistRequest(input),patient,{env,fetcher});
 assert.equal(r.status,200);
 const body=await r.json();
 assert.equal(body.facilityId,facility);
 assert.equal(body.view,"FACILITY_QUEUE");
 assert.deepEqual(body.cases,[triageDto.case]);
 assert.equal(requests.length,3);
 assert.equal(requests[2].method,"POST");
 assert.ok(requests[2].url.endsWith("/views/FACILITY_QUEUE"));
 assert.equal(requests[2].authorization,"Bearer "+token);
});


test("triage CSRF uses explicit trusted browser origin behind reverse proxy, never internal URL or spoofed Host",async()=>{
 const publicOrigin="https://staff.synqoria-clinical.org";
 const reverseProxyEnv={...env,SYNQORIA_WEB_PUBLIC_ORIGIN:publicOrigin};
 const input={view:"FACILITY_QUEUE",caseIds:[caseId]};
 const headers={"Content-Type":"application/json",Origin:publicOrigin,cookie:STAFF_COOKIE+"="+cookie,Host:"attacker.example.net"};
 const req=new Request("https://internal.reverseproxy.local/api/clinical/triage/views/"+patient,{method:"POST",headers,body:JSON.stringify(input)});
 const result=await readTriageWorklist(req,patient,{env:reverseProxyEnv,fetcher});
 assert.equal(result.status,200);
 const spoofed=new Request("https://internal.reverseproxy.local/api/clinical/triage/views/"+patient,{method:"POST",headers:{...headers,Origin:"https://attacker.example.net"},body:JSON.stringify(input)});
 assert.equal((await readTriageWorklist(spoofed,patient,{env:reverseProxyEnv,fetcher})).status,403);
 const untrustedOriginEnv={...env,SYNQORIA_WEB_PUBLIC_ORIGIN:"http://public.synqoria-clinical.org"};
 assert.equal((await readTriageWorklist(req,patient,{env:untrustedOriginEnv,fetcher})).status,503);
});
