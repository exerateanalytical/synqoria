import test from "node:test";
import assert from "node:assert/strict";
import {ops001PublicStatusResponse,ops001ServerAction,ops001RuntimeStatus,
 installOps001VerifiedRuntime,Ops001RuntimeInstallationError}
 from "../apps/web/lib/ops001-runtime-bridge.mjs";
const req=(method="GET")=>new Request("https://staff.synqoria.org/api/auth/status",{method});
test("OPS001 without verified server installation denies all sensitive routes",async()=>{
 assert.deepEqual(ops001RuntimeStatus(),{ready:false});
 const status=ops001PublicStatusResponse();
 assert.equal(status.status,503);
 assert.deepEqual(await status.json(),{ready:false,clinicalRelease:false});
 assert.match(status.headers.get("cache-control"),/no-store/);
 for(const [name,method] of [
  ["login","GET"],["callback","GET"],["logout","POST"],
  ["patient-lookup","POST"],["facility","GET"]
 ]){
  const r=await ops001ServerAction(name,req(method));
  assert.equal(r.status,503,name);
  const body=await r.text();
  assert.ok(!body.includes("Bearer")&&!body.includes("patientId"));
 }
});
test("OPS001 request/user-provided fake identity implementation cannot bootstrap server",()=>{
 assert.throws(()=>installOps001VerifiedRuntime({
  http:{start:async()=>new Response(null,{status:303}),
   callback:async()=>new Response(null,{status:303}),
   logout:async()=>new Response(null,{status:204})},
  authorize:async()=>true,
  auditWriter:{append:async()=>true}
 },{SYNQORIA_RUNTIME_MODE:"production"}),Ops001RuntimeInstallationError);
 assert.equal(ops001RuntimeStatus().ready,false);
});
test("OPS001 unsupported methods do not trigger a non-existent privileged action",async()=>{
 const r=await ops001ServerAction("emergency-bypass",req("POST"));
 assert.equal(r.status,503);
});
