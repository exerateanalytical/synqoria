import assert from "node:assert/strict";
import {createHash} from "node:crypto";
import {readFileSync,writeFileSync,mkdirSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const output=process.env.SYNQORIA_STRUCTURED_PDF_DIR||"/tmp/synqoria-structured-blank-pdfs";
mkdirSync(output,{recursive:true});
const codes=["pat","opd","emr","nur","lab","rad","phm","sur","blo","mat","ped","con","fin","ins","sup","mor","gov","fac","amb","hr","qlt","reh","spc","sys"];
const docs=codes.flatMap(c=>JSON.parse(readFileSync(new URL("../apps/web/app/ui/printables/catalog-"+c+".json",import.meta.url),"utf8")));
assert.equal(docs.length,376);
const audit={version:1,documentCount:docs.length,pdfCount:0,disclaimer:"ALL PDF FILES ARE BLANK, WATERMARKED UNISSUED SAMPLES. NOT VALID FOR PATIENT CARE OR FISCAL USE.",hashes:[],failures:[]};
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
try{
 for(const doc of docs){
  const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1});
  try{
   const res=await page.goto(base+"/ui/printables/"+encodeURIComponent(doc.id),{waitUntil:"domcontentloaded",timeout:30000});
   if(res?.status()!==200)throw Error("route returned "+res?.status());
   await page.locator(".syn-doc-sheet").waitFor({timeout:15000});
   const identity=await page.locator(".syn-doc-view").getAttribute("data-print-code");
   if(identity!==doc.id)throw Error("wrong template identity");
   const sample=await page.locator(".syn-doc-status-badge").innerText();
   if(!sample.includes("SAMPLE")||!sample.includes("NOT VALID"))throw Error("document missing sample warning");
   if((await page.locator(".syn-doc-sheet input, .syn-doc-sheet textarea").count())!==0)throw Error("unexpected editable patient or financial input");
   await page.emulateMedia({media:"print"});
   const pdf=await page.pdf({format:"A4",preferCSSPageSize:true,printBackground:true});
   if(pdf.subarray(0,4).toString()!=="%PDF"||pdf.length<3500)throw Error("malformed PDF: "+pdf.length);
   const digest=createHash("sha256").update(pdf).digest("hex");
   writeFileSync(resolve(output,doc.id+".pdf"),pdf);
   audit.hashes.push({id:doc.id,module:doc.module,kind:doc.kind,bytes:pdf.length,sha256:digest,media:"Uncalibrated blank sample",status:"NOT_VALID"});
   audit.pdfCount++;
  }catch(e){audit.failures.push({id:doc.id,error:String(e).slice(0,500)});}
  finally{await page.close();}
 }
}finally{await browser.close();}
writeFileSync(resolve(output,"structured-pdf-manifest.json"),JSON.stringify(audit,null,2)+"\n");
console.log(JSON.stringify({expected:376,generated:audit.pdfCount,failures:audit.failures.length,examples:audit.failures.slice(0,4)},null,2));
assert.equal(audit.pdfCount,376,"All structured printable specimens must produce PDF");
assert.equal(audit.failures.length,0);
