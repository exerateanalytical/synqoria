const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {execFileSync}=require("node:child_process");
const ts=(()=>{try{return require("typescript");}
 catch{return require(execFileSync("npm",["root","-g"],{encoding:"utf8"}).trim()+"/typescript");}})();
const root=path.join(__dirname,"..");
const read=p=>fs.readFileSync(path.join(root,p),"utf8");
const views="apps/web/app/workforce/clinical/SharedOps001Workspace.tsx";
const route="apps/web/app/workforce/clinical/[screenId]/page.tsx";
const css="apps/web/app/workforce/clinical/ops001-workspace.css";
const ids=["G-S01","G-S02","G-S04","G-S05","G-S07","G-S08","G-S09","M01-S02"];
test("OPS001 eight exact canonical IDs mount distinct source-backed screen flows",()=>{
 const src=read(views),routing=read(route);
 for(const id of ids){
  assert.ok(src.includes(id),id);
  assert.ok(routing.includes(id),id);
  assert.match(src,new RegExp("screenId===\""+id+"\""));
 }
 assert.match(routing,/SharedOps001Workspace/);
 assert.match(src,/data-acceptance="partial-not-certified"/);
 assert.match(src,/EmergencyAccess/);
 assert.match(src,/RegistrationGate/);
 assert.match(src,/PatientLookup/);
 assert.match(src,/RoleHub/);
 assert.match(src,/SessionLock/);
 assert.ok(!src.includes("localStorage"));
 assert.ok(!src.includes("sessionStorage"));
 assert.ok(src.includes('ctrl.current?.abort();setId('));
 assert.ok(src.includes('if(scope!=="verified")erase()'));
 assert.ok(src.includes('r.headers.get("cache-control")'));
 assert.ok(src.includes('r.headers.get("content-type")'));
});
test("OPS001 auth, lookup and logout Next routes are source wired to the same verified provider",()=>{
 const expected=[
  ["apps/web/app/api/auth/status/route.ts","ops001PublicStatusResponse"],
  ["apps/web/app/api/auth/oidc/start/route.ts","login"],
  ["apps/web/app/api/auth/oidc/callback/route.ts","callback"],
  ["apps/web/app/api/auth/logout/route.ts","logout"],
  ["apps/web/app/api/clinical/patient-lookup/route.ts","patient-lookup"],
  ["apps/web/app/api/ops001/facility/route.ts","facility"]
 ];
 for(const [p,marker] of expected){
  const source=read(p);
  assert.ok(source.includes(marker),p);
  assert.ok(source.includes('runtime="nodejs"'),p);
  assert.ok(source.includes('dynamic="force-dynamic"'),p);
 }
});
test("OPS001 TypeScript compiler parses all new workspaces and route files",()=>{
 const files=[views,route,
 "apps/web/app/api/auth/status/route.ts",
 "apps/web/app/api/auth/oidc/start/route.ts",
 "apps/web/app/api/auth/oidc/callback/route.ts",
 "apps/web/app/api/auth/logout/route.ts",
 "apps/web/app/api/clinical/patient-lookup/route.ts",
 "apps/web/app/api/ops001/facility/route.ts"];
 for(const f of files){
  const src=read(f);
  const r=ts.transpileModule(src,{
   fileName:f,reportDiagnostics:true,
   compilerOptions:{jsx:ts.JsxEmit.ReactJSX,strict:true,
    target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}
  });
  const errors=(r.diagnostics||[]).filter(d=>d.category===ts.DiagnosticCategory.Error);
  assert.equal(errors.length,0,f+": "+errors.map(x=>ts.flattenDiagnosticMessageText(x.messageText," ")).join("; "));
 }
});
test("OPS001 design uses responsive flat colors, accessible focus and no glow",()=>{
 const s=read(css).toLowerCase();
 for(const value of ["#073656","#1664d4","#f7f5f0",":focus-visible","max-width:767px","max-width:375px",
  "prefers-reduced-motion","forced-colors"])assert.ok(s.includes(value),value);
 for(const forbidden of ["linear-gradient(", "radial-gradient(", "box-shadow:", "text-shadow:", "filter:drop-shadow(",
  "transform:scale("])assert.ok(!s.includes(forbidden),forbidden);
});
