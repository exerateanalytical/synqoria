import assert from "node:assert/strict";
import {mkdirSync,writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";
const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const folder=process.env.SYNQORIA_REAL_UI_EVIDENCE||"/tmp/synqoria-real-screen-evidence";
mkdirSync(folder,{recursive:true});
const screens=["G-S03","G-S06","G-S10","G-S11","G-S12","M01-S01","M01-S03","M03-S01","M03-S03","M03-S04","M03-S05"];
const errors=[],samples=[],views=[];
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const patient="44444444-4444-4444-8444-444444444444",caseId="55555555-5555-4555-8555-555555555555";
try{
 const api=[
  ["/api/clinical/facility",401],
  ["/api/clinical/patients/"+patient,401],
  ["/api/clinical/triage/"+patient+"/"+caseId,401],
  ["/api/clinical/health",503],
  ["/api/clinical/patients/bad-patient-id",400],
  ["/api/clinical/triage/"+patient+"/"+caseId+"/history",401],
  ["/api/clinical/triage/bad-patient-id/"+caseId,400]
 ];
 for(const [path,status] of api){
  const r=await fetch(base+path,{headers:{Accept:"application/json"}});
  if(r.status!==status)errors.push({path,issue:"unexpected status",expected:status,actual:r.status});
  if(r.headers.get("cache-control")!=="no-store, private")errors.push({path,issue:"missing private no-store"});
  if(!r.headers.get("content-type")?.includes("application/json"))errors.push({path,issue:"missing JSON"});
  const body=await r.json();
  if(!body.code||JSON.stringify(body).includes("Bearer"))errors.push({path,issue:"unsafe response"});
 }
 const triageNoSession=await fetch(base+"/api/clinical/triage/views/"+patient,{method:"POST",headers:{"Origin":base,"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify({view:"FACILITY_QUEUE",caseIds:[caseId]})});
 if(triageNoSession.status!==401)errors.push({path:"triage/worklist",issue:"missing staff session was not denied",actual:triageNoSession.status});
 if(triageNoSession.headers.get("cache-control")!=="no-store, private")errors.push({path:"triage/worklist",issue:"missing response privacy"});
 for(const width of [320,768,1440]){
  for(const screen of screens){
   const page=await browser.newPage({viewport:{width,height:900}});
   const js=[];page.on("pageerror",e=>js.push(String(e)));
   try{
    const response=await page.goto(base+"/workforce/clinical/"+screen,{waitUntil:"domcontentloaded"});
    assert.equal(response?.status(),200);
    await page.locator('[data-real-screen-id="'+screen+'"]').waitFor();
    const bounds=await page.evaluate(()=>{
     const doc=document.documentElement;
     const buttons=[...document.querySelectorAll("button")].filter(b=>{const r=b.getBoundingClientRect(),s=getComputedStyle(b);return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden"});
     const glow=buttons.filter(b=>{const s=getComputedStyle(b);return s.boxShadow!=="none"||s.textShadow!=="none"||s.filter.includes("drop-shadow")}).length;
     let overlap=0;
     for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
      const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
      if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>3&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>3)overlap++;
     }
     return {overflow:doc.scrollWidth>innerWidth+2,glow,overlap};
    });
    if(bounds.overflow||bounds.glow||bounds.overlap||js.length)errors.push({screen,width,bounds,js});
    if(width!==768){
     await page.screenshot({path:resolve(folder,screen+"-"+width+".png"),fullPage:true});samples.push(screen+"-"+width);
    }
    if(screen==="G-S03"){
     await page.getByRole("button",{name:"Read from hospital server"}).click();
     await page.locator('[data-clinical-read-status="unauthenticated"]').waitFor();
    }
    if(screen==="G-S10"){
     await page.getByRole("button",{name:"Read from hospital server"}).click();
     await page.locator('[data-clinical-read-status="unavailable"]').waitFor();
    }
    if(screen==="M01-S03"||screen==="M01-S01"){
     await page.getByRole("button",{name:"Read from hospital server"}).click();
     await page.locator('[data-clinical-read-status="invalid"]').waitFor();
     await page.getByLabel("Patient UUID").fill(patient);
     await page.getByRole("button",{name:"Read from hospital server"}).click();
     await page.locator('[data-clinical-read-status="unauthenticated"]').waitFor();
     await page.getByRole("button",{name:"Clear sensitive context"}).click();
     assert.equal(await page.getByLabel("Patient UUID").inputValue(),"");
    }
    if(screen==="M03-S01"){
     await page.getByRole("button",{name:"Read permitted triage cases"}).click();
     await page.locator('[data-triage-queue-status="invalid"]').waitFor();
     await page.getByLabel("Patient UUID").fill(patient);
     await page.locator("textarea").fill(caseId);
     await page.getByRole("button",{name:"Read permitted triage cases"}).click();
     await page.locator('[data-triage-queue-status="login"]').waitFor();
    }
    if(screen==="M03-S04"){
     await page.getByRole("button",{name:"Read source audit history"}).click();
     await page.locator('[data-audit-status="invalid"]').waitFor();
     await page.getByLabel("Authorized patient UUID").fill(patient);
     await page.getByLabel("Triage case UUID").fill(caseId);
     await page.getByRole("button",{name:"Read source audit history"}).click();
     await page.locator('[data-audit-status="login"]').waitFor();
     await page.getByRole("button",{name:"Clear identifiers and records"}).click();
     assert.equal(await page.getByLabel("Authorized patient UUID").inputValue(),"");
    }
    if(screen==="M03-S05"){
     await page.getByLabel("Patient UUID").fill(patient);
     await page.locator("textarea").fill(caseId);
     await page.getByRole("button",{name:"Read permitted triage cases"}).click();
     await page.locator('[data-triage-queue-status="login"]').waitFor();
     assert.equal(await page.locator(".syn-live-fixed-view strong").innerText(),"PENDING HANDOVERS");
    }
    if(screen==="M03-S03"){
     await page.getByLabel("Patient UUID").fill(patient);
     await page.getByLabel("Triage case UUID").fill(caseId);
     await page.getByRole("button",{name:"Read from hospital server"}).click();
     await page.locator('[data-clinical-read-status="unauthenticated"]').waitFor();
    }
    if(screen==="G-S11"){
     await page.getByLabel("Search support topics").fill("triage");
     if(await page.locator(".syn-live-index-item").count()!==1)errors.push({screen,width,issue:"help search mismatch"});
     await page.getByLabel("Search support topics").fill("");
    }
    if(screen==="G-S12"){
     if(await page.getByRole("heading",{name:"Feature unavailable or retired"}).count()!==1)errors.push({screen,width,issue:"safe not-found title missing"});
    }
    if(screen==="G-S06"){
     await page.getByLabel("Language").selectOption("fr");
     await page.getByLabel("Contraste").selectOption("high");
     await page.getByRole("button",{name:"Enregistrer les préférences"}).click();
     const stored=await page.evaluate(()=>window.localStorage.getItem("synqoria.ui.preferences.v1"));
     if(!stored||!stored.includes('"contrast":"high"')||stored.includes("Bearer")||stored.length>512)errors.push({screen,width,issue:"unsafe preferences storage"});
     await page.reload();
     if(await page.getByLabel("Contraste").inputValue()!=="high")errors.push({screen,width,issue:"preferences failed persistence"});
     if(await page.locator("html").getAttribute("data-synqoria-contrast")!=="high")errors.push({screen,width,issue:"contrast not applied"});
    }
    views.push({screen,width,...bounds});
   }catch(e){errors.push({screen,width,issue:String(e).slice(0,450)});}
   finally{await page.close();}
  }
 }
 const page=await browser.newPage();
 await page.goto(base+"/workforce/clinical");
 if(await page.locator('[data-route-directory="261"]').count()!==1||await page.locator("tbody tr").count()!==261)errors.push({issue:"missing canonical route directory or incomplete screen list"});
 await page.goto(base+"/workforce/clinical/M31-S01");
 if(!page.url().endsWith("/hospital-documents"))errors.push({issue:"template catalogue route did not reach working index"});
 await page.goto(base+"/workforce/clinical/M31-S03");
 if(!page.url().endsWith("/ui/printables"))errors.push({issue:"printable preview route not reached"});
 await page.close();
}finally{await browser.close();}
const results={screens:screens.length,viewportCases:views.length,expectedViewportCases:screens.length*3,apiChecks:8,redirects:2,images:samples.length,errors,clinicalCertification:"DENIED",limits:["No deployed OIDC session issuer","No configured real clinical cell in tests","Clinical field dictionaries and clinical mutation APIs unfinished"]};
writeFileSync(resolve(folder,"real-screen-browser.json"),JSON.stringify(results,null,2)+"\n");
console.log(JSON.stringify({screens:results.screens,viewports:results.viewportCases,apiChecks:results.apiChecks,redirects:results.redirects,errors:errors.length,samples:results.images},null,2));
assert.equal(results.viewportCases,results.expectedViewportCases);
assert.equal(errors.length,0);
