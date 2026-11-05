import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const out=process.env.SYNQORIA_UI_AUDIT_DIR||"/tmp/synqoria-ui-all-screen-audit";
mkdirSync(out,{recursive:true});
const manifest=JSON.parse(readFileSync(new URL("../registers/ui-specific-252-prototype-contracts.json",import.meta.url),"utf8"));
const registry=JSON.parse(readFileSync(new URL("../registers/canonical-screens.json",import.meta.url),"utf8"));
const specs=manifest.remainingSpecialized;
const reports=[],failures=[],sampleShots=[],networkCalls=[];
const layouts={F:0,L:0,D:0,X:0,B:0,T:0,H:0};
const sampled=new Set();
const viewports=[320,1440];
const config={timeout:30000};
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const context=await browser.newContext({viewport:{width:1440,height:900}});
function recordFail(spec,width,key,detail){failures.push({id:spec.id,layout:spec.layout,width,key,detail});}
function activeLabelCount(spec,tab,counts){
 const n=spec.fields.length;
 switch(spec.layout){
  case "F":return tab===spec.tabs.length-1?counts.review===spec.fields.length:spec.fields.filter(f=>f.tabIndex===tab).length===counts.fields;
  case "D":return tab===2?true:(tab===0?Math.max(1,Math.ceil(n/2)):n-Math.max(1,Math.ceil(n/2)))===counts.details;
  case "X":return tab===2?true:(tab===0?Math.ceil(n/2):n-Math.ceil(n/2))===counts.compare;
  case "L":return counts.headers===n+1;
  case "B":return counts.lanes===n;
  case "T":return counts.metrics===n;
  case "H":return counts.metrics===n;
 }
}
try{
 // Basic HTTP contract for all 261 canonical routes.
 for(const row of registry.screen_register){
  let response;
  try{response=await fetch(base+"/ui/catalog/"+row.id,{signal:AbortSignal.timeout(12000)});}
  catch(e){failures.push({id:row.id,key:"route",detail:String(e)});continue;}
  if(response.status!==200)failures.push({id:row.id,key:"route",detail:response.status});
 }
 for(const spec of specs){
  layouts[spec.layout]++;
  for(const width of viewports){
   const page=await context.newPage();await page.setViewportSize({width,height:900});
   const bad=[];
   page.on("pageerror",error=>bad.push(String(error)));
   page.on("request",req=>{const url=new URL(req.url());if(/^\/api\/|^\/auth\/|^\/patients\//.test(url.pathname))networkCalls.push({id:spec.id,width,path:url.pathname});});
   try{
    await page.goto(base+"/ui/catalog/"+spec.id,{waitUntil:"domcontentloaded",timeout:30000});
    await page.locator(".syn-specific").waitFor({timeout:15000});
    const locked=await page.locator(".syn-specific-locked").filter({hasText:spec.primaryAction}).first().isDisabled();
    const tabCount=await page.getByRole("tab").count();
    if(!locked)recordFail(spec,width,"mutation-gate","Primary action was enabled");
    if(tabCount!==spec.tabs.length)recordFail(spec,width,"tabs","Expected "+spec.tabs.length+" got "+tabCount);
    if(bad.length)recordFail(spec,width,"runtime-exception",bad.slice(0,3));
    const info=await page.evaluate(()=>{
     const doc=document.documentElement;
     const overflow=doc.scrollWidth>window.innerWidth+2;
     const buttons=[...document.querySelectorAll("button")].filter(b=>{const r=b.getBoundingClientRect(),st=getComputedStyle(b);return r.width>0&&r.height>0&&st.display!=="none"&&st.visibility!=="hidden"});
     const glow=buttons.filter(b=>{const st=getComputedStyle(b);return st.boxShadow!=="none"||st.textShadow!=="none"||st.filter.includes("drop-shadow")});
     const overlaps=[];for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
      const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
      if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>3&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>3)overlaps.push([buttons[i].textContent?.slice(0,20),buttons[j].textContent?.slice(0,20)]);
     }
     return {bodyOverflow:overflow,scrollWidth:doc.scrollWidth,buttonGlow:glow.length,buttonOverlap:overlaps.length,overlaps:overlaps.slice(0,2)};
    });
    if(info.bodyOverflow)recordFail(spec,width,"body-overflow",info.scrollWidth);
    if(info.buttonGlow)recordFail(spec,width,"button-glow",info.buttonGlow);
    if(info.buttonOverlap)recordFail(spec,width,"button-overlap",info.overlaps);
    const rep=!sampled.has(spec.module);
    if(rep&&width===320){
     sampled.add(spec.module);await page.screenshot({path:resolve(out,spec.module+"-"+spec.id+"-320.png"),fullPage:true});sampleShots.push(spec.id);
    }
    if(width===1440&&sampleShots.includes(spec.id)){await page.screenshot({path:resolve(out,spec.module+"-"+spec.id+"-1440.png"),fullPage:true});}
    for(let i=0;i<spec.tabs.length;i++){
     await page.getByRole("tab").nth(i).click();
     if((await page.getByRole("tab").nth(i).getAttribute("aria-selected"))!=="true")recordFail(spec,width,"tab-selection",i);
     const counts=await page.evaluate(()=>({
      fields:document.querySelectorAll('.syn-specific-field[data-canonical-field]').length,
      details:document.querySelectorAll('.syn-specific-attrs dt').length,
      compare:document.querySelectorAll('.syn-specific-compare > section').length,
      headers:document.querySelectorAll('.syn-specific-data-table th').length,
      lanes:document.querySelectorAll('.syn-specific-lane').length,
      metrics:document.querySelectorAll('select[data-metric-select="true"] option').length || document.querySelectorAll('.syn-specific-attrs strong').length,
      review:document.querySelectorAll('.syn-specific-attrs dt').length
     }));
     if(!activeLabelCount(spec,i,counts))recordFail(spec,width,"missing-field-groups",{tab:i,counts,expected:spec.fields.length});
    }
    reports.push({id:spec.id,module:spec.module,layout:spec.layout,width,tabCount,locked,...info});
   }catch(error){recordFail(spec,width,"runtime",String(error).slice(0,350));}
   finally{await page.close();}
  }
 }
}finally{await context.close();await browser.close();}
const summary={scope:"252 source-specific UI prototypes plus 9 previously specialized identity routes",allCanonicalRoutes:registry.screen_register.length,screenSpecificContracts:specs.length,viewports:viewports,checks:reports.length,expectedViewportChecks:specs.length*viewports.length,layoutCounts:layouts,moduleScreenshots:sampleShots.length,networkCalls:networkCalls.length,failures:failures.slice(0,300),failuresTotal:failures.length,limits:["Automated UI checks are not clinical workflow approval","Clinical field dictionaries and permission matrices remain under review","No authorized patient records or clinical submissions","Not a full EN/FR localization or manual WCAG audit","No mobile native builds or clinical signoff"]};
writeFileSync(resolve(out,"all-screen-audit.json"),JSON.stringify(summary,null,2)+"\n");
console.log(JSON.stringify({routes:summary.allCanonicalRoutes,screenSpecific:summary.screenSpecificContracts,viewports:summary.checks,expected:summary.expectedViewportChecks,layoutCounts:layouts,screenshotModules:summary.moduleScreenshots,networkCalls:summary.networkCalls,failures:summary.failuresTotal,examples:summary.failures.slice(0,6)},null,2));
assert.equal(summary.checks,summary.expectedViewportChecks,"All expected viewport runs must complete");
assert.equal(summary.failuresTotal,0,"Inspect all-screen-audit.json for missing controls, overlap or tabs");
assert.equal(summary.moduleScreenshots,40,"One mobile screenshot per module");
assert.equal(summary.networkCalls,0,"No unauthorized clinical API requests");
