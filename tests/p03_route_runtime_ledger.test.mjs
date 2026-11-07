/**
 * SYNQORIA P03-B0093 evidence-accounting/coordination gate.
 * No phantom feature, screen acceptance or production deployment counts.
 */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const filename=new URL("../registers/p03-route-runtime-engineering-2026-11-06.json",import.meta.url);
const ledger=JSON.parse(readFileSync(filename,"utf8"));
const file=path=>existsSync(new URL("../"+path,import.meta.url));
test("B0093 exactly one unique contiguous source evidence record per verified batch",()=>{
 assert.equal(ledger.schema,"synqoria.p03.runtime-routes.v1");
 assert.equal(ledger.cycle.target,30);
 assert.ok([29,30].includes(ledger.cycle.verifiedCount));
 assert.equal(ledger.cycle.verifiedCount,ledger.sourceEvidence.length);
 assert.equal(ledger.cycle.remaining,30-ledger.sourceEvidence.length);
 const expected=ledger.sourceEvidence.map((_,i)=>"P03-B"+String(64+i).padStart(4,"0"));
 assert.deepEqual(ledger.sourceEvidence.map(x=>x.id),expected);
 for(const item of ledger.sourceEvidence){
  assert.equal(item.clinicalAccepted,false);
  assert.equal(item.productionDeployed,false);
  assert.ok(typeof item.description==="string"&&item.description.length>20);
  assert.ok(file(item.source),"missing source: "+item.id+" "+item.source);
  assert.ok(file(item.test),"missing test: "+item.id+" "+item.test);
  assert.equal(item.verifiedBy?.kind,"github_actions");
  assert.equal(item.verifiedBy?.conclusion,"success");
  assert.ok(Number.isSafeInteger(item.runId)&&item.runId>0);
  assert.equal(item.verifiedBy.url,
   "https://github.com/exerateanalytical/synqoria/actions/runs/"+item.runId);
 }
});
test("B0093 P03 local completion never silently closes global engineering or releases clinical software",()=>{
 assert.equal(ledger.globalEngineeringBacklogClosed,false);
 assert.equal(ledger.safety.clinicalRelease,"DENIED");
 assert.equal(ledger.safety.operationalScreenPackagesAccepted,0);
 assert.equal(ledger.safety.screenCount,261);
 assert.equal(ledger.safety.domainLockedPending,240);
 assert.equal(ledger.sourceQualification.fullApplicationBuildVerified,false);
 assert.equal(ledger.sourceQualification.liveIdpDeploymentVerified,false);
 assert.equal(ledger.sourceQualification.clinicalAcceptanceVerified,false);
 assert.equal(ledger.cycle.cycleComplete,ledger.sourceEvidence.length===30);
 assert.ok(Array.isArray(ledger.blockers)&&ledger.blockers.length>=4);
});
test("B0093 handoff and separate build/OPS001 ownership are preserved",()=>{
 const handoff=readFileSync(new URL("../docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md",import.meta.url),"utf8");
 assert.match(handoff,/P03 identity route continuation \(draft PR #31\)/);
 assert.match(handoff,/OPS001 PR #30/);
 assert.match(handoff,/clinical production release \*\*DENIED\*\*/);
 assert.match(handoff,/No direct messages to other ChatGPT chats are asserted/);
});
