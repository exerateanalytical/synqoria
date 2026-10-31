const test=require('node:test');const assert=require('node:assert/strict');
const {duplicateStatus,VolatileRegistrationSession,RegistrationContextFence,registrationActionPresentation}=require('../dist/index.js');
test('P02-B0037 review UI never discloses candidate identity/count or enables submission',()=>{
 for(const state of ['NOT_CHECKED','REVIEW_REQUIRED','REVIEW_PENDING','RESOLVED_SERVER_SIDE','UNKNOWN'])for(const lang of ['en','fr']){
  const x=duplicateStatus(state,lang);assert.equal(x.canSubmit,false);assert.ok(x.status.length>3&&x.instruction.length>5);
  assert.ok(!JSON.stringify(x).includes('candidate_id'));}
 assert.match(duplicateStatus('UNKNOWN','fr').instruction,/commande/i);
});
test('P02-B0038 context switch, logout and session epoch invalidate volatile PHI draft',()=>{
 const store=new VolatileRegistrationSession(),fence=new RegistrationContextFence(store);
 const s={tenant:'t',facility:'f',actor:'a',sessionEpoch:1};
 assert.equal(fence.enter(s,'en'),true);assert.equal(store.update(s,{name:'Synthetic Test'}),true);
 assert.equal(fence.enter({...s},'en'),true);assert.equal(store.snapshot(s).name,'Synthetic Test');
 const switched={...s,facility:'f2'};assert.equal(fence.enter(switched,'fr'),true);
 assert.equal(store.snapshot(switched).name,'');assert.equal(store.snapshot(s),null);
 assert.equal(fence.enter(null,'en'),false);assert.equal(store.snapshot(switched),null);
});
test('P02-B0039 mobile action never becomes active based on client conditions',()=>{
 for(const lang of ['en','fr'])for(const online of [true,false])for(const verified of [true,false]){
  const x=registrationActionPresentation(lang,online,verified);
  assert.equal(x.disabled,true);assert.equal(x.minTouchTargetPx,48);assert.equal(x.visibleText,true);
  assert.equal(x.screenId,'M01-S02');assert.ok(x.reason.length>5);
 }
});
