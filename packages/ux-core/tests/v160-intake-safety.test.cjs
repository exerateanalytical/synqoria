const test=require('node:test');const assert=require('node:assert/strict');
const {guardedIntakeRoute,intakeA11ySummary,VolatileRegistrationSession,RegistrationMobileLifecycle}=require('../dist/index.js');
test('P02-B0040 canonical route only; no query/deep-link escalation',()=>{
 const g=new Set(['patient:register']);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',true,g).allowed,true);
 for(const path of ['/workforce/patients/new?patient=1','/workforce/patients/new#edit','/workforce/patients/new/1','/workforce/patients//new','/workforce/patients/%6eew','/workforce/patients/new\\other','/workforce/patients/new?redirect=/admin'])
  assert.equal(guardedIntakeRoute(path,'workforce',true,g).allowed,false,path);
 for(const shell of ['portal','platform'])assert.equal(guardedIntakeRoute('/workforce/patients/new',shell,true,g).allowed,false);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',false,g).allowed,false);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',true,new Set()).allowed,false);
});
test('P02-B0041 bilingual accessible summary has stable nonidentifying messages',()=>{
 for(const locale of ['en','fr']){
  const s=intakeA11ySummary(locale,['REVIEW','ACCESS','REVIEW','OFFLINE']);
  assert.deepEqual(s.items.map(x=>x.code),['ACCESS','REVIEW','OFFLINE']);
  assert.equal(s.canSubmit,false);assert.equal(s.role,'alert');assert.equal(s.focusTarget,'intake-status');
  assert.ok(!JSON.stringify(s).includes('patientName'));
 }
});
test('P02-B0042 background and offline destroy synthetic volatile intake draft',()=>{
 const store=new VolatileRegistrationSession(),life=new RegistrationMobileLifecycle(store);
 const scope={tenant:'synthetic-tenant',facility:'synthetic-facility',actor:'synthetic-actor',sessionEpoch:1};
 assert.equal(life.resume(scope,true,'en').editable,false);assert.equal(life.active,true);
 assert.equal(store.update(scope,{name:'Synthetic Person'}),true);
 life.suspend();assert.equal(life.active,false);assert.equal(store.snapshot(scope),null);
 life.resume(scope,true,'fr');assert.equal(store.snapshot(scope).name,'');
 store.update(scope,{name:'Synthetic Person 2'});life.disconnect();assert.equal(store.snapshot(scope),null);
 assert.equal(life.resume(scope,false,'en').viewOnly,true);assert.equal(life.active,false);
});
