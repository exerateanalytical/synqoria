import assert from "node:assert/strict";
import {mkdirSync,readFileSync,writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";
const registry=JSON.parse(readFileSync(new URL("../registers/canonical-screens.json",import.meta.url),"utf8")).screen_register;
const progress=JSON.parse(readFileSync(new URL("../registers/operational-screen-progress-v0.1.json",import.meta.url),"utf8"));
const real=new Set(progress.rows.map(x=>x.id));
const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const folder=process.env.SYNQORIA_ROUTE_EVIDENCE_DIR||"/tmp/synqoria-261-route-evidence";
mkdirSync(folder,{recursive:true});
const errors=[],samples=[],results=[];
const representative={};
for(const s of registry){
 if(!real.has(s.id)&&!(s.layout in representative))representative[s.layout]=s.id;
}
const checked=new Set();
const call=async screen=>{
 const url=base+"/workforce/clinical/"+encodeURIComponent(screen.id);
 try{
  const res=await fetch(url,{redirect:"manual",headers:{Accept:"text/html"}});
  const status=res.status;
  const expected=screen.id==="M31-S01"||screen.id==="M31-S03"?307:200;
  if(status!==expected&&!(expected===307&&status===308))errors.push({id:screen.id,status,expected,issue:"route_resolution"});
  const body=await res.text();
  if(status===200&&body.includes("Internal Server Error"))errors.push({id:screen.id,issue:"server_error"});
  if(status===200&&!real.has(screen.id)&&!body.includes('data-canonical-route="'+screen.id+'"'))errors.push({id:screen.id,issue:"wrong canonical screen rendered"});
  checked.add(screen.id);
 }catch(e){errors.push({id:screen.id,issue:"network_error",detail:String(e).slice(0,180)});}
};
for(let i=0;i<registry.length;i+=8)await Promise.all(registry.slice(i,i+8).map(call));
assert.equal(checked.size,261);

const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
try{
 for(const width of [320,768,1440]){
  for(const [layout,id] of Object.entries(representative)){
   const page=await browser.newPage({viewport:{width,height:870}});
   const pageErrors=[];page.on("pageerror",e=>pageErrors.push(String(e)));
   try{
    const response=await page.goto(base+"/workforce/clinical/"+id,{waitUntil:"domcontentloaded",timeout:20000});
    assert.equal(response?.status(),200);
    await page.locator('[data-canonical-route="'+id+'"]').waitFor();
    const state=await page.locator('[data-domain-state]').getAttribute("data-domain-state");
    assert.equal(state,"unintegrated");
    const flags=await page.evaluate(()=>{
     const el=document.documentElement;
     const actionable=[...document.querySelectorAll(".syn-route-action button")];
     const glow=[...document.querySelectorAll(".syn-route button")].filter(x=>{const cs=getComputedStyle(x);return cs.boxShadow!=="none"||cs.textShadow!=="none"||cs.filter.includes("drop-shadow")});
     return {overflow:el.scrollWidth>innerWidth+2,glow:glow.length,unlocked:actionable.filter(x=>!x.disabled).length,buttonCount:actionable.length};
    });
    if(flags.overflow||flags.glow||flags.unlocked||flags.buttonCount!==1||pageErrors.length)errors.push({id,layout,width,issue:"visual_or_action_gate",flags,pageErrors});
    if(width!==768){const name=id+"-"+width+".png";await page.screenshot({path:resolve(folder,name),fullPage:true});samples.push(name);}
    if(width===320){
     await page.getByRole("button",{name:/workflow and exceptions/i}).click();
     assert.equal(await page.locator('[data-active-tab]').getAttribute("data-active-tab"),"workflows");
     await page.getByRole("button",{name:/related document samples/i}).click();
     assert.equal(await page.locator('[data-active-tab]').getAttribute("data-active-tab"),"documents");
     await page.getByRole("button",{name:/required domain data/i}).click();
     assert.equal(await page.locator('[data-active-tab]').getAttribute("data-active-tab"),"screen");
     await page.getByRole("button",{name:/check current server authorization/i}).click();
     await page.locator('[data-staff-status="login-required"]').waitFor({timeout:10000});
     // All rendered entries remain disabled even if the clinician clicks around.
     const enabledActions=await page.locator(".syn-route-action button:not([disabled])").count();
     assert.equal(enabledActions,0);
    }
    results.push({id,layout,width,...flags});
   }catch(e){errors.push({id,layout,width,issue:"browser",detail:String(e).slice(0,400)});}
   finally{await page.close();}
  }
 }
 const index=await browser.newPage({viewport:{width:320,height:850}});
 await index.goto(base+"/workforce/clinical",{waitUntil:"domcontentloaded"});
 assert.equal(await index.locator('[data-route-directory="261"]').count(),1);
 const original=await index.locator("tbody tr").count();
 if(original!==261)errors.push({issue:"directory_not_complete",got:original});
 await index.getByLabel("Filter by implementation").selectOption("locked");
 const lockedRows=await index.locator("tbody tr").count();
 if(lockedRows!==251)errors.push({issue:"directory_locked_count",got:lockedRows});
 await index.getByLabel("Filter by implementation").selectOption("all");
 await index.getByLabel("Filter by module").selectOption("M03");
 const triageCount=registry.filter(x=>x.module==="M03").length;
 if(await index.locator("tbody tr").count()!==triageCount)errors.push({issue:"module_filter",got:await index.locator("tbody tr").count()});
 await index.getByLabel("Search canonical screens").fill("M03-S02");
 if(await index.locator("tbody tr").count()!==1)errors.push({issue:"directory_search",got:await index.locator("tbody tr").count()});
 await index.close();
 const unknown=await fetch(base+"/workforce/clinical/M99-S99");
 if(unknown.status!==404)errors.push({issue:"unknown_route_leaked",status:unknown.status});
}finally{await browser.close();}
const report={canonicalRoutes:registry.length,visited:checked.size,lockedRouteCount:251,integratedScreenSlices:10,clinicalAccepted:0,layouts:Object.keys(representative).length,viewportCases:results.length,expectedViewportCases:21,errors,samples:samples.length,limitations:["No production identity issuer","No verified PostgreSQL clinical write pipelines for 251 locked domains","Module-specific high-risk clinical workflows remain unavailable"]};
writeFileSync(resolve(folder,"canonical-261-routes-browser.json"),JSON.stringify(report,null,2)+"\n");
console.log(JSON.stringify({canonicalRoutes:report.canonicalRoutes,visited:report.visited,lockedRouteCount:report.lockedRouteCount,integratedScreenSlices:report.integratedScreenSlices,viewports:report.viewportCases,samples:report.samples,errors:report.errors.length},null,2));
if(errors.length)console.error("BROWSER_FINDINGS",JSON.stringify(errors.slice(0,25),null,2));
assert.equal(results.length,21);
assert.equal(errors.length,0);
