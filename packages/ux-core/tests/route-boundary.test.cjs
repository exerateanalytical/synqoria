const test=require('node:test');const assert=require('node:assert/strict');
const {classifySafeRoute,sidebarRoots}=require('../dist');
const ctx={shell:'workforce',grantedCapabilities:new Set(['workspace:patients','workspace:clinical']),licensedModules:new Set(['M01','M03'])};
const id='123e4567-e89b-42d3-a456-426614174000';
test('authorized root appears, child is never promoted to sidebar',()=>{
 assert.deepEqual(classifySafeRoute('/workforce/patients',ctx),{kind:'root',workspaceId:'patients'});
 assert.deepEqual(classifySafeRoute('/workforce/patients/detail/'+id,ctx),{kind:'feature-child-unimplemented',workspaceId:'patients',action:'detail',opaqueId:id});
 assert.deepEqual(sidebarRoots(ctx).map(x=>x.href),['/workforce/clinical','/workforce/patients']);
});
test('cross-shell, missing grants and unlicensed modules deny',()=>{
 for(const path of ['/portal/records','/platform/security','/workforce/pharmacy','/workforce/patients/edit/'+id]){
  const denied=path.includes('edit')?{...ctx,grantedCapabilities:new Set()}:ctx;
  assert.equal(classifySafeRoute(path,denied).kind,'denied-or-unavailable');
 }
});
test('sensitive URLs, traversal, unsupported children and invalid IDs deny',()=>{
 for(const path of ['/workforce/patients?patient=123','/workforce/patients#x','/workforce/patients//detail/'+id,'/workforce/patients/%2e%2e',
 '/workforce/patients/detail/patient_name','/workforce/patients/create','/workforce/patients/detail/'+id+'/tab','/workforce/patients/detail/123e4567-e89b-02d3-a456-426614174000'])
  assert.equal(classifySafeRoute(path,ctx).kind,'denied-or-unavailable',path);
});
