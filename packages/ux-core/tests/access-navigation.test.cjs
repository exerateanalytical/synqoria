const test=require('node:test'); const assert=require('node:assert/strict');
const {messages,selectLocale,translate,workspaceLabel,workspaces,resolveWorkspaceRoot,shellForPath,allowedMobileTabs}=require('../dist');
test('EN/FR dictionary is complete and does not return missing interface copy',()=>{
 const ek=Object.keys(messages.en).sort();assert.deepEqual(ek,Object.keys(messages.fr).sort());
 for(const key of ek){assert.ok(messages.en[key].trim());assert.ok(messages.fr[key].trim());}
 assert.equal(selectLocale('fr'),'fr');assert.equal(selectLocale('FR'),'en');assert.equal(selectLocale({language:'fr'}),'en');
 assert.equal(translate('fr','home'),'Accueil');
});
test('every navigation workspace has usable translations',()=>{
 for(const w of workspaces){assert.ok(workspaceLabel('en',w).trim());assert.ok(workspaceLabel('fr',w).trim());}
});
test('exact routing denies ungranted, unlicensed, wrong-shell and arbitrary descendants',()=>{
 const ctx={shell:'workforce',grantedCapabilities:new Set(['workspace:patients']),licensedModules:new Set(['M01'])};
 assert.equal(resolveWorkspaceRoot('/workforce/patients',ctx).kind,'allowed');
 for(const path of ['/workforce/patients/abc','/workforce/patients?debug=true','/workforce/patient','/portal/records','//workforce/patients'])
   assert.equal(resolveWorkspaceRoot(path,ctx).kind,'not-workspace-root');
 assert.equal(resolveWorkspaceRoot('/workforce/clinical',ctx).kind,'requires-auth-or-license');
 assert.equal(resolveWorkspaceRoot('/workforce/patients',{...ctx,licensedModules:new Set()}).kind,'requires-auth-or-license');
 assert.equal(resolveWorkspaceRoot('/workforce/patients',{...ctx,grantedCapabilities:new Set()}).kind,'requires-auth-or-license');
});
test('shell identification does not confuse similar routes',()=>{
 for(const shell of ['workforce','portal','platform']) assert.equal(shellForPath(`/${shell}/example`),shell);
 assert.equal(shellForPath('/workforce-malicious'),null); assert.equal(shellForPath('/'),null);
});
test('both mobile shells keep four translated, distinct destinations',()=>{
 for(const shell of ['staff','patient'])for(const lang of ['en','fr']){
  const tabs=allowedMobileTabs(shell,lang);
  assert.equal(tabs.length,4);assert.equal(new Set(tabs.map(x=>x.id)).size,4);
  assert.equal(tabs[3].id,'more');assert.ok(tabs.every(x=>x.label.length>0));
 }
});
