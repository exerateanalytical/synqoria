import test from "node:test";
import assert from "node:assert/strict";
import {requireClinicalRequestProvenance,ClinicalRequestProvenanceError}
 from "../apps/web/lib/clinical-request-provenance.mjs";
import {readFacility} from "../apps/web/lib/clinical-cell.mjs";
const origin="https://staff.synqoria.org/";
const request=(url,method="GET",headers={})=>new Request(url,{method,headers});
const deny=x=>assert.throws(x,ClinicalRequestProvenanceError);
test("B0058 same-origin secure GET and explicit-origin POST are permitted",()=>{
 const a=requireClinicalRequestProvenance(request(origin+"api/clinical/facility","GET",{
  Host:"staff.synqoria.org","Sec-Fetch-Site":"same-origin"
 }),origin);
 assert.deepEqual(a,{allowed:true,method:"GET"});
 assert.equal(Object.isFrozen(a),true);
 assert.equal(requireClinicalRequestProvenance(request(origin+"api/clinical/action","POST",{
  Origin:"https://staff.synqoria.org","Sec-Fetch-Site":"same-origin"
 }),origin).allowed,true);
});
test("B0058 cross-site, spoofed Host, HTTP and unauthorized writes are rejected",()=>{
 for(const r of [
  request(origin+"api/clinical/facility","GET",{"Sec-Fetch-Site":"cross-site"}),
  request(origin+"api/clinical/facility","GET",{Host:"evil.example"}),
  request("http://staff.synqoria.org/api/clinical/facility"),
  request(origin+"api/clinical/action","POST"),
  request(origin+"api/clinical/action","POST",{Origin:"https://attacker.example"}),
  request(origin+"api/clinical/action","POST",{"Sec-Fetch-Site":"same-site",Origin:"https://staff.synqoria.org"})
 ])deny(()=>requireClinicalRequestProvenance(r,origin));
});
test("B0058 clinical admission rejects forged provenance before reading staff session or contacting hospital cell",async()=>{
 let sessions=0,network=0;
 const result=await readFacility(request(origin+"api/clinical/facility","GET",{
  "Sec-Fetch-Site":"cross-site"
 }),{
  env:{SYNQORIA_PUBLIC_ORIGIN:origin,SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN:"1",
   SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.synqoria.org/"},
  sessionResolver:async()=>{sessions++;throw Error("not called");},
  fetcher:async()=>{network++;throw Error("not called");}
 });
 assert.equal(result.status,403);
 assert.equal(sessions,0);assert.equal(network,0);
 assert.deepEqual(await result.json(),{status:403,code:"request_provenance_denied"});
});
