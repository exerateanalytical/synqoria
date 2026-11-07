import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=path=>readFileSync(path,"utf8");
const root="apps/web/";
const screens=["M32-S01","M32-S02","M32-S03","M32-S04","M32-S05"];
test("all five M32 canonical screens have Next app routes and real interactive control wiring",()=>{
 const routes=read(root+"app/workforce/privacy/[screen]/page.tsx");
 const component=read(root+"components/privacy/PrivacyWorkbench.tsx");
 for(const s of screens){
  assert.match(routes,new RegExp(s));
  assert.match(component,new RegExp(s));
 }
 for(const action of ["directives","verify","activate","revoke","disclosures","review","cases","close"]){
  assert.ok(component.includes(action));
 }
 assert.ok(component.includes('role="status"'));
 assert.ok(component.includes('lang'));
});
test("privacy API browser proxy does not expose institutional evidence provisioning",()=>{
 const policy=read(root+"lib/privacy/proxy-policy.ts");
 const proxy=read(root+"app/api/privacy/proxy/[...segments]/route.ts");
 assert.ok(policy.includes("isAllowed"));
 assert.ok(!policy.includes('"feed/authority"'));
 assert.ok(proxy.includes("opsCheckCsrf"));
 assert.ok(proxy.includes("opsCheckOrigin"));
 assert.ok(proxy.includes('redirect:"error"'));
 assert.ok(proxy.includes('Cache-Control'));
 assert.ok(proxy.includes('Idempotency-Key'));
 assert.ok(proxy.includes("privacyOrigin"));
 assert.ok(!proxy.includes("localStorage"));
});
test("privacy workstations avoid fake population and use live authorized reads",()=>{
 const ui=read(root+"components/privacy/PrivacyWorkbench.tsx");
 assert.ok(ui.includes('call<RecordRow[]>("GET",collection'));
 assert.ok(ui.includes('call<{id:string;status:string;version:number}>("POST",route'));
 assert.ok(!ui.includes("mockData"));
 assert.ok(!ui.includes("fakePatient"));
 assert.ok(!ui.includes("setInterval"));
});
test("static route names and semantics match canonical M32 register",()=>{
 const j=JSON.parse(read("registers/canonical-screens.json"));
 const present=j.screen_register.filter(s=>s.module==="M32").map(s=>s.id).sort();
 assert.deepEqual(present,screens);
});
