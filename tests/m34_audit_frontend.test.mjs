import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=path=>readFileSync(path,"utf8");
const root="apps/web/";
const ids=["M34-S01","M34-S02","M34-S03","M34-S04","M34-S05"];
test("All five canonical M34 screen IDs dispatch real Next workstations",()=>{
 const route=read(root+"app/workforce/audit/[screen]/page.tsx");
 const ui=read(root+"components/audit/AuditWorkbench.tsx");
 for(const id of ids){
  assert.ok(route.includes(id));
  assert.ok(ui.includes(id));
 }
 const canonical=JSON.parse(read("registers/canonical-screens.json"));
 assert.deepEqual(canonical.screen_register.filter(x=>x.module==="M34").map(x=>x.id).sort(),ids);
 assert.ok(ui.includes("aria-live"));
 assert.ok(ui.includes("role=\"status\""));
});
test("Browser proxy never exposes ingestion, proof issuer or disposal",()=>{
 const proxy=read(root+"app/api/audit/proxy/[...segments]/route.ts");
 const policy=read(root+"lib/audit/proxy-policy.ts");
 for(const required of ["opsCheckOrigin","opsCheckCsrf","Idempotency-Key","Cache-Control",
 "SYNQORIA_AUDIT_API_ORIGIN",'redirect:"error"']){
  assert.ok(proxy.includes(required));
 }
 assert.ok(!policy.includes('"ingest"'));
 assert.ok(!policy.includes('"authority"'));
 assert.ok(!policy.includes('"dispose"'));
 assert.ok(policy.includes("reviewExport"));
 assert.ok(policy.includes("reviewRetention"));
});
test("Workstations access real authorized events, manifest and retention evidence",()=>{
 const ui=read(root+"components/audit/AuditWorkbench.tsx");
 assert.ok(ui.includes('call<EventRow[]|RequestRow[]>("GET",collection'));
 assert.ok(ui.includes('call<{id:string;status:string;version:number}>("POST",route'));
 assert.ok(ui.includes("viewManifest"));
 assert.ok(ui.includes("verify"));
 assert.ok(ui.includes("retention/holds"));
 assert.ok(!ui.includes("fakePatient"));
 assert.ok(!ui.includes("mockData"));
});
