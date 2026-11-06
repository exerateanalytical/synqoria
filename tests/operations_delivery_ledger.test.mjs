import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
const d=JSON.parse(read("registers/operations-seven-delivery-2026-11-06.json"));
const canonical=JSON.parse(read("registers/canonical-modules.json"));
const screens=JSON.parse(read("registers/canonical-screens.json")).screen_register;
test("Seven module delivery ledger matches canonical IDs/counts and does not fake production completion",()=>{
 assert.equal(d.schema,"synqoria.operations.seven.delivery.v1");
 assert.equal(d.targetModuleCount,7);
 assert.equal(d.modules.length,7);
 assert.equal(new Set(d.modules.map(x=>x.id)).size,7);
 assert.equal(d.modules.reduce((n,x)=>n+x.canonicalScreens,0),35);
 for(const m of d.modules){
  assert.ok(canonical.module_register.some(x=>x.id===m.id));
  assert.equal(screens.filter(x=>x.module===m.id).length,m.canonicalScreens);
  assert.ok(m.javaWorkflowFoundations);
  assert.equal(m.staticPreviewScreens,m.canonicalScreens);
  assert.equal(m.fullOperationalIntegration,false);
  assert.equal(m.productionCompleted,false);
  assert.equal(m.accepted,false);
  assert.ok(m.unresolved.length>=3);
 }
 assert.equal(d.actualProductionModulesCompleted,0);
 assert.equal(d.clinicalOrOperationalScreenAccepted,0);
 assert.equal(d.clinicalRelease,"DENIED");
 assert.equal(d.globalEngineeringBacklogClosed,false);
 assert.equal(d.dependencies.authoritativeWorkforceGrant,true);
});
test("Handoff says source only and records actual role/DB/stock/API evidence",()=>{
 const handoff=read("docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md");
 assert.match(handoff,/seven administrative modules/);
 assert.match(handoff,/0\/7 full modules completed/);
 assert.match(handoff,/Clinical release DENIED/);
 for(const file of [d.sources.plan,d.sources.postgres,d.sources.uiContracts]){
  assert.ok(existsSync(new URL("../"+file,import.meta.url)),file);
 }
});
