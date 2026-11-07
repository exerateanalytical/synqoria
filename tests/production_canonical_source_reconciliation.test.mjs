import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=path=>readFileSync(path,"utf8");
const canonical=JSON.parse(read("registers/canonical-screens.json")).screen_register;
const modules=JSON.parse(read("registers/canonical-modules.json")).module_register;
const routes=JSON.parse(read("registers/screen-routes.json")).screens;
const status=JSON.parse(read("registers/screen-status-matrix.json")).items;
const expected=["M02-S01","M02-S02","M02-S03","M02-S04","M02-S05","M16-S01","M16-S02","M16-S03","M16-S04","M16-S05","M35-S01","M35-S02","M35-S03","M35-S04","M35-S05","M35-S06"];
test("Canonical registry never creates extra screens or changes module assignments",()=>{
 assert.equal(canonical.length,261);assert.equal(modules.length,40);
 assert.equal(new Set(canonical.map(x=>x.id)).size,261);
 assert.deepEqual(canonical.filter(x=>["M02","M16","M35"].includes(x.module)).map(x=>x.id).sort(),expected);
});
test("One authoritative application URL per integrated screen; alternative routes only redirect",()=>{
 const page=read("apps/web/app/workforce/clinical/[screenId]/page.tsx");
 for(const id of ["M02","M16","M35"])assert.ok(page.includes('screen.module==="'+id+'"'));
 for(const [mod,slug] of [["M02","scheduling"],["M16","referrals"],["M35","integrations"]]){
  const alt=read("apps/web/app/workforce/"+slug+"/[screen]/page.tsx");
  assert.ok(alt.includes('redirect("/workforce/clinical/"+screen)'));
  assert.ok(!alt.includes("Workbench"));
  const ui=read("apps/web/components/"+slug+"/"+(mod==="M02"?"Scheduling":mod==="M16"?"Referral":"Integration")+"Workbench.tsx");
  assert.ok(ui.includes('"/workforce/clinical/"+'));
 }
 for(const id of expected){
  const route=routes.find(x=>x.screen_id===id);
  assert.equal(route.route,"/workforce/clinical/"+id);
  assert.equal(route.implementation_status,"source_api_workflow_ci_verified_unaccepted");
  const st=status.find(x=>x.id===id);
  assert.equal(st.implemented,true);
  assert.equal(st.tested,true);
  assert.equal(st.accepted,false);
 }
});
test("Canonical display module labels are derived from authority register, not stale hard-coded module names",()=>{
 const page=read("apps/web/app/workforce/clinical/[screenId]/page.tsx");
 assert.ok(page.includes("modules.module_register.map"));
 assert.ok(!page.includes('M16:"Maternal'));
 assert.ok(!page.includes('M35:"Quality'));
});
