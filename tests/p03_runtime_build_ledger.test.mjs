import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
const d=JSON.parse(read("registers/p03-runtime-build-engineering-2026-11-06.json"));
test("P03 B0123 ledger is contiguous, unique, source/test backed with completed CI evidence",()=>{
 assert.equal(d.schema,"synqoria.p03.production-runtime-build.v1");
 assert.equal(d.cycle.target,30);
 assert.equal(d.cycle.verifiedCount,30);
 assert.equal(d.cycle.verifiedCount,d.sourceEvidence.length);
 assert.equal(d.cycle.remaining,30-d.cycle.verifiedCount);
 assert.equal(d.cycle.cycleComplete,true);
 assert.deepEqual(d.sourceEvidence.map(x=>x.id),
  Array.from({length:d.cycle.verifiedCount},(_,i)=>"P03-B"+String(94+i).padStart(4,"0")));
 for(const e of d.sourceEvidence){
  assert.ok(e.description.length>40,e.id);
  assert.ok(existsSync(new URL("../"+e.source,import.meta.url)),e.source);
  assert.ok(existsSync(new URL("../"+e.test,import.meta.url)),e.test);
  assert.equal(e.verifiedBy.kind,"github_actions");
  assert.equal(e.verifiedBy.conclusion,"success");
  assert.ok(Number.isSafeInteger(e.verifiedBy.runId));
  assert.equal(e.verifiedBy.url,
   "https://github.com/exerateanalytical/synqoria/actions/runs/"+e.verifiedBy.runId);
  assert.equal(e.clinicalAccepted,false);
  assert.equal(e.productionDeployed,false);
 }
});
test("P03 B0123 local engineering cycle never implies global engineering or clinical acceptance",()=>{
 assert.equal(d.globalEngineeringBacklogClosed,false);
 assert.equal(d.safety.canonicalModules,40);
 assert.equal(d.safety.canonicalScreens,261);
 assert.equal(d.safety.domainLockedPending,240);
 assert.equal(d.safety.operationalPackagesAccepted,0);
 assert.equal(d.safety.clinicalRelease,"DENIED");
 assert.equal(d.dependencies.normalEngineeringWorkflowPassing,false);
 assert.equal(d.dependencies.liveHospitalIdentityConfigured,false);
 assert.equal(d.dependencies.clinicalDisclosureEndpointDeployed,false);
});
test("P03 B0123 shared GitHub handoff identifies ownership, build, OPS001 and unfinished clinical stage",()=>{
 const txt=read("docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md");
 assert.match(txt,/P03 follow-up source engineering B0112–B0122/);
 assert.match(txt,/OPS001 PR #30/);
 assert.match(txt,/clinical release \*\*DENIED\*\*/);
 assert.match(txt,/production stage NOT AUTHORIZED/);
});
