import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const file=p=>new URL("../"+p,import.meta.url);
const text=p=>readFileSync(file(p),"utf8");
const d=JSON.parse(text("registers/p03-exact-sql-contract-cycle-2026-11-06.json"));
test("B0153 P03 source cycle is contiguous, source/test backed and CI-evidenced",()=>{
 assert.equal(d.schema,"synqoria.p03.exact-sql-contract-cycle.v1");
 assert.equal(d.parentPullRequest,32);
 assert.equal(d.sourcePullRequest,33);
 assert.equal(d.cycle.target,30);
 assert.equal(d.cycle.verifiedCount,30);
 assert.equal(d.cycle.verifiedCount,d.sourceEvidence.length);
 assert.equal(d.cycle.remaining,30-d.cycle.verifiedCount);
 assert.equal(d.cycle.cycleComplete,true);
 assert.deepEqual(d.sourceEvidence.map(x=>x.id),
  Array.from({length:d.cycle.verifiedCount},
   (_,i)=>"P03-B"+String(124+i).padStart(4,"0")));
 for(const evidence of d.sourceEvidence){
  assert.ok(evidence.description.length>=20,evidence.id);
  assert.equal(existsSync(file(evidence.source)),true,evidence.source);
  assert.equal(existsSync(file(evidence.test)),true,evidence.test);
  assert.equal(evidence.verifiedBy.kind,"github_actions");
  assert.equal(evidence.verifiedBy.conclusion,"success");
  assert.ok(Number.isSafeInteger(evidence.verifiedBy.runId));
  assert.equal(evidence.verifiedBy.url,
   "https://github.com/exerateanalytical/synqoria/actions/runs/"+evidence.verifiedBy.runId);
  assert.equal(evidence.clinicalAccepted,false);
  assert.equal(evidence.productionDeployed,false);
 }
});
test("B0153 local source-cycle completion never declares clinical or programme completion",()=>{
 assert.equal(d.safety.canonicalModules,40);
 assert.equal(d.safety.canonicalScreens,261);
 assert.equal(d.safety.domainLockedPending,240);
 assert.equal(d.safety.operationalPackagesAccepted,0);
 assert.equal(d.safety.globalEngineeringBacklogClosed,false);
 assert.equal(d.safety.screenProductionStageAuthorized,false);
 assert.equal(d.safety.clinicalRelease,"DENIED");
 assert.equal(d.dependencies.reviewedNpmLockMerged,false);
 assert.equal(d.dependencies.liveHospitalIdentityConfigured,false);
 assert.equal(d.dependencies.clinicalDisclosureEndpointDeployed,false);
 assert.equal(d.dependencies.ops001SharedRoutesReconciled,false);
});
test("B0153 shared handoff separates PR owners, source checks, root CI and clinical release",()=>{
 const handoff=text("docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md");
 assert.match(handoff,/P03 B0124–B0153 SQL contract source cycle/);
 assert.match(handoff,/OPS001 PR #30/);
 assert.match(handoff,/PR #23\/PR #24/);
 assert.match(handoff,/root engineering workflow still fails/);
 assert.match(handoff,/clinical release DENIED/);
});
