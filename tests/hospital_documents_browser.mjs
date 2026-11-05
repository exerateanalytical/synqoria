import assert from "node:assert/strict";
import {readFileSync,writeFileSync,mkdirSync} from "node:fs";
import {resolve} from "node:path";
import {createHash} from "node:crypto";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const evidence=process.env.SYNQORIA_DOC_EVIDENCE||"/tmp/synqoria-document-evidence";
const pdfDir=resolve(evidence,"blank-sample-pdfs");
const shotDir=resolve(evidence,"screenshots");
mkdirSync(pdfDir,{recursive:true});mkdirSync(shotDir,{recursive:true});
const catalog=JSON.parse(readFileSync(new URL("../registers/hospital-document-template-catalog-v1.json",import.meta.url),"utf8"));
const fields=JSON.parse(readFileSync(new URL("../registers/hospital-document-field-library.json",import.meta.url),"utf8")).fieldGroups;
const templates=catalog.templates;
const widths=[320,1440];
const report={
 title:"SYNQORIA 304 blank document sample browser/print audit",sourceVersion:catalog.schemaVersion,
 model:"UNISSUED / SAMPLE ONLY / NO PHI",checkedRoutes:0,layoutChecks:0,expectedLayoutChecks:templates.length*widths.length,
 printedPdfCount:0,pdfEvidence:[],screenshotCount:0,moduleScreenshotCount:0,
 checkTypes:["Route 200","exact field/group count","sample watermark","blank-only content","no body overflow","no interactive overlap/glow","user-initiated browser print","PDF signature"],
 findings:[],clinicalAcceptance:"DENIED",exceptions:["No patient/financial data bound","No signed legal consent","No printer hardware/label stock calibration","No trained human content verification","No full English/French localization","No regulatory acceptance"]
};
const screenshotModules=new Set();
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const context=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,acceptDownloads:false});
const note=(template,width,kind,data)=>report.findings.push({id:template.id,width,kind,data});
try{
 // Routes are real HTTP responses. All 304 are present before reviewing rendering.
 for(const item of templates){
  const response=await fetch(base+"/documents/"+encodeURIComponent(item.id),{signal:AbortSignal.timeout(12000)});
  if(response.status!==200)note(item,0,"route",response.status);
  report.checkedRoutes++;
 }
 const libraryResponse=await fetch(base+"/documents");
 if(libraryResponse.status!==200)report.findings.push({id:"catalog",kind:"route",data:libraryResponse.status});
 for(const template of templates){
  const expectedFields=template.groupKeys.reduce((n,k)=>n+fields[k].fields.length,0);
  for(const width of widths){
   const page=await context.newPage();await page.setViewportSize({width,height:900});
   const errors=[];page.on("pageerror",e=>errors.push(String(e)));
   const requests=[];page.on("request",request=>{
    const path=new URL(request.url()).pathname;
    if(/^\/(?:api|patients|auth)\//.test(path))requests.push(path);
   });
   try{
    const res=await page.goto(base+"/documents/"+encodeURIComponent(template.id),{waitUntil:"domcontentloaded",timeout:30000});
    await page.locator(".syn-doc-paper").waitFor({timeout:12000});
    if(res?.status()!==200)note(template,width,"page-status",res?.status());
    const actualGroups=await page.locator(".syn-doc-section").count();
    const actualFields=await page.locator("[data-document-field]").count();
    if(actualGroups!==template.groupKeys.length)note(template,width,"sections",{expected:template.groupKeys.length,actual:actualGroups});
    if(actualFields!==expectedFields)note(template,width,"fields",{expected:expectedFields,actual:actualFields});
    if((await page.locator(".syn-doc-paper").getAttribute("data-template-id"))!==template.id)note(template,width,"identity","wrong document");
    const ribbon=await page.locator(".syn-doc-sample-ribbon").innerText();
    if(!ribbon.includes("SAMPLE")||!ribbon.includes("NOT VALID"))note(template,width,"safety-banner",ribbon);
    if((await page.locator(".syn-doc-paper input, .syn-doc-paper textarea").count())!==0)note(template,width,"live-entry","Editable sensitive entry found");
    if((await page.locator(".syn-doc-sheet-footer").count())!==1)note(template,width,"footer","Missing issue/retention status");
    if(errors.length||requests.length)note(template,width,"runtime-or-network",{errors,requests});
    const computed=await page.evaluate(()=>{
      const e=document.documentElement;
      const buttons=[...document.querySelectorAll("button")].filter(b=>{const r=b.getBoundingClientRect(),s=getComputedStyle(b);return r.width>0&&r.height>0&&s.visibility!=="hidden"&&s.display!=="none"});
      const glowing=buttons.filter(b=>{const s=getComputedStyle(b);return s.boxShadow!=="none"||s.textShadow!=="none"||s.filter.includes("drop-shadow")}).length;
      const overlaps=[];
      for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
        const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
        if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>3&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>3)overlaps.push([buttons[i].textContent?.slice(0,32),buttons[j].textContent?.slice(0,32)]);
      }
      return {bodyOverflow:e.scrollWidth>window.innerWidth+2,scrollWidth:e.scrollWidth,glowing,overlapCount:overlaps.length,samples:overlaps.slice(0,2)};
    });
    if(computed.bodyOverflow||computed.glowing||computed.overlapCount)note(template,width,"visual",computed);
    if(width===320&&!screenshotModules.has(template.module)){
      await page.screenshot({path:resolve(shotDir,template.module+"-"+template.id+"-320.png"),fullPage:true});
      screenshotModules.add(template.module);report.screenshotCount++;
    }
    if(width===1440){
      if(template.canonicalPrintCode){
        await page.screenshot({path:resolve(shotDir,template.id+"-1440.png"),fullPage:true});
        report.screenshotCount++;
      }
      const bytes=await page.pdf({format:"A4",printBackground:true,preferCSSPageSize:true});
      if(bytes.subarray(0,4).toString()!=="%PDF"||bytes.length<5000)note(template,width,"pdf","Malformed PDF bytes");
      const filename=template.id+".pdf";
      writeFileSync(resolve(pdfDir,filename),bytes);
      report.pdfEvidence.push({id:template.id,module:template.module,bytes:bytes.length,sha256:createHash("sha256").update(bytes).digest("hex"),mediaSource:"A4 sample; intended media calibration pending"});
      report.printedPdfCount++;
    }
    if(template.id==="P04"&&width===1440){
       const notice=await page.locator(".syn-doc-safety").innerText();
       if(!notice.includes("Diagnostic safeguard"))note(template,width,"lab-safety","Missing diagnostic disclaimer");
    }
    if(template.id==="P06"&&width===1440){
       const notice=await page.locator(".syn-doc-safety").innerText();
       if(!notice.includes("Medication safeguard"))note(template,width,"prescription-safety","Missing medication disclaimer");
    }
    if(template.id==="P12"&&width===1440){
       const notice=await page.locator(".syn-doc-safety").innerText();
       if(!notice.includes("Consent safeguard"))note(template,width,"consent-safety","Missing consent disclaimer");
    }
    if(template.id==="P11"&&width===1440){
       const notice=await page.locator(".syn-doc-safety").innerText();
       if(!notice.includes("Financial safeguard"))note(template,width,"financial-safety","Missing financial disclaimer");
    }
    if(template.id==="P01"&&width===1440){
       await page.evaluate(()=>{window.__synqoriaPrintCount=0;window.print=()=>{window.__synqoriaPrintCount++;};});
       await page.getByRole("button",{name:/Print blank SAMPLE/}).click();
       const calls=await page.evaluate(()=>window.__synqoriaPrintCount);
       if(calls!==1)note(template,width,"print-button",{calls});
       await page.getByRole("button",{name:"Template specification"}).click();
       if(!await page.locator(".syn-doc-meta").isVisible())note(template,width,"specification","control failed");
    }
    report.layoutChecks++;
   }catch(error){note(template,width,"exception",String(error).slice(0,450));}
   finally{await page.close();}
  }
 }
}finally{await context.close();await browser.close();}
report.moduleScreenshotCount=screenshotModules.size;
writeFileSync(resolve(evidence,"audit.json"),JSON.stringify({...report,findings:report.findings.slice(0,250)},null,2)+"\n");
writeFileSync(resolve(evidence,"pdf-manifest.json"),JSON.stringify({templateCount:report.printedPdfCount,clinicalStatus:"SAMPLE_NOT_VALID",pdfs:report.pdfEvidence},null,2)+"\n");
console.log(JSON.stringify({routes:report.checkedRoutes,viewportChecks:report.layoutChecks,expectedViewportChecks:report.expectedLayoutChecks,generatedSamplePdfs:report.printedPdfCount,modulesWithScreenshots:report.moduleScreenshotCount,totalScreenshots:report.screenshotCount,failures:report.findings.length,examples:report.findings.slice(0,5)},null,2));
assert.equal(report.checkedRoutes,304);
assert.equal(report.layoutChecks,608);
assert.equal(report.printedPdfCount,304);
assert.equal(report.moduleScreenshotCount,40);
assert.equal(report.findings.length,0,"Document samples have missing fields or unsafe print/layout findings");
