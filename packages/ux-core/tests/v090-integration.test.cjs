const {test}=require('node:test');const assert=require('node:assert/strict');
const {ClinicalApiClient,createSensitiveStateClear,PatientSwitchCoordinator,mobileTaskDecision,clinicalSidebar,safeClinicalDeepLink}=require('../dist');
const ID='123e4567-e89b-42d3-a456-426614174000',TOKEN='SYNQORIA_MOCK_BEARER_TOKEN';
test('P01-B0019 bounded HTTP streaming rejects oversized content without content-length',async()=>{
 const payload=JSON.stringify({patientId:ID,identityStatus:'verified',revision:1});
 const response=()=>new Response(payload,{status:200,headers:{'Content-Type':'application/json'}});
 const read=f=>new ClinicalApiClient('https://safe.example/',f).readPatient(ID,TOKEN);
 assert.equal((await read(async()=>response())).status,'ok');
 assert.equal((await read(async()=>new Response('x'.repeat(5000),{headers:{'content-type':'application/json'}}))).status,'unavailable');
 assert.equal((await read(async()=>new Response(payload,{headers:{'content-type':'application/json','content-length':'foo'}}))).status,'unavailable');
 assert.equal((await read(async()=>new Response(payload,{headers:{'content-type':'text/plain'}}))).status,'unavailable');
 assert.equal((await read(async()=>new Response('{broken',{headers:{'content-type':'application/json'}}))).status,'unavailable');
});
test('P02-B0018 clear caches and draft index before new patient authorization, failures deny access',async()=>{
 const order=[];let fail=false;
 const cleaner=createSensitiveStateClear({abortPatientRequests:()=>order.push('abort'),clearMemoryQueries:async()=>order.push('queries'),clearPersistedDraftIndex:async()=>{order.push('drafts');if(fail)throw Error('disk');},clearTelemetryContext:()=>order.push('telemetry')});
 const s=new PatientSwitchCoordinator({clearSensitivePatientState:cleaner,authorizePatient:async()=>{order.push('authorize');return true;}});
 s.request('patient_00000001');await s.executeReauthorization();assert.deepEqual(order,['abort','queries','drafts','telemetry','authorize']);
 order.length=0;fail=true;s.request('patient_00000002');await s.executeReauthorization();assert.deepEqual(order,['abort','queries','drafts','telemetry']);assert.equal(s.snapshot().patientId,null);
});
test('P02-B0019 mobile gate refuses offline, stale, and unentitled clinical tasks',()=>{
 const verified={kind:'verified',expiresAt:1000,tenantId:ID,facilityId:ID,capabilities:['patient:read']};
 assert.equal(mobileTaskDecision('patients',verified,true,999),'allowed-shell');
 assert.equal(mobileTaskDecision('patients',verified,false,999),'offline-locked');
 assert.equal(mobileTaskDecision('patients',verified,true,1000),'locked-sign-in');
 assert.equal(mobileTaskDecision('tasks',verified,true,999),'not-authorized');
 assert.equal(mobileTaskDecision('patients',{kind:'locked'},true,0),'locked-sign-in');
});
test('P02-B0020 sidebar contains authorized module roots only, no details',()=>{
 const roots=[{id:'clinical-work',href:'/workforce/clinical',kind:'root',enabled:true,entitled:true},{id:'b',href:'/workforce/clinical/detail',kind:'detail',enabled:true,entitled:true},{id:'c',href:'/workforce/clinical',kind:'root',enabled:true,entitled:true},{id:'d',href:'/workforce/patient/123',kind:'root',enabled:true,entitled:true},{id:'revenue',href:'/workforce/revenue',kind:'root',enabled:true,entitled:false}];
 assert.deepEqual(clinicalSidebar(roots).map(x=>x.id),['clinical-work']);
 assert.equal(safeClinicalDeepLink('/workforce/patients/detail/'+ID),false);
});
