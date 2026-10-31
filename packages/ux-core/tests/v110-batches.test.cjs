const test=require('node:test');const assert=require('node:assert/strict');
const {ScopedClinicalStore,PatientReadPipeline,mobileShellTabs}=require('../dist/index.js');
const passed=(p)=>({status:'ok',patientId:p,identityStatus:'verified',revision:3});
test('P02-B0024 scoped cache denies wrong patient/facility, old epoch, and logout',()=>{
 const store=new ScopedClinicalStore();assert.throws(()=>store.begin('p','f',false));
 const first=store.begin('patient-A','facility-X',true);
 assert.equal(store.commit(first,'patient-A','facility-X',true,passed('patient-B')),false);
 assert.equal(store.commit(first,'patient-A','facility-X',true,passed('patient-A')),true);
 assert.equal(store.read(first,'patient-A','facility-X',true).patientId,'patient-A');
 assert.equal(store.read(first,'patient-A','facility-Y',true),null);
 assert.equal(store.read(first,'patient-A','facility-X',false),null);
 const second=store.begin('patient-B','facility-X',true);
 assert.equal(store.read(first,'patient-A','facility-X',true),null);
 assert.equal(store.commit(first,'patient-A','facility-X',true,passed('patient-A')),false);
 assert.equal(store.commit(second,'patient-B','facility-X',true,passed('patient-B')),true);
 store.clear();assert.equal(store.read(second,'patient-B','facility-X',true),null);
});
test('P02-B0025 stale response is rejected even if transport ignores AbortSignal',async()=>{
 const store=new ScopedClinicalStore();let resolveA,resolveB;
 const pending=new Map();
 const api=(id)=>new Promise(resolve=>{pending.set(id,resolve);});
 const pipeline=new PatientReadPipeline(store,api);
 const a=pipeline.select('patient-A','hospital-1',true);
 const b=pipeline.select('patient-B','hospital-1',true);
 pending.get('patient-B')(passed('patient-B'));assert.equal(await b,true);assert.equal(pipeline.value('patient-B','hospital-1',true).patientId,'patient-B');
 pending.get('patient-A')(passed('patient-A'));assert.equal(await a,false);
 pipeline.invalidate();assert.equal(pipeline.value('patient-B','hospital-1',true),null);
 assert.equal(await pipeline.select('patient-C','hospital-1',false),false);
});
test('P02-B0025 transport error and denied result are never accepted',async()=>{
 const store=new ScopedClinicalStore();const a=new PatientReadPipeline(store,async()=>({status:'unavailable'}));
 assert.equal(await a.select('P','F',true),false);
 const b=new PatientReadPipeline(store,async()=>{throw new Error('network failure')});
 assert.equal(await b.select('P','F',true),false);
});
test('P02-B0026 staff/patient navigation is four labelled tabs and data destinations are locked',()=>{
 const locked={kind:'locked'};
 for(const kind of ['staff','patient'])for(const locale of ['en','fr']){
  const tabs=mobileShellTabs(kind,locale,locked,false,0);
  assert.equal(tabs.length,4);assert.equal(new Set(tabs.map(x=>x.id)).size,4);
  assert.ok(tabs.every(x=>x.label.length&&x.reason.length));
  assert.ok(tabs.filter(x=>x.id!=='more'&&x.id!=='home').every(x=>x.enabled===false));
 }
 const verified={kind:'verified',tenantId:'t',facilityId:'f',expiresAt:100000,capabilities:['patient:read','appointments:view','queue:view']};
 const p=mobileShellTabs('patient','fr',verified,true,100);
 assert.ok(p.some(t=>t.id==='appointments'&&t.label==='Rendez-vous'));
 assert.ok(p.every(t=>t.id==='records'?t.enabled===false:true)); // No protected tab activated until a data-ready native adapter exists.
});
