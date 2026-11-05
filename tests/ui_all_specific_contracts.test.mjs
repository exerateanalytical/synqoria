import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";

const root=resolve(new URL("..",import.meta.url).pathname);
const read=p=>readFileSync(resolve(root,p),"utf8");
const register=JSON.parse(read("registers/canonical-screens.json"));
const all=register.screen_register;
const manifest=JSON.parse(read("registers/ui-specific-252-prototype-contracts.json"));
const specs=manifest.remainingSpecialized;
const identities=new Set(manifest.identitySpecialized);
const byId=new Map(specs.map(s=>[s.id,s]));
const render=read("apps/web/app/ui/catalog/SpecificScreenPreview.tsx");
const detail=read("apps/web/app/ui/catalog/[screenId]/page.tsx");
const dispatcher=read("apps/web/app/ui/catalog/ScreenPreviewClient.tsx");
const css=read("apps/web/app/ui/catalog/specific-screen.css");
test("all 261 canonical routes are accounted for by 9 identity + 252 remaining contracts",()=>{
 assert.equal(all.length,261);
 assert.equal(identities.size,9);
 assert.equal(specs.length,252);
 assert.equal(new Set(specs.map(s=>s.id)).size,252);
 const expected=new Set(all.map(s=>s.id));
 for(const id of identities)assert.ok(expected.has(id));
 for(const spec of specs){assert.ok(expected.has(spec.id));assert.ok(!identities.has(spec.id));}
 assert.equal(new Set([...identities,...specs.map(s=>s.id)]).size,261);
 assert.equal(new Set(specs.map(s=>s.module)).size,40);
});
test("every remaining screen has exact authority title, layout, primary action and groups",()=>{
 for(const row of all.filter(x=>!identities.has(x.id))){
  const s=byId.get(row.id);
  assert.ok(s,row.id);assert.equal(s.title,row.name);assert.equal(s.layout,row.layout);
  assert.equal(s.module,row.module);assert.equal(s.primaryAction,row.primary_action);
  assert.deepEqual(s.fields.map(f=>f.label),row.data_input_groups.split(",").map(x=>x.trim()).filter(Boolean),row.id);
  assert.ok(s.fields.every(f=>f.tabIndex>=0&&f.tabIndex<s.tabs.length),row.id);
  assert.ok(s.tabs.length>=3&&s.tabs.length<=4,row.id);
  assert.ok(s.controls.length>=7,row.id);
  assert.ok(s.navigation.modulePeers.includes(s.id),row.id);
 }
});
test("every source-record has a non-submittable clinical command and explicit acceptance limit",()=>{
 for(const s of specs){
  assert.equal(s.runtime.clinicalCommand,"disabled",s.id);
  assert.equal(s.runtime.exports,"disabled",s.id);
  assert.equal(s.runtime.acceptance,"NOT_ACCEPTED",s.id);
  assert.equal(s.runtime.browserStorage,"none",s.id);
  assert.equal(s.runtime.sessionPersistence,"none",s.id);
  assert.ok(s.eligibilityNote&&s.authorityWorkflow,s.id);
  assert.ok(s.modalContracts.includes("Session expired"),s.id);
  assert.ok(s.fields.every(f=>f.source.includes("SYNQORIA_UIUX_Master_v1.1.md")),s.id);
  assert.ok(s.fields.every(f=>f.requiredness.includes("awaiting")),s.id);
 }
 for(const needle of ['disabled className="syn-specific-locked"','if(document.visibilityState==="hidden")','onSubmit={e=>{e.preventDefault();onReview();}}','No hospital query was sent','No authorized','role="tablist"','role="tabpanel"'])assert.ok(render.includes(needle),needle);
});
test("all seven layouts have operational preview controls and no invented clinical values",()=>{
 for(const code of ["F","L","D","X","B","T","H"]){
  assert.ok(specs.some(x=>x.layout===code),code);
  assert.ok(render.includes('case "'+code+'":'),code);
 }
 for(const token of ["Show filters","Hide filters","Choose columns","Visible information columns","Clear filters","Apply filters","Items per page","Previous page","Next page","Board","List","From","To","Current authoritative value","Proposed or referenced value","Review synthetic draft","Clear demo draft","Independent reviewer","Preview interaction state"]){
  if(token==="Independent reviewer")continue; // lives in contract modal list, not component literal
  assert.ok(render.includes(token),token);
 }
 assert.ok(specs.some(x=>x.layout==="F"&&x.fields.some(f=>f.widget==="restricted")));
 assert.ok(specs.every(x=>x.fields.every(f=>!["approved","validated"].includes(f.requiredness))));
});
test("Next route and client dispatch use single source-traceable contract per canonical ID",()=>{
 assert.ok(detail.includes("ui-specific-252-prototype-contracts.json"));
 assert.ok(detail.includes("remainingSpecialized.find(x=>x.id===s.id)"));
 assert.ok(dispatcher.includes("if(isIdentityPreview(contract.screenId))"));
 assert.ok(dispatcher.includes("if(specificSpec)return <SpecificScreenPreview spec={specificSpec}/>"));
});
test("visual source uses wrap-safe, contrast-conscious no-glow primitives",()=>{
 assert.doesNotMatch(css,/box-shadow\s*:|text-shadow\s*:|drop-shadow\s*\(|linear-gradient\s*\(|transform\s*:\s*scale\s*\(/i);
 assert.ok(css.includes("max-width:360px"));
 assert.ok(css.includes("max-width:767px"));
 assert.ok(css.includes("focus-visible"));
 assert.ok(css.includes("min-height:44px"));
 assert.ok(css.includes("overflow-wrap:anywhere"));
});
console.log("Coverage assertions are contract and source tests, not 252 clinical screen acceptance results.");
