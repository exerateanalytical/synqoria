import test from "node:test";
import assert from "node:assert/strict";
import {safeFetch} from "../apps/web/lib/clinical-cell.mjs";
const origin="https://clinical.synqoria.org";
const path="/api/v1/patients/11111111-1111-4111-8111-111111111111";
const response=(headers={})=>new Response(JSON.stringify({patientId:"approved"}),{
 status:200,headers:{"Content-Type":"application/json",...headers}
});
const run=fn=>safeFetch(origin,path,"test.jwt.mock",fn,{requireNoStore:true});
test("B0059 non-cacheable exact-origin clinical record is accepted",async()=>{
 const requests=[];
 const result=await run(async(url,options)=>{
  requests.push({url,options});return response({"Cache-Control":"no-store, private"});
 });
 assert.equal(result.status,200);
 assert.deepEqual(result.body,{patientId:"approved"});
 assert.equal(requests.length,1);
 assert.equal(requests[0].url,origin+path);
 assert.equal(requests[0].options.redirect,"error");
 assert.equal(requests[0].options.credentials,"omit");
 assert.equal(requests[0].options.cache,"no-store");
});
test("B0059 public, absent, or max-age source cache controls reject patient record",async()=>{
 for(const headers of [{},{"Cache-Control":"public,max-age=120"},
  {"Cache-Control":"private,max-age=0"},{"Cache-Control":"no-cache"}]){
  assert.equal((await run(async()=>response(headers))).status,503);
 }
});
test("B0059 redirected 200 and mismatched final source URL fail closed",async()=>{
 const redirected=response({"Cache-Control":"no-store"});
 Object.defineProperty(redirected,"redirected",{value:true});
 assert.equal((await run(async()=>redirected)).status,503);
 const swapped=response({"Cache-Control":"no-store"});
 Object.defineProperty(swapped,"url",{value:"https://otherhospital.example.net/api/v1/patients/1"});
 assert.equal((await run(async()=>swapped)).status,503);
});
test("B0059 upstream failure and invalid response remain non-disclosing",async()=>{
 assert.equal((await run(async()=>{throw Error("upstream not reachable");})).status,503);
 assert.equal((await run(async()=>new Response("not json",{headers:{
  "Content-Type":"text/html","Cache-Control":"no-store"
 }}))).status,503);
});
