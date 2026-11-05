import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {resolve,dirname} from "node:path";
import {fileURLToPath} from "node:url";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"..");
const read=p=>readFileSync(resolve(root,p),"utf8");
const codes=["pat","opd","emr","nur","lab","rad","phm","sur","blo","mat","ped","con","fin","ins","sup","mor","gov","fac","amb","hr","qlt","reh","spc","sys"];
const docs=codes.flatMap(code=>JSON.parse(read("apps/web/app/ui/printables/catalog-"+code+".json")));
const ids=docs.map(x=>x.id);
const get=(id)=>{const t=docs.find(x=>x.id===id);assert.ok(t,"Required "+id);return t;};
const render=read("apps/web/app/ui/printables/PrintablePreview.tsx");
const printCss=read("apps/web/app/ui/printables/printable.css");
const list=read("apps/web/app/ui/printables/PrintLibraryClient.tsx");
const registry=read("apps/web/app/ui/printables/registry.ts");
const profiles=read("apps/web/app/ui/printables/print-profiles.ts");
const page=read("apps/web/app/ui/printables/[templateId]/page.tsx");

test("catalogue explicitly contains 376 individually coded hospital documents in 24 groups",()=>{
 assert.equal(codes.length,24);
 assert.equal(docs.length,376);
 assert.equal(new Set(ids).size,376);
 assert.equal(new Set(docs.map(x=>x.domain)).size,24);
 assert.ok(docs.every(x=>x.en.length>3&&x.fr.length>3));
 assert.ok(docs.every(x=>x.sections.length>=2));
 assert.ok(docs.every(x=>x.sections.every(s=>s.heading&&s.fields.length>=1)));
 assert.ok(docs.every(x=>x.status==="BLANK_SPECIMEN_UNAPPROVED"));
 assert.ok(docs.every(x=>x.sections.every(s=>s.fields.every(f=>f.length>2))));
});

test("canonical P01–P18 exist exactly once and include the mandated 18 print classes",()=>{
 const canonical=Array.from({length:18},(_,i)=>"P"+String(i+1).padStart(2,"0"));
 assert.deepEqual(docs.filter(x=>x.canonical).map(x=>x.id).sort(),canonical);
 assert.deepEqual(docs.filter(x=>/^P\d\d$/.test(x.id)).map(x=>x.id).sort(),canonical);
 assert.equal(get("P04").kind,"lab-result");
 assert.equal(get("P06").kind,"prescription");
 assert.equal(get("P10").kind,"invoice");
 assert.equal(get("P11").kind,"receipt");
 assert.equal(get("P12").kind,"consent");
 assert.equal(get("P14").kind,"transfusion");
 assert.equal(get("P17").kind,"custody");
 assert.equal(get("P18").kind,"audit");
});

test("high-risk documents enumerate their essential review and source context without inventing values",()=>{
 const fields=id=>get(id).sections.flatMap(s=>s.fields).join(" ").toLowerCase();
 for(const [id,patterns] of [
 ["P04",[/specimen/,/accession/,/unit/,/reference interval/,/validator/,/status/]],
 ["P06",[/medication/,/dose/,/unit/,/route/,/frequency/,/quantity/,/allergy/,/prescriber/]],
 ["P10",[/invoice/,/quantity/,/unit price/,/tax/,/ledger|status/]],
 ["P11",[/receipt/,/amount/,/payment method/,/reconciliation|ledger/,/cashier/]],
 ["P12",[/risks/,/benefits/,/alternatives/,/patient/,/consent/,/witness/]],
 ["P14",[/unit id/,/compatibility/,/start\/end/,/monitoring/,/two-person/]],
 ["P17",[/verified source/,/custody/,/handover/,/authorized recipient/]],
 ["P18",[/date range/,/hash\/digest/,/authorized/,/audit/,/retention/]]
 ])for(const exp of patterns)assert.match(fields(id),exp,id+" "+exp);
});
test("all print kinds have named layout profiles, with extra safeguards for receipts and labels",()=>{
 const kinds=[...new Set(docs.map(d=>d.kind))];
 for(const kind of kinds)assert.ok(profiles.includes('"'+kind+'":norm('),"Missing dedicated profile for "+kind);
 for(const name of ["a4-portrait","a4-landscape","a5-proof","label-proof","receipt-proof"])assert.ok(profiles.includes(name),name);
 assert.ok(profiles.includes("No simulated barcode is issued"));
 assert.ok(profiles.includes("patient"));
 assert.ok(profiles.includes("requiresSourceApproval:true"));
});
test("all print routes use register as source and keep clinical source separate from blank printing",()=>{
 for(const required of ["generateStaticParams","DOCUMENT_CATALOG.map","findPrintable(templateId)","notFound()","PrintablePreview"])assert.ok(page.includes(required),required);
 for(const required of ["window.print()","SAMPLE · NOT VALID","MODÈLE · NON VALIDE","isSafetyCritical","syn-doc-consent","syn-doc-signatures","syn-doc-finance","syn-doc-record-table","syn-doc-notice-strong","Not valid for patient care","No consent is given"]) {
  if(required==="isSafetyCritical")assert.ok(render.includes(required));
  else assert.ok(render.includes(required),required);
 }
 assert.ok(list.includes('Only 18 canonical outputs'));
 assert.ok(list.includes('PER_PAGE=24'));
 assert.ok(list.includes('search'));
 assert.ok(registry.includes("CANONICAL_PRINT_IDS"));
});
test("printing has real A4/A5/receipt/label proofs, safe page breaks and no glow",()=>{
 for(const token of ["@page syn-doc-a4","@page syn-doc-landscape","@page syn-doc-a5","@page syn-doc-receipt","@page syn-doc-label","@media print","counter(page)","break-inside:avoid","table-header-group","width:min(210mm,100%)","max-width:360px","focus-visible"])assert.ok(printCss.includes(token),token);
 assert.doesNotMatch(printCss,/box-shadow\s*:|text-shadow\s*:|drop-shadow\s*\(|linear-gradient\s*\(|transform\s*:\s*scale\s*\(/i);
});
console.log("All print templates are BLANK UNISSUED SPECIMENS. These source tests do not prove clinical, fiscal, legal or printer certification.");
