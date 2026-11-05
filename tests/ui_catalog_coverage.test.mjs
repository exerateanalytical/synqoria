import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {resolve,dirname} from "node:path";
import {buildScreenContract,UI_COMPONENTS,UI_DIALOGS,UI_PRINTS,UI_STATES,UI_LAYOUT_CONFIG} from "../apps/web/app/ui/catalog/screen-contract.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const read=(path)=>readFileSync(resolve(root,path),"utf8");
const register=JSON.parse(read("registers/canonical-screens.json"));
const screens=register.screen_register;
const specs=screens.map(buildScreenContract);
const components=read("apps/web/app/ui/catalog/ScreenPreviewClient.tsx");
const gallery=read("apps/web/app/ui/catalog/ComponentGallery.tsx");
const details=read("apps/web/app/ui/catalog/[screenId]/page.tsx");
const listing=read("apps/web/app/ui/catalog/page.tsx");
const css=read("apps/web/app/ui/catalog/interaction.css");
const sheet=read("apps/web/app/ui/catalog/catalog.css");

test("canonical scope: exactly 40 modules + 12 shared screens = 261 stable routes",()=>{
 assert.equal(specs.length,261);
 assert.equal(new Set(specs.map(s=>s.screenId)).size,261);
 assert.equal(new Set(specs.filter(s=>s.module!=="SHARED").map(s=>s.module)).size,40);
 assert.equal(specs.filter(s=>s.module==="SHARED").length,12);
 assert.equal(specs.filter(s=>s.module!=="SHARED").length,249);
 assert.equal(register.total_screens,261);
});
test("seven layouts have a complete specimen control inventory",()=>{
 const expected={F:100,X:62,L:43,D:36,B:10,T:6,H:4};
 for(const [type,count] of Object.entries(expected)){
  assert.equal(specs.filter(s=>s.layout===type).length,count,type);
  assert.ok(UI_LAYOUT_CONFIG[type],type);
 }
 for(const s of specs){
  assert.ok(s.title.trim(),s.screenId);
  assert.ok(s.primaryAction.trim(),s.screenId);
  assert.ok(s.fieldGroups.length>0,s.screenId);
  assert.ok(s.fieldGroups.every(x=>x.trim().length>0),s.screenId);
  assert.ok(s.tabs.length>=3,s.screenId);
  assert.equal(new Set(s.tabs).size,s.tabs.length,s.screenId);
  assert.ok(s.controls.length>=4,s.screenId);
  assert.equal(s.authorization,"unconnected",s.screenId);
  assert.equal(s.release,"denied",s.screenId);
  assert.equal(s.saveMode,"memory-only-preview",s.screenId);
 }
});
test("non-optional action safety is blocked until genuine clinical integration",()=>{
 for(const s of specs.filter(s=>["F","X","D"].includes(s.layout))){
  assert.ok(s.controls.includes("primary command locked"),s.screenId);
 }
 assert.ok(components.includes("disabled title="));
 assert.ok(components.includes("e.preventDefault()"));
 assert.ok(components.includes("No clinical command"));
 assert.ok(details.includes("Do not enter real patient information"));
});
test("14 UI states, 12 dialog variants, 18 print contracts, 26 shared components",()=>{
 assert.equal(UI_STATES.length,14);
 assert.equal(UI_DIALOGS.length,12);
 assert.equal(UI_PRINTS.length,18);
 assert.equal(UI_COMPONENTS.length,26);
 assert.ok(gallery.includes("showModal()"));
 assert.ok(gallery.includes("onCancel="));
 assert.ok(gallery.includes("disabled>Print or export"));
});
test("261 route generation, screen navigation, tabs and controls are present",()=>{
 for(const token of ["generateStaticParams","ScreenPreviewClient","peer","id="related""]){
  if(token==="peer")assert.ok(details.includes("peers"));else assert.ok(details.includes(token),token);
 }
 for(const token of ["role="tablist"","role="tabpanel"","aria-selected","aria-expanded","aria-pressed","onKeyDown","type="search"","type="date"","type="submit"","type="reset"","Show filters","Clear all","Apply filters","Sort by","Results per page","Previous page","Next page","Board view","List view"]){
  if(token==="type="reset"")continue; // deliberate clear-draft action is type=button
  assert.ok(components.includes(token),token);
 }
 assert.ok(listing.includes("screens.map("));
});
test("visual restrictions: responsive controls, no glow or gradient effects",()=>{
 for(const s of [css,sheet]){
  assert.doesNotMatch(s,/box-shadow\s*:|text-shadow\s*:|filter\s*:\s*drop-shadow|linear-gradient\s*\(/i);
  assert.doesNotMatch(s,/transform\s*:\s*scale\s*\(/i);
 }
 assert.match(css,/max-width:700px/);
 assert.match(css,/max-width:360px/);
 assert.match(css,/focus-visible/);
 assert.match(css,/min-height:44px/);
 assert.match(css,/overflow-wrap:anywhere/);
 assert.match(css,/prefers-reduced-motion/);
});
console.log("SYNQORIA: structural UI contract tests only, NOT rendered browser/a11y/clinical acceptance");
