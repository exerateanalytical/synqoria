/**
 * SYNQ-OPS-001 responsive smoke: real compiled Next.js + Chromium only.
 * Evidence for visual and route behavior, NOT clinical E2E or WCAG certification.
 */
const assert=require("node:assert/strict");
const {spawn,execFileSync}=require("node:child_process");
const fs=require("node:fs");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const {chromium}=(()=>{try{return require("playwright");}
 catch{return require(path.join(execFileSync("npm",["root","-g"],{encoding:"utf8"}).trim(),"playwright"));}})();
const screens=["G-S01","G-S02","G-S04","G-S05","G-S07","G-S08","G-S09","M01-S02"];
const widths=[320,375,768,1024,1440,1920];
const host="http://127.0.0.1:3107";
const output=path.join(root,"evidence","ops001","browser");
fs.mkdirSync(output,{recursive:true});
let serverOutput="";
const server=spawn(process.execPath,[path.join(root,"node_modules","next","dist","bin","next"),"start","-p","3107"],{
 cwd:path.join(root,"apps","web"),env:{...process.env,NEXT_TELEMETRY_DISABLED:"1"},
 stdio:["ignore","pipe","pipe"]
});
server.stdout.on("data",data=>{serverOutput+=String(data).slice(-2500);});
server.stderr.on("data",data=>{serverOutput+=String(data).slice(-2500);});
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function serverReady(){
 for(let tries=0;tries<65;tries++){
  if(server.exitCode!==null)throw Error("Next server failed: "+serverOutput.slice(-1400));
  try{const r=await fetch(host+"/workforce/clinical/G-S01");if(r.status===200)return;}
  catch{}
  await delay(300);
 }
 throw Error("Next web start did not become ready: "+serverOutput.slice(-1400));
}
let browser=null;
(async()=>{
 await serverReady();
 browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
 let checked=0;
 for(const width of widths){
  const context=await browser.newContext({viewport:{width,height:900},
   deviceScaleFactor:1,reducedMotion:"reduce"});
  const page=await context.newPage();
  const failures=[];
  page.on("pageerror",error=>failures.push(error.message));
  for(const id of screens){
   const response=await page.goto(host+"/workforce/clinical/"+id,{waitUntil:"domcontentloaded"});
   assert.equal(response?.status(),200,"route "+id+" at "+width);
   const locator=page.locator('main.ops001-page[data-screen-id="'+id+'"]');
   await locator.waitFor({timeout:20000});
   assert.equal(await locator.getAttribute("data-acceptance"),"partial-not-certified");
   const overflow=await page.evaluate(()=>{
    const w=window.innerWidth;
    return Math.max(document.body.scrollWidth,document.documentElement.scrollWidth)-w;
   });
   assert.ok(overflow<=2,"horizontal document overflow "+overflow+"px at "+width+" for "+id);
   assert.equal(failures.length,0,"client runtime error at "+width+" for "+id+": "+failures.join("; "));
   const h1=page.locator("main.ops001-page h1");
   assert.ok((await h1.textContent()||"").trim().length>5,"screen title missing");
   if(id==="G-S05"){
    assert.equal(await page.locator("#ops001-patient").isDisabled(),true,
     "patient entry must remain locked until verified session");
   }
   if(id==="G-S09"){
    assert.equal(await page.locator(".ops001-card button").isDisabled(),true,
     "no unauthorized emergency-grant action");
   }
   if(width===320&&id==="G-S01"){
    await page.getByRole("button",{name:"FR",exact:true}).click();
    assert.match(await h1.textContent(),/Connexion du personnel/);
    await page.getByRole("button",{name:"EN",exact:true}).click();
   }
   if((width===320&&id==="G-S01")||(width===375&&id==="G-S05")||
      (width===1440&&id==="G-S04")||(width===1920&&id==="G-S09")){
    await page.screenshot({path:path.join(output,id+"-"+width+".png"),fullPage:true});
   }
   checked++;
  }
  await context.close();
 }
 console.log("PASS: "+checked+" true Chromium desktop/mobile route-width checks. 4 evidence screenshots.");
 console.log("QUALIFICATION: no real IdP/PHI/clinical reviewer, no accessibility certification.");
})().catch(error=>{
 console.error("FAIL:",error&&error.stack||String(error));
 process.exitCode=1;
}).finally(async()=>{
 if(browser)await browser.close().catch(()=>{});
 server.kill("SIGTERM");
 await delay(250);
 if(server.exitCode===null)server.kill("SIGKILL");
});
