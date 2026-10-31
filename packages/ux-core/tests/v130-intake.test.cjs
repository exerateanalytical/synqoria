const test=require('node:test');const assert=require('node:assert/strict');
const {VolatileRegistrationSession,blankRegistrationDraft,registrationErrorCopy,registrationIssueSummary,staffIntakeModel}=require('../dist/index.js');
const scope={tenant:'t1',facility:'f1',actor:'a1',sessionEpoch:2};
const access={verified:true,online:true,facilitySelected:true,shiftCurrent:true,canRegister:true,serverConnected:true,clinicalReviewAvailable:true};
test('P02-B0030 volatile draft clears on facility, actor and epoch switches; cannot accept review flag',()=>{
 const s=new VolatileRegistrationSession();s.begin(scope,'fr');assert.equal(s.update(scope,{name:'Synthetic Name',duplicateReviewCleared:true}),true);
 assert.equal(s.snapshot(scope).name,'Synthetic Name');assert.equal(s.snapshot(scope).duplicateReviewCleared,false);
 assert.equal(s.snapshot({...scope,facility:'f2'}),null);assert.equal(s.snapshot(scope),null);
 s.begin(scope,'en');s.update(scope,{name:'Synthetic Name'});assert.equal(s.update({...scope,sessionEpoch:3},{name:'another'}),false);
 assert.equal(s.snapshot(scope),null);assert.deepEqual(blankRegistrationDraft('fr'),{name:'',birthInformation:'unknown',dateOfBirth:'',estimatedAge:'',language:'fr',duplicateReviewCleared:false});
 assert.throws(()=>s.begin({...scope,sessionEpoch:-1},'en'));
});
test('P02-B0031 bilingual issue summary deduplicates and never includes submitted PHI',()=>{
 const summary=registrationIssueSummary(['review_required','invalid_date','review_required'],'fr');
 assert.equal(summary.messages.length,2);assert.match(summary.heading,/2/);assert.match(summary.messages[0],/vérification/);
 assert.equal(registrationErrorCopy('future_birth','en'),'Date of birth cannot be in the future.');
 assert.doesNotMatch(JSON.stringify(summary),/Synthetic Name|2001-02-03/);
});
test('P02-B0032 native staff model has four tabs and never enables local registration submission',()=>{
 const draft={...blankRegistrationDraft('en'),duplicateReviewCleared:true};
 const result=staffIntakeModel('fr',draft,access,'2026-10-30');
 assert.equal(result.screen,'M01-S02');assert.deepEqual(result.tabs,['work','patients','tasks','more']);
 assert.deepEqual(result.labels,['Travail','Patients','Tâches','Plus']);assert.equal(result.submitEnabled,false);assert.equal(result.readOnly,true);
 assert.equal(result.gate,'READY_FOR_AUTHORIZED_TRANSPORT');
 assert.equal(staffIntakeModel('en',draft,{...access,verified:false},'2026-10-30').gate,'ACCESS_DENIED');
});
