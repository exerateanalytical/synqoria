import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve,dirname} from "node:path";
import {fileURLToPath} from "node:url";
const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const read=p=>readFileSync(resolve(root,p),"utf8");
const codes=["pat","opd","emr","nur","lab","rad","phm","sur","blo","mat","ped","con","fin","ins","sup","mor","gov","fac","amb","hr","qlt","reh","spc","sys"];
const detailed=codes.flatMap(c=>JSON.parse(read("apps/web/app/ui/printables/catalog-"+c+".json")));
const pdf=JSON.parse(read("registers/hospital-document-template-catalog-v1.json")).templates;
const core=Array.from({length:18},(_,i)=>"P"+String(i+1).padStart(2,"0"));
test("both document sources are intact and their union has 662 identifier records",()=>{
 assert.equal(detailed.length,376);assert.equal(pdf.length,304);
 assert.equal(new Set([...detailed,...pdf].map(x=>x.id)).size,662);
 assert.equal(new Set(pdf.map(x=>x.module)).size,40);
});
test("canonical P01-P18 remain uniquely mapped to the same domain module",()=>{
 assert.deepEqual(detailed.filter(x=>x.canonical).map(x=>x.id).sort(),core);
 assert.deepEqual(pdf.filter(x=>x.canonicalPrintCode).map(x=>x.id).sort(),core);
 for(const id of core)assert.equal(detailed.find(x=>x.id===id)?.module,pdf.find(x=>x.id===id)?.module,id);
});
test("both printable engines and reconciled catalogue expose all page routes",()=>{
 for(const path of ["apps/web/app/documents/page.tsx","apps/web/app/documents/[templateId]/page.tsx","apps/web/app/ui/printables/page.tsx","apps/web/app/ui/printables/[templateId]/page.tsx","apps/web/app/hospital-documents/page.tsx"]){
  assert.ok(read(path).includes("export default"),path);
 }
 const unified=read("apps/web/app/hospital-documents/page.tsx");
 for(const s of ["DOCUMENT_CATALOG","enhanced.templates","byId.set(t.id","alternateHref:link"])assert.ok(unified.includes(s),s);
 const screens=read("apps/web/app/ui/catalog/[screenId]/page.tsx");
 for(const s of ["relatedStructured","relatedPdf","DOCUMENT_CATALOG.filter","enhancedDocs.templates.filter","/hospital-documents"])assert.ok(screens.includes(s),s);
});
test("all actual issuance remains deny-by-default and both sources are sample-only",()=>{
 const locked=read("apps/web/app/ui/printables/issuance-gate.ts");
 assert.ok(locked.includes("allowed:false"));
 assert.ok(locked.includes("productionIssuanceServiceNotImplemented"));
 assert.ok(locked.includes("NEVER_ISSUE_FROM_UI=true"));
 assert.ok(pdf.every(x=>x.release.mode==="SAMPLE_ONLY"&&x.release.clinical==="DENIED"&&x.release.dataSource==="NO_LIVE_DATA"));
 assert.ok(detailed.every(x=>x.status==="BLANK_SPECIMEN_UNAPPROVED"));
});
test("unified catalogue supports filters and print-safe no-glow reflow",()=>{
 const page=read("apps/web/app/hospital-documents/UnifiedHospitalDocuments.tsx");
 for(const s of ["type=\"search\"","Hospital module","Document type","Template family","Clear all filters","Alternative PDF layout","Unique template identifiers"])assert.ok(page.includes(s),s);
 const css=read("apps/web/app/hospital-documents/document-library.css");
 for(const s of ["max-width:350px","focus-visible"])assert.ok(css.includes(s),s);
 assert.doesNotMatch(css,/box-shadow\s*:|text-shadow\s*:|drop-shadow\s*\(|linear-gradient\s*\(/i);
});
console.log("Sample source and completeness tests do not imply hospital regulatory or clinical approval.");
