const test=require('node:test');const assert=require('node:assert/strict');
const {emptyPatientContext,transitionPatientContext:move}=require('../dist');
const A='patient__demo_001',B='patient__demo_002';
test('a new target clears old context and enters reauthorization',()=>{
 let s=move(emptyPatientContext,{type:'request-switch',targetId:A});
 assert.deepEqual(s,{stage:'reauthorizing',patientId:null,targetId:A,clearPatientCaches:true,clearSensitiveTelemetry:true});
 s=move(s,{type:'authorize-target',targetId:B,backendAccessVerified:true});assert.equal(s.stage,'reauthorizing');
 s=move(s,{type:'authorize-target',targetId:A,backendAccessVerified:false});assert.equal(s.stage,'reauthorizing');
 s=move(s,{type:'authorize-target',targetId:A,backendAccessVerified:true});assert.deepEqual(s,{stage:'active',patientId:A,dirty:false});
});
test('unsaved work requires confirmation; cancel preserves draft',()=>{
 let s=move(emptyPatientContext,{type:'request-switch',targetId:A});
 s=move(s,{type:'authorize-target',targetId:A,backendAccessVerified:true});
 s=move(s,{type:'mark-dirty'}); s=move(s,{type:'request-switch',targetId:B});
 assert.equal(s.stage,'confirm-unsaved');assert.equal(s.patientId,A);
 let cancelled=move(s,{type:'cancel'});assert.deepEqual(cancelled,{stage:'active',patientId:A,dirty:true});
 assert.equal(move(s,{type:'authorize-target',targetId:B,backendAccessVerified:true}).stage,'confirm-unsaved');
});
test('save requests do not imply server persistence; failed saves remain guarded',()=>{
 let s={stage:'confirm-unsaved',patientId:A,targetId:B};
 s=move(s,{type:'request-save-draft'});assert.equal(s.stage,'saving-draft');
 s=move(s,{type:'draft-save-confirmed',confirmedByServer:false});assert.equal(s.stage,'saving-draft');
 s=move(s,{type:'draft-save-confirmed',confirmedByServer:true});assert.equal(s.stage,'reauthorizing');
 assert.equal(s.patientId,null);
});
test('discard requires explicit confirmation and cannot bypass backend authorization',()=>{
 let s=move({stage:'confirm-unsaved',patientId:A,targetId:B},{type:'discard'});
 assert.equal(s.stage,'reauthorizing');
 s=move(s,{type:'authorization-denied'});assert.deepEqual(s,emptyPatientContext);
});
test('invalid IDs cannot enter patient navigation state',()=>{
 for(const targetId of ['patient name','/patients/123','?patient=abc','<script>x</script>','short','p'.repeat(130)])
  assert.deepEqual(move(emptyPatientContext,{type:'request-switch',targetId}),emptyPatientContext);
});
test('same patient and invalid action order do not change state',()=>{
 const a={stage:'active',patientId:A,dirty:true};
 assert.deepEqual(move(a,{type:'request-switch',targetId:A}),a);
 assert.deepEqual(move(a,{type:'discard'}),a);
 assert.deepEqual(move(a,{type:'authorize-target',targetId:B,backendAccessVerified:true}),a);
});
