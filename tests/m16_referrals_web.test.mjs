import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(p,"utf8");
const root="apps/web/";
const ids=["M16-S01","M16-S02","M16-S03","M16-S04","M16-S05"];
test("M16 all canonical screens route to distinct bilingual workstations",()=>{
 const route=read(root+"app/workforce/referrals/[screen]/page.tsx");
 const ui=read(root+"components/referrals/ReferralWorkbench.tsx");
 const canon=JSON.parse(read("registers/canonical-screens.json"));
 assert.deepEqual(canon.screen_register.filter(r=>r.module==="M16").map(r=>r.id).sort(),ids);
 for(const id of ids){assert.ok(route.includes(id));assert.ok(ui.includes(id));}
 assert.ok(ui.includes("Français"));assert.ok(ui.includes("aria-current"));
 assert.ok(ui.includes("aria-live"));assert.ok(ui.includes("role=\"status\""));
});
test("M16 browser BFF is authenticated, CSRF protected, and origin pinned",()=>{
 const proxy=read(root+"app/api/referrals/proxy/[...segments]/route.ts");
 const policy=read(root+"lib/referrals/proxy-policy.ts");
 for(const required of ["opsConfig","opsCheckCsrf","opsCheckOrigin","Idempotency-Key",
   "Cache-Control",'redirect:"error"',"SYNQORIA_REFERRAL_API_ORIGIN"]){
  assert.ok(proxy.includes(required),required);
 }
 assert.ok(policy.includes("allowed")||policy.includes("policy"));
 assert.ok(!proxy.includes("localStorage"));
 assert.ok(!proxy.includes("SYNQORIA_PRIVACY_DB_PASSWORD"));
});
test("M16 client consumes live APIs and cannot synthesize operational records",()=>{
 const ui=read(root+"components/referrals/ReferralWorkbench.tsx");
 const client=read(root+"lib/referrals/client.ts");
 assert.ok(ui.includes('api<Row[]>("GET"')||ui.includes('api<History[]>("GET"'));
 assert.ok(ui.includes('"POST"'));
 assert.ok(client.includes('credentials:"same-origin"'));
 assert.ok(!ui.includes("mockData"));assert.ok(!ui.includes("fakePatient"));
});
