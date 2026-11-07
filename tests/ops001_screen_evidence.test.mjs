import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const root=new URL("../",import.meta.url);
const read=path=>JSON.parse(readFileSync(new URL(path,root),"utf8"));
const evidence=read("registers/ops001-partial-implementation-2026-11-06.json");
const plan=read("registers/operational-240-one-cycle-batches-v1.json").batches[0];
const routes=read("registers/screen-routes.json").screens;

test("OPS001 holds precisely the original eight canonical screen IDs",()=>{
 assert.equal(evidence.batchId,"SYNQ-OPS-001");
 assert.deepEqual(evidence.screens.map(x=>x.id),plan.screens.map(x=>x.id));
 assert.equal(evidence.screens.length,8);
 assert.equal(new Set(evidence.screens.map(x=>x.id)).size,8);
 for(const row of evidence.screens){
  assert.equal(row.accepted,false);
  assert.equal(row.route,routes.find(x=>x.screen_id===row.id).route);
  assert.equal(row.canonicalPrimaryAction,plan.screens.find(x=>x.id===row.id).primaryAction);
  assert.ok(row.remainingGates.length>=3);
 }
});
test("OPS001 source-only progress cannot claim clinical acceptance or installed policies",()=>{
 assert.equal(evidence.accepted,false);
 assert.equal(evidence.clinicallyCertified,false);
 assert.equal(evidence.acceptedOperationalPackages,0);
 assert.equal(evidence.domainLockedScreenIntegrationsWithoutFullAcceptance,240);
 assert.equal(evidence.releaseGate,"DENIED");
 assert.equal(evidence.trustedRuntime.installedInProduction,false);
 assert.equal(evidence.trustedRuntime.registrationCommandDeployed,false);
 assert.equal(evidence.screens.filter(x=>x.status==="VERIFIED_PARTIAL").length,5);
 assert.equal(evidence.screens.filter(x=>x.status==="BLOCKED_DEPENDENCY").length,3);
 assert.equal(evidence.focusedTests[0].conclusion,"success");
});
