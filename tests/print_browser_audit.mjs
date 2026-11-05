import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_PRINT_BASE_URL||"http://127.0.0.1:3000";
const out=process.env.SYNQORIA_PRINT_AUDIT_DIR||"/tmp/synqoria-print-browser";
mkdirSync(out,{recursive:true});
const codes=["pat","opd","emr","nur","lab","rad","phm","sur","blo","mat","ped","con","fin","ins","sup","mor","gov","fac","amb","hr","qlt","reh","spc","sys"];
const docs=codes.flatMap(c=>JSON.parse(readFileSync(new URL("../apps/web/app/ui/printables/catalog-"+c+".json",import.meta.url),"utf8")));
const representative=codes.map(c=>docs.find(d=>d.domain===c.toUpperCase()));
const failures=[],viewports=[],shots=[],printed=[];
const widths=[320,768,1440];
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
try{
 for(const doc of docs){
  try{
   const response=await fetch(base+"/ui/printables/"+encodeURIComponent(doc.id),{signal:AbortSignal.timeout(12000)});
   if(response.status!==200)failures.push({id:doc.id,issue:"route",status:response.status});
  }catch(e){failures.push({id:doc.id,issue:"route",error:String(e).slice(0,200)});}
 }
 const root=await browser.newPage({viewport:{width:320,height:900}});
 await root.goto(base+"/ui/printables",{waitUntil:"domcontentloaded"});
 assert.equal(await root.locator(".syn-library-item").count(),24);
 await root.locator("input[type=search]").fill("specimen label");
 assert.match(await root.locator(".syn-library-results").innerText(),/matching forms/);
 await root.locator(".syn-library-reset").click();
 await root.getByLabel("Only 18 canonical outputs (P01–P18)").check();
 assert.match(await root.locator(".syn-library-results").innerText(),/18\s+matching forms/);
 await root.close();
 for(const doc of representative){
  for(const width of widths){
   const page=await browser.newPage({viewport:{width,height:900},deviceScaleFactor:1});
   const errors=[];page.on("pageerror",e=>errors.push(String(e)));
   try{
    const response=await page.goto(base+"/ui/printables/"+doc.id,{waitUntil:"domcontentloaded",timeout:30000});
    assert.equal(response?.status(),200);
    await page.locator(".syn-doc-sheet").waitFor({timeout:12000});
    const a=await page.evaluate(()=>{
     const root=document.documentElement; const overflow=root.scrollWidth>window.innerWidth+2;
     const sample=document.querySelector(".syn-doc-head-meta strong")?.textContent||"";
     const title=document.querySelector(".syn-doc-heading h1")?.textContent||"";
     const sheet=document.querySelector(".syn-doc-sheet");
     const buttons=[...document.querySelectorAll("button")].filter(b=>{
       const r=b.getBoundingClientRect(),s=getComputedStyle(b);return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden";
     });
     const glow=buttons.filter(b=>{const s=getComputedStyle(b);return s.boxShadow!=="none"||s.textShadow!=="none"||s.filter.includes("drop-shadow")}).length;
     let overlap=0;
     for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
      const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
      if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>3&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>3)overlap++;
     }
     return {overflow,sample,title,glow,overlap,media:sheet?.getAttribute("data-media"),sections:sheet?.querySelectorAll(".syn-doc-content-section").length||0};
    });
    if(a.overflow||a.glow||a.overlap||!a.sample.includes("NOT VALID")||!a.title||a.sections<3||errors.length)failures.push({id:doc.id,width,issue:"visual-or-runtime",details:{...a,errors}});
    viewports.push({id:doc.id,width,...a});
    if(width===320||width===1440){
     await page.screenshot({path:resolve(out,doc.id+"-"+width+".png"),fullPage:true});shots.push(doc.id+"-"+width);
    }
    if(width===1440){
     await page.getByRole("button",{name:"Français"}).click();
     const title=await page.locator(".syn-doc-heading h1").innerText();
     if(title!==doc.fr)failures.push({id:doc.id,width,issue:"fr-title",title});
     if(!(await page.locator(".syn-doc-status-badge").innerText()).includes("NON VALIDE"))failures.push({id:doc.id,width,issue:"fr-warning"});
     await page.getByRole("button",{name:"English"}).click();
     await page.evaluate(()=>{window.__synPrintCalled=false;window.print=()=>{window.__synPrintCalled=true;};});
     await page.getByRole("button",{name:"Print marked blank sample"}).click();
     const invoked=await page.evaluate(()=>window.__synPrintCalled===true);
     if(!invoked)failures.push({id:doc.id,width,issue:"print-button"});
     printed.push(doc.id);
    }
   }catch(e){failures.push({id:doc.id,width,issue:"exception",error:String(e).slice(0,600)});}
   finally{await page.close();}
  }
 }
 for(const id of Array.from({length:18},(_,i)=>"P"+String(i+1).padStart(2,"0"))){
  const page=await browser.newPage({viewport:{width:1440,height:900}});
  await page.goto(base+"/ui/printables/"+id,{waitUntil:"domcontentloaded"});
  await page.emulateMedia({media:"print"});
  const mark=await page.locator(".syn-doc-status-badge").innerText();
  if(!mark.includes("NOT VALID"))failures.push({id,issue:"print-watermark"});
  if(await page.locator(".syn-doc-toolbar").isVisible())failures.push({id,issue:"print-ui-visible"});
  if(["P04","P06","P07","P10","P11","P14"].includes(id)){
   if((await page.locator(".syn-doc-table-scroll table").count())===0)failures.push({id,issue:"missing-document-table"});
  }
  if(id==="P12"&&await page.locator(".syn-doc-consent-options").count()===0)failures.push({id,issue:"missing-consent-choice"});
  if(id==="P03"&&await page.locator(".syn-doc-notice-strong").count()===0)failures.push({id,issue:"missing-label-calibration"});
  await page.close();
 }
}finally{await browser.close();}
const result={templates:docs.length,departments:codes.length,routeFailures:failures.filter(f=>f.issue==="route").length,viewportChecks:viewports.length,expected:codes.length*widths.length,localeAndPrintChecks:printed.length,printedCanonicalSamples:18,screenshots:shots.length,failures:failures.slice(0,200),failureCount:failures.length,limitations:["Blank samples only","No real patient or payment data","No legal signatures, barcodes or receipt issuance","No calibrated medical label printers","No comprehensive screen-reader or full EN/FR translation certification"]};
writeFileSync(resolve(out,"print-browser-audit.json"),JSON.stringify(result,null,2)+"\n");
console.log(JSON.stringify({templates:result.templates,departments:result.departments,viewports:result.viewportChecks,localeAndPrint:result.localeAndPrintChecks,printMediaCanonical:result.printedCanonicalSamples,failures:result.failureCount,examples:result.failures.slice(0,6)},null,2));
assert.equal(result.templates,376);
assert.equal(result.viewportChecks,result.expected);
assert.equal(result.localeAndPrintChecks,24);
assert.equal(result.failureCount,0,"Review print-browser-audit.json");
