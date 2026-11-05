import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {dirname} from "node:path";
import {CANONICAL_PRINT_CODES,DOCUMENT_TABLE_COLUMNS,DOCUMENT_RELEASE,validateHospitalCatalog,resolveTemplate} from "../apps/web/app/documents/document-runtime.mjs";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const read=path=>readFileSync(resolve(root,path),"utf8");
const manifest=JSON.parse(read("registers/hospital-document-template-catalog-v1.json"));
const library=JSON.parse(read("registers/hospital-document-field-library.json")).fieldGroups;
const docs=manifest.templates;
const layout=read("apps/web/app/documents/DocumentRenderer.tsx");
const styles=read("apps/web/app/documents/document-print.css");
const list=read("apps/web/app/documents/DocumentCatalog.tsx");

test("304 distinct documented template IDs in all forty modules",()=>{
 assert.equal(docs.length,304);
 assert.equal(new Set(docs.map(x=>x.id)).size,304);
 assert.equal(new Set(docs.map(x=>x.module)).size,40);
 assert.equal(manifest.summary.canonical,18);
 assert.equal(manifest.summary.extensions,286);
 for(const [code,label] of Object.entries(manifest.canonicalP01toP18)){
  const match=docs.filter(x=>x.id===code);
  assert.equal(match.length,1,code);
  assert.equal(match[0].canonicalPrintCode,code);
  assert.equal(match[0].title,label);
 }
 assert.equal(CANONICAL_PRINT_CODES.length,18);
});
test("each template has full field sets, safe access and no clinical release",()=>{
 const errors=validateHospitalCatalog(manifest,library);
 assert.deepEqual(errors,[]);
 for(const doc of docs){
  const resolved=resolveTemplate(doc,library);
  assert.equal(resolved.sections.length,doc.groupKeys.length);
  assert.ok(resolved.sections.every(s=>s.fields.length>=4),doc.id);
  assert.ok(resolved.sections.flatMap(s=>s.fields).every(f=>f.recordValue===false),doc.id);
  assert.equal(doc.release.mode,"SAMPLE_ONLY");
  assert.equal(doc.release.legal,"UNREVIEWED");
  assert.equal(doc.release.signatures,"UNVERIFIED");
  assert.equal(doc.release.dataSource,"NO_LIVE_DATA");
  assert.equal(doc.release.retention,"POLICY_REQUIRED");
  assert.equal(doc.release.authorization,"SERVER_REQUIRED");
  assert.equal(doc.integrity.requiresAudit,true);
  assert.equal(doc.integrity.requiresVersion,true);
  assert.equal(doc.integrity.requiresIssuer,true);
  assert.equal(doc.integrity.requiresTimestamp,true);
 }
});
test("35 domain-specific table layouts, no unlabeled columns",()=>{
 const allTables=Object.values(library).flatMap(g=>g.fields.filter(f=>f.type==="table"));
 assert.equal(allTables.length,35);
 assert.equal(Object.keys(DOCUMENT_TABLE_COLUMNS).length,35);
 for(const f of allTables){
  const headers=DOCUMENT_TABLE_COLUMNS[f.id];
  assert.ok(headers&&headers.length>=4,f.id);
  assert.ok(headers.every(x=>x.trim().length>0),f.id);
  assert.equal(new Set(headers).size,headers.length,f.id);
 }
 for(const id of ["labResults_1","prescription_1","financeLines_1","medAdministration_1","transfusion_1","microbiology_3","goodsReceipt_3"]){
  assert.ok(DOCUMENT_TABLE_COLUMNS[id]?.length>=5,id);
 }
});
test("all 18 canonical document outputs retain required safety-critical domain groups",()=>{
 const required={
 P01:["patient","identity"],P02:["patient","encounter"],P03:["patient","specimen","label"],
 P04:["patient","specimen","labResults"],P05:["patient","imagingReport"],P06:["patient","prescription","allergy"],
 P07:["patient","medAdministration"],P08:["patient","discharge"],P09:["patient","referral"],
 P10:["financeLines","document"],P11:["payment","financeLines"],P12:["patient","consent","signature"],
 P13:["patient","procedure","signature"],P14:["patient","transfusion"],P15:["purchase","document"],
 P16:["goodsReceipt","operationInventory"],P17:["mortuary","identity"],P18:["audit","privacy"]
 };
 for(const [id,groups] of Object.entries(required)){
  const doc=docs.find(x=>x.id===id);
  assert.ok(doc,id);
  for(const group of groups)assert.ok(doc.groupKeys.includes(group),id+" missing "+group);
 }
});
test("print previews render only blank data and explicit unissued status",()=>{
 for(const term of ["SAMPLE TEMPLATE — NOT ISSUED OR VALID","UNISSUED SAMPLE","Not assigned","No values, author signatures","no live","Not signed."]){
  if(term==="no live")continue;
  assert.ok(layout.includes(term),term);
 }
 assert.ok(layout.includes("window.print()"),"sample printing must be user initiated");
 assert.ok(layout.includes("Not entered — approved content required"));
 assert.ok(layout.includes("Not valid for clinical".toUpperCase()) || layout.includes("NOT VALID FOR CLINICAL"));
 assert.ok(layout.includes("Signature") || layout.includes("signatures"));
 assert.ok(layout.includes("aria-labelledby"));
 assert.equal(DOCUMENT_RELEASE.generation,"SAMPLE_ONLY");
 assert.equal(DOCUMENT_RELEASE.realPrint,"NOT_AUTHORIZED");
});
test("A4 print styling avoids glowing and clipping, includes page-break policy",()=>{
 for(const bad of [/box-shadow\s*:/i,/text-shadow\s*:/i,/linear-gradient\s*\(/i,/drop-shadow\s*\(/i,/transform\s*:\s*scale\s*\(/i,/overflow\s*:\s*hidden/i]){
  assert.doesNotMatch(styles,bad);
 }
 assert.ok(styles.includes("@page{size:A4"));
 assert.ok(styles.includes("break-inside:avoid"));
 assert.ok(styles.includes("page-break-after:avoid"));
 assert.ok(styles.includes("min-height:44px"));
 assert.ok(styles.includes("max-width:850px"));
 assert.ok(styles.includes("max-width:390px"));
 assert.ok(styles.includes("prefers-reduced-motion"));
});
test("template search, module/media filters, canonical filter and list navigation exist",()=>{
 for(const expected of ["type=\"search\"","All modules","All media","Additional candidates","P01–P18 canonical","Clear filters","Cards","Table","Open complete form preview","Showing {list.length}"]){
  assert.ok(list.includes(expected),expected);
 }
 assert.ok(read("apps/web/app/documents/[templateId]/page.tsx").includes("generateStaticParams"));
 assert.ok(read("apps/web/app/documents/page.tsx").includes("DocumentCatalog"));
});
