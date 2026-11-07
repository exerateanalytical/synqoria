import assert from "node:assert/strict";
import {mkdirSync,writeFileSync} from "node:fs";
import {chromium} from "playwright";
const base=process.env.SYNQORIA_UI_BASE_URL||"http://127.0.0.1:3000";
const out=process.env.SYNQORIA_UNIFIED_DOC_EVIDENCE||"/tmp/synqoria-unified-document-evidence";
mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const report={route:"/hospital-documents",widths:[320,768,1440],tests:[],failures:[],notes:["Sample-only UI; no medical, financial, privacy or legal issuance","No hospital-specific field dictionary or complete English/French terminology certification"]};
try{
 for(const width of report.widths){
  const page=await browser.newPage({viewport:{width,height:900}});
  const js=[];page.on("pageerror",err=>js.push(String(err)));
  const r=await page.goto(base+"/hospital-documents",{waitUntil:"domcontentloaded",timeout:30000});
  assert.equal(r?.status(),200);
  await page.locator(".syn-unified-docs").waitFor();
  const text=await page.locator(".syn-unified-stats").innerText();
  if(!text.includes("662")||!text.includes("40")||!text.includes("18"))report.failures.push({width,problem:"missing expected counts",text});
  const info=await page.evaluate(()=>{
   const root=document.documentElement;
   const buttons=[...document.querySelectorAll("button")].filter(b=>{const r=b.getBoundingClientRect(),s=getComputedStyle(b);return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden"});
   const glow=buttons.filter(b=>{const s=getComputedStyle(b);return s.boxShadow!=="none"||s.textShadow!=="none"||s.filter.includes("drop-shadow")}).length;
   const overlaps=[];for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++){const a=buttons[i].getBoundingClientRect(),b=buttons[j].getBoundingClientRect();if(Math.min(a.right,b.right)-Math.max(a.left,b.left)>3&&Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>3)overlaps.push([buttons[i].textContent?.trim(),buttons[j].textContent?.trim()]);}
   return {overflow:root.scrollWidth>innerWidth+2,glow,overlap:overlaps.length,overlaps:overlaps.slice(0,2)};
  });
  if(info.overflow||info.glow||info.overlap||js.length)report.failures.push({width,problem:"layout-runtime",info,js});
  assert.equal(await page.locator(".syn-unified-card").count(),30,"First page should have 30 cards");
  await page.getByRole("combobox",{name:"Template family"}).selectOption("pdf-sample");
  assert.match(await page.locator(".syn-unified-results").innerText(),/304/);
  await page.getByRole("combobox",{name:"Template family"}).selectOption("structured-form");
  assert.match(await page.locator(".syn-unified-results").innerText(),/376/);
  await page.getByRole("combobox",{name:"Hospital module"}).selectOption("M40");
  assert.ok((await page.locator(".syn-unified-card").count())>0,"M40 must have a form");
  await page.getByRole("button",{name:"Clear all filters"}).click();
  await page.locator('input[type=search]').fill("laboratory");
  assert.ok((await page.locator(".syn-unified-card").count())>0);
  await page.getByRole("button",{name:"Clear all filters"}).click();
  await page.screenshot({path:out+"/unified-"+width+".png",fullPage:true});
  report.tests.push({width,info,cardCount:30});
  await page.close();
 }
 const sample=await browser.newPage();
 await sample.goto(base+"/ui/catalog/M40-S01",{waitUntil:"domcontentloaded"});
 const links=await sample.locator('a[href^="/documents/"]').count();
 if(links<1)report.failures.push({problem:"M40 screen has no linked PDF forms",links});
 await sample.close();
}finally{await browser.close();}
writeFileSync(out+"/audit.json",JSON.stringify(report,null,2)+"\n");
console.log(JSON.stringify({layouts:report.tests.length,failures:report.failures.length,findings:report.failures.slice(0,3)},null,2));
assert.equal(report.tests.length,3);
assert.equal(report.failures.length,0);
