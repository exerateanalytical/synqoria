import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>JSON.parse(readFileSync(new URL("../"+p,import.meta.url),"utf8"));
const canonical=read("registers/canonical-screens.json").screen_register;
const gaps=read("registers/operational-completion-gaps-v1.json");
const routes=read("registers/screen-routes.json").screens;
const status=new Map(read("registers/operational-screen-progress-v0.1.json").rows.map(x=>[x.id,x]));
test("every canonical screen has a unique gap and actionable source field/action mapping",()=>{
 assert.equal(gaps.capabilities.length,261);
 assert.equal(new Set(gaps.capabilities.map(x=>x.screenId)).size,261);
 assert.deepEqual(gaps.capabilities.map(x=>x.screenId),canonical.map(x=>x.id));
 for(let i=0;i<261;i++){
  const p=gaps.capabilities[i],c=canonical[i];
  assert.equal(p.title,c.name);
  assert.equal(p.layout,c.layout);
  assert.equal(p.canonicalPrimaryAction,c.primary_action);
  assert.ok(p.canonicalDataGroups.length>0);
  assert.ok(p.missingEvidence.length>0);
  assert.equal(p.domainWorkflowAccepted,false);
  assert.equal(p.clinicalCertification,false);
 }
});
test("gap register cannot count source-read subsets as completed clinical commands",()=>{
 assert.equal(gaps.missingDomainIntegration,240);
 assert.equal(gaps.partialOrNonclinical,21);
 assert.equal(gaps.clinicallyAccepted,0);
 assert.equal(gaps.capabilities.filter(x=>x.implementationState==="domain_locked").length,240);
 assert.equal(gaps.capabilities.filter(x=>x.implementationState==="scoped_read_only_partial").length,16);
 assert.equal(gaps.capabilities.filter(x=>x.implementationState==="functional_nonclinical_partial").length,5);
 for(const p of gaps.capabilities)assert.ok(status.has(p.screenId)!==(p.implementationState==="domain_locked"));
 assert.equal(routes.length,261);
});
