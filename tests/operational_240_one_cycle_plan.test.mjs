import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
function read(path){return JSON.parse(readFileSync(new URL("../"+path,import.meta.url),"utf8"));}
const canonical=read("registers/canonical-screens.json").screen_register;
const gaps=read("registers/operational-completion-gaps-v1.json").capabilities;
const batches=read("registers/operational-240-one-cycle-batches-v1.json");
const locked=gaps.filter(x=>x.implementationState==="domain_locked");
const statuses=new Map(gaps.map(x=>[x.screenId,x]));
const flat=batches.batches.flatMap(x=>x.screens.map(s=>({...s,batch:x.id})));
test("one-cycle plan assigns all and only 240 missing integrations exactly once",()=>{
 assert.equal(canonical.length,261);
 assert.equal(batches.summary.plannedEngineeringBatches,30);
 assert.equal(batches.summary.screensPerBatch,8);
 assert.equal(batches.batches.length,30);
 assert.equal(locked.length,240);
 assert.equal(flat.length,240);
 assert.equal(new Set(flat.map(s=>s.id)).size,240);
 assert.deepEqual([...flat.map(s=>s.id)].sort(),locked.map(s=>s.screenId).sort());
});
test("each work package holds exactly eight canonical screen requirements and primary action",()=>{
 for(let i=0;i<batches.batches.length;i++){
  const b=batches.batches[i];
  assert.equal(b.number,i+1);
  assert.equal(b.id,"SYNQ-OPS-"+String(i+1).padStart(3,"0"));
  assert.equal(b.status,"PLANNED_NOT_IMPLEMENTED");
  assert.equal(b.screenCount,8);
  assert.equal(b.screens.length,8);
  assert.equal(b.dependencies.length,4);
  for(const s of b.screens){
   const spec=statuses.get(s.id);
   assert.ok(spec);
   assert.equal(s.status,"MISSING_DOMAIN_INTEGRATION");
   assert.equal(s.accepted,false);
   assert.equal(s.title,spec.title);
   assert.equal(s.module,spec.module);
   assert.equal(s.layout,spec.layout);
   assert.deepEqual(s.dataGroups,spec.canonicalDataGroups);
   assert.equal(s.primaryAction,spec.canonicalPrimaryAction);
   assert.ok(s.domainLane!=="unassigned");
  }
 }
});
test("no static plan makes an unwarranted claim of implemented or clinically accepted screens",()=>{
 assert.equal(batches.summary.alreadyCompletedByThisPlan,0);
 assert.equal(batches.summary.remainingToImplement,240);
 assert.equal(batches.baseline.clinicallyAccepted,0);
 assert.equal(batches.summary.clinicalCertification,"DENIED");
 assert.equal(batches.executionPolicy.noUnverifiedScreenCompletion,true);
 assert.equal(batches.executionPolicy.noSkippedDependencies,true);
 assert.equal(batches.executionPolicy.writeOnlyAfterBackendAuthorizes,true);
 assert.equal(batches.definitionOfDone.length,14);
});
test("every module with a missing integration is covered without changing canonical routes",()=>{
 const expected=new Set(locked.map(x=>x.module));
 const actual=new Set(flat.map(x=>x.module));
 assert.deepEqual(actual,expected);
 assert.equal(actual.size,41);
 const routed=read("registers/screen-routes.json");
 assert.equal(routed.screens.length,261);
 for(const s of flat)assert.equal(routed.screens.find(x=>x.screen_id===s.id).implementation_status,"route_registered_domain_adapter_missing");
});
