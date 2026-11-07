import assert from "node:assert/strict";
import {readFileSync,writeFileSync,mkdirSync} from "node:fs";
import {resolve} from "node:path";
import {chromium} from "playwright";

const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const out=process.env.SYNQORIA_UI_AUDIT_DIR||"/tmp/synqoria-ui-identity-audit";
mkdirSync(out,{recursive:true});
const register=JSON.parse(readFileSync(new URL("../registers/canonical-screens.json",import.meta.url),"utf8"));
const ids=["G-S01","G-S02","G-S03","M01-S01","M01-S02","M01-S03","M01-S04","M01-S05","M01-S06"];
const widths=[320,768,1440];
const result={scope:"nine canonical auth and M01 screen-specific preview routes",testedAt:new Date().toISOString(),routes:[],viewports:[],interactions:[],issues:[],prohibitedClinicalActions:[],claim:"Browser UI preview smoke evidence only; no approved workflow, authorization, API, usability or clinical release"};
const record=(name,passed,detail="")=>result.interactions.push({name,passed,detail});
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
try{
 for(const screenId of ids){
  assert.ok(register.screen_register.some(s=>s.id===screenId),screenId);
  for(const width of widths){
   const page=await browser.newPage({viewport:{width,height:880},deviceScaleFactor:1});
   const apiRequests=[];
   page.on("request",req=>{
    const u=new URL(req.url());
    if(u.pathname.startsWith("/api/")||u.pathname.startsWith("/auth/")||u.pathname.startsWith("/patients/"))apiRequests.push(u.pathname);
   });
   const resp=await page.goto(base+"/ui/catalog/"+screenId,{waitUntil:"domcontentloaded",timeout:30000});
   assert.equal(resp?.status(),200,screenId);
   await page.locator(".syn-identity-preview").waitFor({timeout:12000});
   const tabs=await page.getByRole("tab").count();
   const focus=await page.getByRole("tab").first().getAttribute("aria-selected");
   const locked=await page.locator(".syn-identity-actions .syn-locked").isDisabled();
   const visual=await page.evaluate(()=>{
    const body=document.documentElement;
    const overflow=body.scrollWidth>window.innerWidth+2;
    const buttons=[...document.querySelectorAll("button")].filter(b=>{
     const r=b.getBoundingClientRect(),s=getComputedStyle(b);
     return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden";
    });
    const glow=buttons.filter(b=>{
     const s=getComputedStyle(b);return s.boxShadow!=="none"||s.textShadow!=="none"||s.filter.includes("drop-shadow");
    });
    const overlap=[];
    for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){
     const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();
     const ix=Math.min(a.right,b.right)-Math.max(a.left,b.left);
     const iy=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
     if(ix>3&&iy>3)overlap.push([buttons[i].textContent?.slice(0,25),buttons[j].textContent?.slice(0,25)]);
    }
    return {bodyWidth:body.scrollWidth,overflow,glow:glow.length,overlap:overlap.length,overlapExamples:overlap.slice(0,3)};
   });
   result.viewports.push({screenId,width,tabs,locked,firstTabSelected:focus==="true",apiRequests, ...visual});
   if(visual.overflow||visual.glow||visual.overlap||tabs!==3||!locked||focus!=="true"||apiRequests.length)result.issues.push({screenId,width,tabs,locked,focus,apiRequests,visual});
   await page.screenshot({path:resolve(out,screenId+"-"+width+".png"),fullPage:true});
   if(screenId==="G-S01"&&width===1440){
    const pw=page.locator('input[type="password"]');
    assert.equal(await pw.count(),1);
    assert.equal(await pw.isDisabled(),true);
    await page.getByRole("tab").first().focus();
    await page.keyboard.press("ArrowRight");
    assert.equal(await page.getByRole("tab").nth(1).getAttribute("aria-selected"),"true");
    record("G-S01 safe credential and keyboard tab behavior",true);
   }
   if(screenId==="G-S02"&&width===1440){
    assert.equal(await page.locator('input[name="code"]').isDisabled(),true);
    record("G-S02 challenge input locked",true);
   }
   if(screenId==="G-S03"&&width===1440){
    assert.equal(await page.locator('select[name="facility"]').isDisabled(),true);
    record("G-S03 facility cannot be chosen without a server",true);
   }
   if(screenId==="M01-S01"&&width===1440){
    await page.getByRole("button",{name:"Show advanced filters"}).click();
    assert.ok(await page.locator(".syn-identity-filter-panel").isVisible());
    await page.getByRole("button",{name:"Reset filters"}).click();
    assert.match(await page.locator(".syn-identity-status").innerText(),/no lookup was sent/);
    record("M01-S01 search filters and no unauthorized lookup",true);
   }
   if(screenId==="M01-S02"&&width===1440){
    await page.locator('select[name="birthInformation"]').selectOption("known");
    assert.equal(await page.locator('input[name="dateOfBirth"]').isDisabled(),false);
    assert.equal(await page.locator('input[name="estimatedAge"]').isDisabled(),true);
    await page.locator('input[name="dateOfBirth"]').fill("2099-01-01");
    await page.locator('.syn-identity-form button[type="submit"]').click();
    assert.match(await page.locator(".syn-identity-feedback").innerText(),/future/);
    assert.match(await page.locator(".syn-identity-warning").innerText(),/Independent duplicate review/);
    assert.equal(await page.locator(".syn-identity-actions .syn-locked").isDisabled(),true);
    await page.getByRole("button",{name:"Clear demonstration entries"}).click();
    await page.getByRole("tab").first().click();
    assert.equal(await page.locator('select[name="birthInformation"]').inputValue(),"unknown");
    await page.getByRole("button",{name:"Français"}).click();
    assert.match(await page.locator(".syn-identity-alert").innerText(),/APERÇU DE CONCEPTION/);
    record("M01-S02 birth-state conditional entry, validation, independent review, clear and FR labels",true);
   }
   if((screenId==="M01-S05"||screenId==="M01-S06")&&width===1440){
    assert.equal(await page.locator(".syn-identity-actions .syn-locked").isDisabled(),true);
    record(screenId+" merge approval remains locked",true);
   }
   await page.close();
  }
  result.routes.push(screenId);
 }
}finally{await browser.close();}
writeFileSync(resolve(out,"identity-audit.json"),JSON.stringify(result,null,2)+"\n");
console.log(JSON.stringify({routes:result.routes.length,viewports:result.viewports.length,interactions:result.interactions.length,issues:result.issues.length},null,2));
assert.equal(result.routes.length,9);
assert.equal(result.viewports.length,27);
assert.equal(result.issues.length,0,"Inspect identity-audit.json and screenshots; 9 screen UX specimens must pass visual smoke checks");
