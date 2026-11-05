import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync} from "node:fs";
const root=new URL("../",import.meta.url);
const read=p=>JSON.parse(readFileSync(new URL(p,root),"utf8"));
const register=read("registers/p03-identity-engineering-2026-11-05.json");
test("P03 run accounts for individually verified source increments, no fabricated batches",()=>{
 assert.equal(register.intent.requestedRunSize,30);
 assert.ok(register.intent.executedVerified>=20&&register.intent.executedVerified<=30);
 assert.equal(register.intent.unimplementedInThisRun,30-register.intent.executedVerified);
 assert.equal(register.intent.runStatus,register.intent.executedVerified===30?"FOCUSED_VERIFIED_ONLY":"INCOMPLETE");
 assert.equal(register.batches.length,register.intent.executedVerified);
 const expected=Array.from({length:register.intent.executedVerified},(_,i)=>"P03-B"+String(i+34).padStart(4,"0"));
 assert.deepEqual(register.batches.map(x=>x.id),expected);
 assert.equal(new Set(register.batches.map(x=>x.id)).size,10);
 assert.equal(new Set(register.batches.map(x=>x.verifiedBy.runId)).size,register.batches.length);
 for(const item of register.batches){
  assert.equal(item.verifiedBy.conclusion,"success");
  assert.match(item.verifiedBy.url,/^https:\/\/github\.com\/exerateanalytical\/synqoria\/actions\/runs\/[0-9]+$/);
  assert.ok(existsSync(new URL(item.source,root)),item.source);
  assert.ok(existsSync(new URL(item.test,root)),item.test);
  assert.equal(item.clinicalScreenAccepted,false);
  assert.equal(item.status,"IMPLEMENTED_FOCUSED_TESTED_NOT_PRODUCTION_INTEGRATED");
 }
});
test("P03 engineering foundation cannot accidentally mark patient screens or hospital release complete",()=>{
 assert.equal(register.boundary.stage,"engineering-foundations");
 assert.equal(register.boundary.clinicalRelease,"DENIED");
 assert.equal(register.boundary.canonicalScreens,261);
 assert.equal(register.boundary.canonicalModules,40);
 assert.equal(register.boundary.domainLockedScreenIntegrations,240);
 assert.equal(register.boundary.acceptedEightScreenOperationalBatches,0);
 assert.equal(register.boundary.clinicalScreenAcceptance,0);
 assert.equal(register.boundary.productionIdentityDeployment,false);
});
