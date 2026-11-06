import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const root=new URL("../",import.meta.url);
const read=p=>readFileSync(new URL(p,root),"utf8");
const canonical=JSON.parse(read("registers/canonical-screens.json")).screen_register;
const c=JSON.parse(read("apps/web/lib/operations-screen-catalog.json"));
const allowed=new Set(["M21","M22","M23","M25","M29","M31","M37"]);
test("seven admin module UI preview contracts preserve all 35 canonical screen IDs and actions",()=>{
 assert.equal(c.schema,"synqoria.admin.seven.screen-contract.v1");
 assert.equal(c.status,"locked_preview_only");
 assert.equal(c.moduleCount,7);assert.equal(c.screenCount,35);
 assert.deepEqual(new Set(c.modules.map(m=>m.id)),allowed);
 const screens=c.modules.flatMap(m=>m.screens);
 assert.equal(screens.length,35);
 assert.equal(new Set(screens.map(s=>s.id)).size,35);
 for(const s of screens){
  const old=canonical.find(x=>x.id===s.id);
  assert.ok(old,s.id);
  assert.equal(old.name,s.name);
  assert.equal(old.primary_action,s.primaryAction);
  assert.equal(old.data_input_groups,s.dataInputGroups);
  assert.equal(s.status,"LOCKED_PREVIEW");
  assert.ok(s.nameFr.length>4);
 }
});
test("operational UI preview is responsive and cannot execute unverified privileged actions",()=>{
 assert.ok(existsSync(new URL("apps/web/app/workforce/operations/page.tsx",root)));
 assert.ok(existsSync(new URL("apps/web/app/workforce/operations/[module]/[screen]/page.tsx",root)));
 const flows=JSON.parse(read("apps/web/lib/operations-screen-flows.json"));
 assert.equal(Object.keys(flows.flows).length,35);
 for(const module of c.modules){
  for(const screen of module.screens){
   const flow=flows.flows[screen.id];
   assert.ok(flow,screen.id);
   assert.equal(flow.module,module.id);
   assert.ok(flow.fields.length>=1);
   assert.ok(flow.recordTypes.length>=1);
   assert.ok(flow.safety.length>=5);
  }
 }
 const workbench=read("apps/web/components/OperationsWorkbench.tsx");
 for(const token of ["viewItems","allowedActions","allowedMovements","displayedFields"])
  assert.match(workbench,new RegExp(token));
 const details=read("apps/web/app/workforce/operations/[module]/[screen]/page.tsx");
 assert.match(details,/OperationsWorkbench/);
 const ui=read("apps/web/components/OperationsWorkbench.tsx");
 assert.match(ui,/M21/);
 assert.match(ui,/M22/);
 assert.match(ui,/M23/);
 assert.match(ui,/M25/);
 assert.match(ui,/M29/);
 assert.match(ui,/M31/);
 assert.match(ui,/M37/);
 assert.match(ui,/expectedVersion/);
 assert.match(ui,/csrf\(\)/);
 assert.match(ui,/stock movement/i);
 const proxy=read("apps/web/app/api/operations/proxy/[...segments]/route.ts");
 assert.match(proxy,/opsCheckCsrf/);
 assert.match(proxy,/opsCheckOrigin/);
 assert.match(proxy,/OPS_COOKIE.token/);
 const oidc=read("apps/web/lib/operations-auth.ts");
 assert.match(oidc,/timingSafeEqual/);
 assert.match(oidc,/opsChallenge/);
 assert.match(details,/notFound\(\)/);
 assert.match(details,/generateStaticParams/);
 const css=read("apps/web/app/workforce/operations/operations.module.css");
 assert.match(css,/@media\(max-width:480px\)/);
 assert.doesNotMatch(css,/box-shadow:\s*0[^;]*rgba/);
});
