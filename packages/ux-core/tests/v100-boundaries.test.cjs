const test=require('node:test');const assert=require('node:assert/strict');
const {PatientRequestFence,allowedOfflineAction,verifiedCellEndpoint}=require('../dist/index.js');
test('patient-bound response: active epoch only',()=>{
 const fence=new PatientRequestFence();const a=fence.begin('patient-1','facility-1');
 assert.equal(fence.accepts(a,'patient-1','facility-1',true),true);
 assert.equal(fence.accepts(a,'patient-1','facility-2',true),false);
 assert.equal(fence.accepts(a,'patient-1','facility-1',false),false);
 const b=fence.begin('patient-2','facility-1');assert.equal(fence.accepts(a,'patient-1','facility-1',true),false);
 assert.equal(fence.accepts(b,'patient-2','facility-1',true),true);
 fence.invalidate();assert.equal(fence.accepts(b,'patient-2','facility-1',true),false);
});
test('offline task control never asserts a server clinical effect',()=>{
 const base={enrolled:true,sessionValid:true,patientGrantCurrent:true,approvedOfflineDraft:true};
 assert.equal(allowedOfflineAction('draft-note',base),'local-draft-only');
 for(const action of ['sign-note','prescribe','administer-medication','view-cached-summary'])assert.equal(allowedOfflineAction(action,base),'online-verification-required');
 assert.equal(allowedOfflineAction('draft-note',{...base,patientGrantCurrent:false}),'blocked');
 assert.equal(allowedOfflineAction('draft-note',{...base,sessionValid:false}),'blocked');
 assert.equal(allowedOfflineAction('draft-note',{...base,enrolled:false}),'blocked');
 assert.equal(allowedOfflineAction('draft-note',{...base,approvedOfflineDraft:false}),'online-verification-required');
});
test('endpoint provision gate rejects unverified, credentials, localhost, IP and custom ports',()=>{
 assert.equal(verifiedCellEndpoint('https://hospital.example/',true),'https://hospital.example');
 for(const p of ['http://hospital.example/','https://user:pw@hospital.example/','https://localhost/','https://127.0.0.1/','https://hospital.example:8443/','https://hospital.example/abc','https://hospital.example/?tenant=abc'])assert.equal(verifiedCellEndpoint(p,true),null,p);
 assert.equal(verifiedCellEndpoint('https://hospital.example/',false),null);
});
