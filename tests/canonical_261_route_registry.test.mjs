import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

function read(path){return JSON.parse(readFileSync(new URL("../"+path,import.meta.url),"utf8"));}
const canonical=read("registers/canonical-screens.json").screen_register;
const routes=read("registers/screen-routes.json");
const implementation=read("registers/operational-screen-progress-v0.1.json");
const allowed=new Set(["api_read_subset_not_clinically_complete","nonclinical_functional","route_registered_domain_adapter_missing"]);
const integrated=new Map(implementation.rows.map(s=>[s.id,s]));
test("all 261 canonical IDs have exactly one real application URL with no route omissions",()=>{
 assert.equal(canonical.length,261);
 assert.equal(routes.screens.length,261);
 assert.equal(new Set(canonical.map(s=>s.id)).size,261);
 assert.equal(new Set(routes.screens.map(s=>s.screen_id)).size,261);
 assert.equal(new Set(routes.screens.map(s=>s.route)).size,261);
 for(const [index,s] of canonical.entries()){
  const r=routes.screens[index];
  assert.equal(r.screen_id,s.id);
  assert.equal(r.module,s.module);
  assert.equal(r.route,"/workforce/clinical/"+s.id);
  assert.ok(allowed.has(r.implementation_status),s.id+" bad status");
  assert.equal(r.clinical_accepted,false);
  assert.equal(r.clinical_commands_enabled,false);
 }
});
test("route-only integration is never misreported as completed clinical work",()=>{
 assert.equal(routes.route_summary.registered,261);
 assert.equal(routes.route_summary.domainLocked,251);
 assert.equal(routes.route_summary.apiReadSubsets,5);
 assert.equal(routes.route_summary.nonclinicalFunctional,5);
 assert.equal(implementation.routedScreenUrls,261);
 assert.equal(implementation.routeSlices,10);
 assert.equal(implementation.routeOnlyLocked,251);
 assert.equal(implementation.remainingWithoutOperationalRoute,251);
 assert.equal(implementation.clinicallyAccepted,0);
 assert.equal(implementation.clinicallyCertifiedModules,0);
 assert.equal(implementation.clinicalRelease,"DENIED");
 const totals={};
 for(const r of routes.screens)totals[r.implementation_status]=(totals[r.implementation_status]||0)+1;
 assert.deepEqual(totals,{route_registered_domain_adapter_missing:251,api_read_subset_not_clinically_complete:5,nonclinical_functional:5});
});
test("all 40 modules and shared routing families are covered, with explicit data groups",()=>{
 assert.equal(new Set(canonical.map(s=>s.module)).size,41);
 assert.deepEqual(new Set(canonical.map(s=>s.layout)),new Set(["F","L","D","X","B","T","H"]));
 for(const s of canonical){
  assert.ok(s.name.trim());
  assert.ok(s.data_input_groups.trim(),s.id+" missing input groups");
  assert.ok(s.primary_action.trim(),s.id+" missing action intent");
 }
});
test("all 10 established functional/read subsets are preserved without shadowing",()=>{
 const source=readFileSync(new URL("../apps/web/app/workforce/clinical/[screenId]/page.tsx",import.meta.url),"utf8");
 for(const id of integrated.keys())assert.ok(canonical.some(x=>x.id===id));
 assert.equal(integrated.size,10);
 for(const s of integrated.values()){
  const r=routes.screens.find(x=>x.screen_id===s.id);
  assert.equal(r.implementation_status,s.category==="verified-api-read-subset"?"api_read_subset_not_clinically_complete":"nonclinical_functional");
 }
 assert.ok(source.includes("if(operational.has(screenId))"));
 assert.ok(source.includes("return <ClinicalReadWorkspace"));
 assert.ok(source.includes("return <TriageQueueWorkspace"));
 assert.ok(source.includes("return <StaffPreferences"));
 assert.ok(source.includes("return <UnintegratedScreen"));
 assert.ok(source.includes("notFound()"));
});
test("domain workstations cannot claim server mutation, PHI persistence, or clinical acceptance",()=>{
 const source=readFileSync(new URL("../apps/web/app/workforce/clinical/UnintegratedScreen.tsx",import.meta.url),"utf8");
 assert.ok(source.includes("data-domain-state=\"unintegrated\""));
 assert.ok(source.includes("disabled title={t.disabled}"));
 assert.ok(source.includes("credentials:\"same-origin\""));
 assert.ok(source.includes("cache:\"no-store\""));
 assert.ok(!source.includes("localStorage"));
 assert.ok(!source.includes("indexedDB"));
 assert.ok(!source.includes("method:\"POST\""));
 assert.ok(!source.includes("method:\"PUT\""));
 assert.ok(!source.includes("method:\"PATCH\""));
 assert.ok(!source.includes("method:\"DELETE\""));
});
test("both document catalogue families remain linked without clinical issuance",()=>{
 const source=readFileSync(new URL("../apps/web/app/workforce/clinical/[screenId]/page.tsx",import.meta.url),"utf8");
 assert.ok(source.includes("DOCUMENT_CATALOG"));
 assert.ok(source.includes("documents.templates"));
 assert.ok(source.includes("satisfies DocumentLink[]"));
 assert.ok(source.includes("redirect(\"/hospital-documents\")"));
 assert.ok(source.includes("redirect(\"/ui/printables\")"));
});
