const test=require('node:test');const assert=require('node:assert/strict');
const {PatientSwitchCoordinator}=require('../dist');
const a='patient__demo_001',b='patient__demo_002';
test('cache clear precedes backend verification, no authorization from UI alone',async()=>{
 const events=[];const s=new PatientSwitchCoordinator({clearSensitivePatientState:async()=>{events.push('clear');},authorizePatient:async()=>{events.push('backend');return true;}});
 assert.equal(s.request(a).stage,'reauthorizing');await s.executeReauthorization();
 assert.deepEqual(events,['clear','backend']);assert.equal(s.snapshot().patientId,a);
 s.markDirty();assert.equal(s.request(b).stage,'confirm-unsaved');
 await s.executeReauthorization();assert.equal(s.snapshot().patientId,a);
 s.cancel();assert.equal(s.snapshot().dirty,true);
 s.request(b);s.saveRequested();s.serverSaveConfirmed(false);assert.equal(s.snapshot().stage,'saving-draft');
 s.serverSaveConfirmed(true);await s.executeReauthorization();assert.equal(s.snapshot().patientId,b);
});
test('clear failure and authorization denial fail closed',async()=>{
 const s=new PatientSwitchCoordinator({clearSensitivePatientState:async()=>{throw Error('secret');},authorizePatient:async()=>true});
 s.request(a);await s.executeReauthorization();assert.equal(s.snapshot().patientId,null);
 const denied=new PatientSwitchCoordinator({clearSensitivePatientState:async()=>{},authorizePatient:async()=>false});
 denied.request(a);await denied.executeReauthorization();assert.equal(denied.snapshot().patientId,null);
});
test('dispose invalidates late asynchronous authorization response',async()=>{
 let resolve;const s=new PatientSwitchCoordinator({clearSensitivePatientState:async()=>{},authorizePatient:()=>new Promise(r=>{resolve=r;})});
 s.request(a);const promise=s.executeReauthorization();await new Promise(r=>setImmediate(r));
 s.dispose();resolve(true);await promise;assert.equal(s.snapshot().patientId,null);
 assert.equal(s.request(b).patientId,null);
});
