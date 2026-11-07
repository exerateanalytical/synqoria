import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const out=process.env.SYNQORIA_UI_AUDIT_DIR||"/tmp/synqoria-ui-browser-audit";
mkdirSync(out,{recursive:true});
const registry=JSON.parse(readFileSync(new URL("../registers/canonical-screens.json",import.meta.url),"utf8"));
const screens=registry.screen_register;
const representatives={F:"M05-S02",L:"G-S05",D:"G-S08",X:"G-S09",B:"M02-S01",T:"M06-S04",H:"G-S04"};
const widths=[320,768,1440];
const summary={schemaVersion:1,phase:"browser specimen QA only",routesChecked:0,routeFailures:[],viewportChecks:[],checklist:{noBodyOverflow:true,noButtonGlow:true,noButtonOverlap:true,threeTabsEach:true},explicitExclusions:["No live clinical data","No approved domain forms","No cross-tenant API verification","No screen reader or WCAG certification","No approved medication/clinical rules","No 261-screen human usability signoff"]};
for(const s of screens){
 const url=base+"/ui/catalog/"+encodeURIComponent(s.id);
 try{
  const response=await fetch(url,{signal:AbortSignal.timeout(12000)});
  if(response.status!==200)summary.routeFailures.push({id:s.id,status:response.status});
 }catch(error){summary.routeFailures.push({id:s.id,error:String(error)});}
 summary.routesChecked++;
}
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
try{
 for(const [layout,id] of Object.entries(representatives)){
  for(const width of widths){
   const page=await browser.newPage({viewport:{width,height:900},deviceScaleFactor:1});
   await page.goto(base+"/ui/catalog/"+id,{waitUntil:"domcontentloaded",timeout:30000});
   await page.locator(".syn-ui-preview").waitFor({timeout:15000});
   const findings=await page.evaluate(()=>{
    const doc=document.documentElement;
    const overflowing=doc.scrollWidth>window.innerWidth+2;
    const buttons=[...document.querySelectorAll("button")].filter(el=>{const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden"});
    const glow=buttons.filter(el=>{const c=getComputedStyle(el);return c.boxShadow!=="none"||c.textShadow!=="none"||c.filter.includes("drop-shadow")});
    const overlap=[];
    for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
     const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
     const ix=Math.min(a.right,b.right)-Math.max(a.left,b.left);
     const iy=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
     if(ix>3&&iy>3)overlap.push([buttons[i].textContent?.slice(0,35),buttons[j].textContent?.slice(0,35)]);
    }
    return {overflowing,scrollWidth:doc.scrollWidth,glowCount:glow.length,overlapCount:overlap.length,overlapSamples:overlap.slice(0,3)};
   });
   const tabCount=await page.getByRole("tab").count();
   summary.viewportChecks.push({layout,id,width,...findings,tabCount});
   if(findings.overflowing)summary.checklist.noBodyOverflow=false;
   if(findings.glowCount)summary.checklist.noButtonGlow=false;
   if(findings.overlapCount)summary.checklist.noButtonOverlap=false;
   if(tabCount!==3)summary.checklist.threeTabsEach=false;
   await page.screenshot({path:resolve(out,id+"-"+width+".png"),fullPage:true});
   if(layout==="F"&&width===1440){
    await page.locator(".syn-spec-form input").first().fill("SYNTHETIC UI TEST ONLY");
    await page.getByRole("button",{name:"Review demonstration entries"}).click();
    assert.match(await page.locator(".syn-review-grid").innerText(),/Demonstration entry provided/);
   }
   if(layout==="L"&&width===1440){
    await page.getByRole("button",{name:"Show filters"}).first().click();
    assert.equal(await page.locator("#syn-filter-panel").isVisible(),true);
    await page.getByRole("button",{name:"Apply filters"}).click();
    assert.match(await page.locator(".syn-filter-applied").innerText(),/Filters applied/);
    await page.getByRole("button",{name:"Clear all"}).click();
   }
   await page.close();
  }
 }
 const page=await browser.newPage({viewport:{width:768,height:900}});
 await page.goto(base+"/ui/catalog/components",{waitUntil:"domcontentloaded"});
 await page.getByRole("button",{name:"12 dialog variants"}).click();
 assert.equal(await page.locator(".syn-gallery-button").count(),12);
 await page.locator(".syn-gallery-button").first().click();
 assert.equal(await page.locator("dialog[open]").count(),1);
 await page.locator("dialog button:not([disabled])").first().click();
 assert.equal(await page.locator("dialog[open]").count(),0);
 await page.getByRole("button",{name:"18 print outputs"}).click();
 assert.equal(await page.locator(".syn-preview-paper").count(),18);
 await page.close();
}finally{await browser.close();}
writeFileSync(resolve(out,"audit.json"),JSON.stringify(summary,null,2)+"\n");
console.log(JSON.stringify({routes:summary.routesChecked,routeFailures:summary.routeFailures.length,viewports:summary.viewportChecks.length,checklist:summary.checklist},null,2));
assert.equal(summary.routeFailures.length,0,"Every canonical screen must have an HTTP 200 route");
assert.ok(Object.values(summary.checklist).every(Boolean),"Layout smoke assertions failed; inspect audit.json and screenshots");
