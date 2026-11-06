import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const file=path=>existsSync(new URL("../"+path,import.meta.url));
const data=JSON.parse(readFileSync(new URL("../registers/p03-runtime-build-engineering-2026-11-06.json",import.meta.url),"utf8"));
test("B0111 next-cycle ledger records unique IDs and exact real CI source/test provenance",()=>{
 assert.equal(data.schema,"synqoria.p03.production-runtime-build.v1");
 assert.equal(data.cycle.target,30);
 assert.ok([17,18].includes(data.cycle.verifiedCount));
 assert.equal(data.cycle.verifiedCount,data.sourceEvidence.length);
 assert.equal(data.cycle.remaining,30-data.sourceEvidence.length);
 assert.equal(data.cycle.cycleComplete,false);
 assert.deepEqual(data.sourceEvidence.map(x=>x.id),data.sourceEvidence.map((_,i)=>
  "P03-B"+String(94+i).padStart(4,"0")));
 for(const ev of data.sourceEvidence){
  assert.ok(file(ev.source),"missing source: "+ev.id);
  assert.ok(file(ev.test),"missing test: "+ev.id);
  assert.ok(ev.description.length>40);
  assert.equal(ev.verifiedBy.kind,"github_actions");
  assert.equal(ev.verifiedBy.conclusion,"success");
  assert.ok(Number.isSafeInteger(ev.verifiedBy.runId));
  assert.equal(ev.verifiedBy.url,"https://github.com/exerateanalytical/synqoria/actions/runs/"+ev.verifiedBy.runId);
  assert.equal(ev.clinicalAccepted,false);
  assert.equal(ev.productionDeployed,false);
 }
});
test("B0111 no source batch accepted as a clinical screen or global engineering release",()=>{
 assert.equal(data.globalEngineeringBacklogClosed,false);
 assert.equal(data.safety.canonicalModules,40);
 assert.equal(data.safety.canonicalScreens,261);
 assert.equal(data.safety.domainLockedPending,240);
 assert.equal(data.safety.operationalPackagesAccepted,0);
 assert.equal(data.safety.clinicalRelease,"DENIED");
 assert.equal(data.dependencies.normalEngineeringWorkflowPassing,false);
 assert.equal(data.dependencies.liveHospitalIdentityConfigured,false);
 assert.equal(data.dependencies.clinicalDisclosureEndpointDeployed,false);
});
test("B0111 handoff identifies owned branch, remaining work and parent PR conflicts",()=>{
 const handoff=readFileSync(new URL("../docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md",import.meta.url),"utf8");
 assert.match(handoff,/P03 runtime and actual database TLS continuation \(draft PR #32\)/);
 assert.match(handoff,/OPS001 PR #30/);
 assert.match(handoff,/13\/30 still UNIMPLEMENTED/);
 assert.match(handoff,/clinical release \*\*DENIED\*\*/);
});
