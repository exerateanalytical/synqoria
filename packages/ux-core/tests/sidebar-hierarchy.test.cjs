const {test}=require('node:test');const assert=require('node:assert/strict');
const {sidebarHierarchy}=require('../dist');
const ctx={shell:'workforce',grantedCapabilities:new Set(['workspace:patients']),licensedModules:new Set(['M01'])};
const id='00000000-0000-4000-8000-000000000001';
test('sidebar is root-only, child detail receives generic breadcrumb',()=>{
 const x=sidebarHierarchy(ctx,`/workforce/patients/detail/${id}`,'en');
 assert.deepEqual(x.entries.map(e=>e.id),['patients']);assert.equal(x.activeRoot,'patients');
 assert.deepEqual(x.breadcrumbs.map(e=>e.label),['Patients','Details']);assert.equal(x.breadcrumbs[1].href,null);
 assert.ok(x.entries.every(e=>!e.href.includes('detail')));
});
test('French labels, denied root and malformed child never leak into hierarchy',()=>{
 assert.deepEqual(sidebarHierarchy(ctx,`/workforce/patients/review/${id}`,'fr').breadcrumbs.map(e=>e.label),['Patients','Réviser']);
 assert.equal(sidebarHierarchy(ctx,'/workforce/clinical','en').activeRoot,null);
 for(const bad of ['/workforce/patients/detail/named-patient','/workforce/patients/details/'+id,'/workforce/patients?x=1','/workforce/patients//edit/'+id])
  assert.equal(sidebarHierarchy(ctx,bad,'fr').activeRoot,null);
});
