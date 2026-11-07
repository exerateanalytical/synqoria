import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=path=>readFileSync(new URL("../"+path,import.meta.url),"utf8");
const source=JSON.parse(read("registers/canonical-screens.json")).screen_register;
const m=JSON.parse(read("apps/web/lib/mortuary-screen-catalog.json"));
const ids=["M27-S01","M27-S02","M27-S03","M27-S04","M27-S05"];
test("M27 5 canonical screen IDs, labels and actions are unchanged",()=>{
 assert.equal(m.module,"M27");assert.equal(m.screens.length,5);
 assert.deepEqual(m.screens.map(x=>x.id),ids);
 for(const screen of m.screens){
  const canon=source.find(x=>x.id===screen.id);
  assert.ok(canon);
  assert.equal(screen.name,canon.name);
  assert.equal(screen.primaryAction,canon.primary_action);
  assert.notEqual(screen.nameFr,"");
 }
 assert.equal(m.acceptance,"source_candidate_not_institutionally_accepted");
});
test("M27 workstations use a separate five-screen layout with OIDC BFF, CSRF and no client bearer token",()=>{
 const dynamic=read("apps/web/app/workforce/operations/[module]/[screen]/page.tsx");
 assert.match(dynamic,/redirect\("\/workforce\/clinical\/"\+screen\)/);
 assert.match(dynamic,/mortuary\.screens\.map/);
 const canonicalRoute=read("apps/web/app/workforce/clinical/[screenId]/page.tsx");
 assert.match(canonicalRoute,/MortuaryWorkbench/);
 assert.match(canonicalRoute,/screen\.module==="M27"/);
 const ui=read("apps/web/components/MortuaryWorkbench.tsx");
 for(const id of ids)assert.match(ui,new RegExp(id));
 for(const name of ["REVIEW_RELEASE","AUTHORIZE_RELEASE","HANDOVER","IDENTIFY","STORE","TRANSFER","RECEIVE"]){
  assert.match(ui,new RegExp(name));
 }
 assert.match(ui,/csrf\(\)/);
 assert.match(ui,/crypto\.randomUUID/);
 assert.doesNotMatch(ui,/localStorage|sessionStorage|Bearer /);
 assert.match(ui,/role="alert"/);
 assert.match(ui,/screenId/);
 const proxy=read("apps/web/app/api/operations/proxy/[...segments]/route.ts");
 for(const token of ["mortuaryActions","mortuaryCustody","mortuaryApprovals","mortuaryHolds","mortuaryAudit","mortuaryList"]){
  assert.match(proxy,new RegExp(token));
 }
 assert.match(proxy,/opsCheckCsrf/);
 assert.match(proxy,/opsCheckOrigin/);
 assert.match(proxy,/OPS_COOKIE.token/);
 const css=read("apps/web/app/workforce/mortuary/mortuary.module.css");
 assert.match(css,/@media\(max-width:640px\)/);
 assert.match(css,/:focus-visible/);
 assert.doesNotMatch(css,/box-shadow:/);
});
test("Mortuary SQL schema denies external authority spoofing and enforces signed custody & dual review",()=>{
 const sql=read("services/operations-core/db/migration/V005__mortuary_custody_release.sql");
 for(const table of ["mortuary_cases","mortuary_authority_evidence","mortuary_legal_holds",
  "mortuary_custody_events","mortuary_release_reviews","mortuary_commands","mortuary_audit_events","mortuary_outbox"])
  assert.match(sql,new RegExp("CREATE TABLE synqoria_operations\\."+table+"\\s*\\("));
 assert.match(sql,/FORCE ROW LEVEL SECURITY/);
 assert.match(sql,/GRANT SELECT ON synqoria_operations\.mortuary_authority_evidence/);
 assert.match(sql,/GRANT SELECT ON synqoria_operations\.mortuary_legal_holds/);
 assert.match(sql,/UNIQUE\(cell_id,facility_id,case_id,stage\)/);
 assert.match(sql,/UNIQUE\(cell_id,facility_id,case_id,actor_id\)/);
});

test("Legal holds are viewed only through the role-scoped staff gateway",()=>{
 const api=read("services/operations-core/src/main/java/org/synqoria/operations/mortuary/MortuaryApi.java");
 const store=read("services/operations-core/src/main/java/org/synqoria/operations/mortuary/MortuaryStore.java");
 const ui=read("apps/web/components/MortuaryWorkbench.tsx");
 const contract=read("contracts/openapi/synqoria-m27-mortuary-v1.yaml");
 assert.match(api,/cases\/\{id\}\/holds/);
 assert.match(store,/actor.require\("m27:review"\)/);
 assert.match(ui,/Legal release holds/);
 assert.match(contract,/readMortuaryLegalHolds/);
});
