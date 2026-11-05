import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {sealStaffSession,STAFF_COOKIE} from "../apps/web/lib/clinical-session.mjs";
import {parseLongitudinalParams,validateLongitudinalSource,readLongitudinal} from "../apps/web/lib/longitudinal-cell.mjs";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",patient="44444444-4444-4444-8444-444444444444",
 encounter="55555555-5555-4555-8555-555555555555",noteId="66666666-6666-4666-8666-666666666666",
 observationId="77777777-7777-4777-8777-777777777777",otherId="88888888-8888-4888-8888-888888888888";
const env={SYNQORIA_STAFF_COOKIE_KEY:randomBytes(32).toString("hex"),SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.examplehospital.cm/"};
const caps=["patient:view","encounter.read","encounter.audit.read","note.read","observation.read"];
const stamp=Date.now(),token="VerifiedOpaqueToken1234567890";
function cookie(capabilities=caps){
 return sealStaffSession({issuer:"SYNQORIA_OIDC_VERIFIED_V1",actorId:actor,tenantId:tenant,facilityId:facility,bearer:token,issuedAt:stamp-2000,expiresAt:stamp+600000,capabilities},{env,now:stamp});
}
function request(resource,query="",session=cookie()){
 return new Request("https://staff.examplehospital.cm/api/clinical/longitudinal/"+patient+"/"+encounter+"/"+resource+query,
 {headers:{Accept:"application/json",cookie:STAFF_COOKIE+"="+session}});
}
const summary={encounterId:encounter,revision:12,stage:"ACTIVE",onLeave:false,notes:2,observations:1,auditHead:"a".repeat(64)};
const note={id:noteId,status:"SIGNED",author:actor,title:"Clinical review",body:"No synthetic patient data in production",confidential:true};
const measurement={id:observationId,code:"8310-5",value:"36.8",unit:"Cel",measuredAt:"2026-11-05T14:00:00Z",status:"VERIFIED"};
const audit={history:[{revision:2,operation:"encounter.open",at:"2026-11-05T14:00:00Z",digest:"b".repeat(64)}],nextRevision:null};
const noteIndex={notes:[{id:noteId,status:"SIGNED",title:"Clinical review",confidential:true}],nextId:null};
const source={summary,audit,"note-index":noteIndex,observations:{observations:[measurement]},note,lineage:{lineage:[{...measurement,replaces:null}]}};
const json=(payload,status=200)=>new Response(JSON.stringify(payload),{status,headers:{"Content-Type":"application/json","Cache-Control":"no-store, private"}});
function upstream({overrides={},onFetch=()=>{},failAt=null}={}){
 return async (url,init)=>{
  onFetch(url,init);
  if(url.endsWith("/api/v1/facility/context"))return json({facilityId:facility,revision:4});
  if(url.endsWith("/api/v1/patients/"+patient))return json({patientId:patient,identityStatus:"verified",revision:3});
  if(failAt==="clinical")return json({status:503,code:"not_integrated"},503);
  const tail=url.includes("/encounters/"+encounter+"/")?url.split("/encounters/"+encounter+"/")[1]:"";
  const key=tail.startsWith("notes/")?"note":tail.includes("/lineage")?"lineage":tail.startsWith("observations?")?"observations":
   tail.startsWith("audit?")?"audit":tail.startsWith("note-index")?"note-index":tail;
  return key in source?json(key in overrides?overrides[key]:source[key]):json({status:404,code:"not_found"},404);
 };
}
test("verified staff cookies can carry server-proven dotted P07 capabilities",()=>{
 assert.ok(cookie().startsWith("v1."));
 assert.throws(()=>cookie(["patient:view","encounter/read"]),/not_verified/);
});
test("rejects unsupported resources, extra keys, invalid code and missing member scopes",()=>{
 assert.equal(parseLongitudinalParams("summary",{}),null===undefined?undefined:{}); // corrected below by explicit equivalence
 assert.equal(parseLongitudinalParams("notes",{}),null);
 assert.equal(parseLongitudinalParams("summary",{tenantId:tenant}),null);
 assert.equal(parseLongitudinalParams("observations",{}),null);
 assert.equal(parseLongitudinalParams("observations",{code:"8310-5"}).code,"8310-5");
 assert.equal(parseLongitudinalParams("observations",{code:"8310-5;drop"}),null);
 assert.equal(parseLongitudinalParams("audit",{limit:"101"}),null);
 assert.equal(parseLongitudinalParams("note-index",{afterId:"wrong"}),null);
});
test("source decoders reject added fields, wrong encounter, fake measurements, oversized notes and forged IDs",()=>{
 assert.equal(validateLongitudinalSource("summary",summary,{}, {encounterId:encounter}),true);
 assert.equal(validateLongitudinalSource("summary",{...summary,fullPatientName:"LEAK"},{},{encounterId:encounter}),false);
 assert.equal(validateLongitudinalSource("summary",{...summary,encounterId:otherId},{},{encounterId:encounter}),false);
 assert.equal(validateLongitudinalSource("note",note,{},{memberId:noteId}),true);
 assert.equal(validateLongitudinalSource("note",{...note,body:"x".repeat(8193)},{},{memberId:noteId}),false);
 assert.equal(validateLongitudinalSource("note",note,{},{memberId:otherId}),false);
 assert.equal(validateLongitudinalSource("observations",source.observations,{code:"8310-5",limit:50},{}),true);
 assert.equal(validateLongitudinalSource("observations",source.observations,{code:"1234-6",limit:50},{}),false);
 assert.equal(validateLongitudinalSource("lineage",source.lineage,{},{memberId:observationId}),true);
 assert.equal(validateLongitudinalSource("lineage",{lineage:[{...measurement,replaces:otherId}]},{},{memberId:observationId}),false);
 assert.equal(validateLongitudinalSource("audit",audit,{afterRevision:0,limit:50},{}),true);
 assert.equal(validateLongitudinalSource("note-index",noteIndex,{limit:50},{}),true);
});
test("authorized encounter summary requires facility, patient and capability; never discloses bearer",async()=>{
 const calls=[];
 const res=await readLongitudinal(request("summary"),patient,encounter,"summary",null,{env,fetcher:upstream({onFetch:(url,init)=>calls.push({url,init})})});
 assert.equal(res.status,200);
 const data=await res.json();
 assert.deepEqual(data,{resource:"summary",patientId:patient,encounterId:encounter,facilityId:facility,data:summary});
 assert.ok(!JSON.stringify(data).includes(token));
 assert.equal(res.headers.get("cache-control"),"no-store, private");
 assert.equal(calls.length,3);
 assert.ok(calls.every(x=>x.init.method==="GET"&&x.init.headers.Authorization==="Bearer "+token&&x.init.credentials==="omit"&&x.init.cache==="no-store"));
});
test("missing session or missing capability denies without contacting the upstream",async()=>{
 let calls=0;const fetcher=upstream({onFetch:()=>calls++});
 let res=await readLongitudinal(new Request(request("summary").url),patient,encounter,"summary",null,{env,fetcher});
 assert.equal(res.status,401);
 res=await readLongitudinal(request("summary","",cookie(["patient:view"])),patient,encounter,"summary",null,{env,fetcher});
 assert.equal(res.status,404);
 res=await readLongitudinal(request("summary","",cookie(["encounter.read"])),patient,encounter,"summary",null,{env,fetcher});
 assert.equal(res.status,404);
 assert.equal(calls,0);
});
test("bounded clinical note content accessible only with note.read and exact note id",async()=>{
 const res=await readLongitudinal(request("notes/"+noteId),patient,encounter,"note",noteId,{env,fetcher:upstream()});
 assert.equal(res.status,200);
 assert.equal((await res.json()).data.body,note.body);
 const denied=await readLongitudinal(request("notes/"+noteId,"",cookie(["patient:view","encounter.read"])),patient,encounter,"note",noteId,{env,fetcher:upstream()});
 assert.equal(denied.status,404);
 const malformed=await readLongitudinal(request("notes/"+otherId),patient,encounter,"note",otherId,{env,fetcher:upstream()});
 assert.equal(malformed.status,503);
});
test("code-filtered observation timeline validates source and rejects corrupt scope",async()=>{
 const res=await readLongitudinal(request("observations","?code=8310-5&limit=50"),patient,encounter,"observations",null,{env,fetcher:upstream()});
 assert.equal(res.status,200);
 assert.deepEqual((await res.json()).data.observations,[measurement]);
 const wrongCode=await readLongitudinal(request("observations","?code=1234-6"),patient,encounter,"observations",null,{env,fetcher:upstream()});
 assert.equal(wrongCode.status,503);
 const injection=await readLongitudinal(request("observations","?code=x&code=y"),patient,encounter,"observations",null,{env,fetcher:upstream()});
 assert.equal(injection.status,400);
});
test("exact-ID observation correction lineage never submits changes",async()=>{
 const req=request("observations/"+observationId+"/lineage");
 const calls=[];
 const res=await readLongitudinal(req,patient,encounter,"lineage",observationId,{env,fetcher:upstream({onFetch:(url,init)=>calls.push(init)})});
 assert.equal(res.status,200);
 assert.equal((await res.json()).data.lineage[0].id,observationId);
 assert.ok(calls.every(c=>c.method==="GET"));
});
test("audit and note-index pagination reject unexpected query injection",async()=>{
 assert.equal((await readLongitudinal(request("audit","?afterRevision=0&limit=50"),patient,encounter,"audit",null,{env,fetcher:upstream()})).status,200);
 assert.equal((await readLongitudinal(request("note-index","?limit=50"),patient,encounter,"note-index",null,{env,fetcher:upstream()})).status,200);
 assert.equal((await readLongitudinal(request("audit","?limit=1000"),patient,encounter,"audit",null,{env,fetcher:upstream()})).status,400);
});
test("unsupported/malformed patient, encounter and member IDs denied without upstream queries",async()=>{
 let calls=0;const fetcher=upstream({onFetch:()=>calls++});
 const bad=await readLongitudinal(request("summary"),"not-id",encounter,"summary",null,{env,fetcher});
 assert.equal(bad.status,400);
 const missing=await readLongitudinal(request("summary"),patient,encounter,"unknown",null,{env,fetcher});
 assert.equal(missing.status,404);
 const member=await readLongitudinal(request("notes/not-uuid"),patient,encounter,"note","not-uuid",{env,fetcher});
 assert.equal(member.status,400);
 assert.equal(calls,0);
});
test("cell unconfigured, unavailable or source errors fail closed without synthetic fallback",async()=>{
 const missing=await readLongitudinal(request("summary"),patient,encounter,"summary",null,{env:{SYNQORIA_STAFF_COOKIE_KEY:env.SYNQORIA_STAFF_COOKIE_KEY},fetcher:upstream()});
 assert.equal(missing.status,503);
 const unavailable=await readLongitudinal(request("summary"),patient,encounter,"summary",null,{env,fetcher:upstream({failAt:"clinical"})});
 assert.equal(unavailable.status,503);
 const contaminated=await readLongitudinal(request("summary"),patient,encounter,"summary",null,{env,fetcher:upstream({overrides:{summary:{...summary,bearer:token}}})});
 assert.equal(contaminated.status,503);
 assert.ok(!(await contaminated.text()).includes(token));
});
