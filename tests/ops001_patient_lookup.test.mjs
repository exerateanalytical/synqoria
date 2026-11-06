import test from "node:test";
import assert from "node:assert/strict";
import {readCanonicalPatientLookup,Ops001LookupError,createVerifiedOps001Lookup} from "../apps/web/lib/ops001-patient-lookup.mjs";
const origin="https://staff.synqoria.org/";
const patient="11111111-1111-4111-8111-111111111111";
const request=(body={patientId:patient},{method="POST",url=origin+"api/clinical/patient-lookup",headers={}}={})=>
 new Request(url,{method,headers:{"Content-Type":"application/json",
 "Origin":origin.slice(0,-1),"Sec-Fetch-Site":"same-origin",...headers},
 body:method==="POST"?JSON.stringify(body):undefined});
const deny=req=>assert.rejects(()=>readCanonicalPatientLookup(req,origin),Ops001LookupError);
test("OPS001/G-S05 exact patient UUID accepted in private same-origin POST body",async()=>{
 const r=await readCanonicalPatientLookup(request(),origin);
 assert.deepEqual(r,{patientId:patient});
 assert.ok(Object.isFrozen(r));
 assert.ok(!request().url.includes(patient));
});
test("OPS001/G-S05 no anonymous GET, query identifier, cross-site request or spoofed Host",async()=>{
 await deny(request(undefined,{method:"GET"}));
 await deny(request(undefined,{url:origin+"api/clinical/patient-lookup?patientId="+patient}));
 await deny(request(undefined,{headers:{"Origin":"https://another.example.net"}}));
 await deny(request(undefined,{headers:{"Sec-Fetch-Site":"cross-site"}}));
 await deny(request(undefined,{headers:{"Host":"crosshospital.example.net"}}));
});
test("OPS001/G-S05 rejects arbitrary demographics, extra fields, alternate content-type and malformed identifier",async()=>{
 for(const body of [{patientId:""}, {patientId:"Patient Name"}, {patientId:patient,name:"Private"},
  {id:patient}, [patient], {}])await deny(request(body));
 await deny(request(undefined,{headers:{"Content-Type":"text/plain"}}));
 await deny(request(undefined,{headers:{"Content-Type":"application/json; charset=utf-8"}}));
});
test("OPS001/G-S05 denies oversized payload even with false Content-Length",async()=>{
 const huge={patientId:patient,comment:"sensitive".repeat(40)};
 await deny(request(huge));
 await deny(request(undefined,{headers:{"Content-Length":"900"}}));
});
test("OPS001/G-S05 forged/unverified identity runtime cannot create patient lookup service",()=>{
 assert.throws(()=>createVerifiedOps001Lookup({
  authorize:async()=>true,auditWriter:{append:async()=>true}
 },{SYNQORIA_PUBLIC_ORIGIN:origin}));
});
