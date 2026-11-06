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
 const details=read("apps/web/app/workforce/operations/[module]/[screen]/page.tsx");
 assert.match(details,/disabled aria-disabled="true"/);
 assert.match(details,/notFound\(\)/);
 assert.match(details,/generateStaticParams/);
 const css=read("apps/web/app/workforce/operations/operations.module.css");
 assert.match(css,/@media\(max-width:480px\)/);
 assert.doesNotMatch(css,/box-shadow:\s*0[^;]*rgba/);
});
