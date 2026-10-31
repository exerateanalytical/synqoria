// TypeScript compiler transpile diagnostics validate TSX syntax without pretending to
// resolve unavailable Next/Expo/React packages or verify actual application builds.
const {execFileSync}=require('node:child_process');
const ts=(()=>{try{return require('typescript')}catch{return require(execFileSync('npm',['root','-g'],{encoding:'utf8'}).trim()+'/typescript')}})();
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
let checked=0;
for(const f of ['apps/web/components/ShellFrame.tsx','apps/web/components/TriageCaseWorkspace.tsx','apps/staff-mobile/App.tsx','apps/staff-mobile/RegistrationPreview.tsx','apps/patient-mobile/App.tsx']){
 const src=fs.readFileSync(path.join(process.cwd(),f),'utf8');
 const result=ts.transpileModule(src,{fileName:f,reportDiagnostics:true,compilerOptions:{jsx:ts.JsxEmit.ReactJSX,strict:true,target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}});
 const errors=(result.diagnostics||[]).filter(d=>d.category===ts.DiagnosticCategory.Error);
 assert.equal(errors.length,0,`${f}: ${errors.map(d=>ts.flattenDiagnosticMessageText(d.messageText,' ')).join('; ')}`);
 checked++;
}
console.log(`PASS: ${checked} TSX syntax-only parses; NO dependency/type/native build assertions`);
