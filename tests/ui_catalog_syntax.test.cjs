const test=require("node:test");
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {execFileSync}=require("node:child_process");
const ts=(()=>{try{return require("typescript")}catch{return require(execFileSync("npm",["root","-g"],{encoding:"utf8"}).trim()+"/typescript")}})();
const files=[
 "apps/web/app/ui/catalog/page.tsx",
 "apps/web/app/ui/catalog/[screenId]/page.tsx",
 "apps/web/app/ui/catalog/components/page.tsx",
 "apps/web/app/ui/catalog/ScreenPreviewClient.tsx",
 "apps/web/app/ui/catalog/ComponentGallery.tsx"
];
for(const file of files)test("TSX syntax: "+file,()=>{
 const code=fs.readFileSync(path.resolve(process.cwd(),file),"utf8");
 const result=ts.transpileModule(code,{fileName:file,reportDiagnostics:true,compilerOptions:{jsx:ts.JsxEmit.ReactJSX,strict:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}});
 const errors=(result.diagnostics||[]).filter(d=>d.category===ts.DiagnosticCategory.Error);
 assert.deepEqual(errors.map(d=>ts.flattenDiagnosticMessageText(d.messageText," ")),[]);
});
console.log("TSX syntax-only checks: not a Next build, type check or browser visual audit");
