const test=require('node:test');const assert=require('node:assert/strict');
const {registrationFieldStructure,intakeNavigation,registrationSubmissionLocked,mobileRegistrationAccessibility}=require('../dist/index.js');
test('P02-B0033 registration fields are bilingual, unique, and avoid invented identifying values',()=>{
 const en=registrationFieldStructure('en'),fr=registrationFieldStructure('fr');
 assert.equal(en.length,5);assert.equal(fr.length,5);
 assert.equal(new Set(en.map(f=>f.id)).size,en.length);
 assert.deepEqual(en.map(f=>f.key),fr.map(f=>f.key));
 assert.ok(en.every(f=>f.hint.length>5&&f.label.length>5));
 assert.ok(fr.some(f=>/naissance/i.test(f.label)));
 assert.ok(en.some(f=>f.key==='legalName'&&f.required===false));
 assert.ok(en.some(f=>f.key==='birthInformation'&&f.required===true));
 assert.ok(!JSON.stringify(en).includes('patientId'));
});
test('P02-B0034 staff patient registration remains subroute and denies untrusted workspace navigation',()=>{
 const grants=new Set(['patient:register']);
 const ready=intakeNavigation('workforce',grants,true);
 assert.equal(ready.canonicalScreen,'M01-S02');assert.equal(ready.activeSidebar,'patients');
 assert.equal(ready.url,'/workforce/patients/new');assert.ok(ready.canEnter);
 for(const shell of ['portal','platform'])assert.equal(intakeNavigation(shell,grants,true).canEnter,false);
 assert.equal(intakeNavigation('workforce',new Set(),true).canEnter,false);
 assert.equal(intakeNavigation('workforce',grants,false).canEnter,false);
});
test('P02-B0035 registration cannot be submitted on any local-only ready state',()=>{
 const state={authenticated:true,online:true,facilityScoped:true,reviewCurrent:true,serverReleaseVerified:true,clinicalSignoff:true};
 assert.deepEqual(registrationSubmissionLocked(state),{locked:true,reason:'CLINICAL_RELEASE_NOT_APPROVED'});
 for(const key of Object.keys(state)){
  assert.deepEqual(registrationSubmissionLocked({...state,[key]:false}),{locked:true,reason:key==='clinicalSignoff'?'CLINICAL_RELEASE_NOT_APPROVED':'ACCESS_NOT_VERIFIED'});
 }
});
test('P02-B0036 native accessibility copy maintains locked action and 48px targets',()=>{
 const en=mobileRegistrationAccessibility('en'),fr=mobileRegistrationAccessibility('fr');
 assert.equal(en.screen,'M01-S02');assert.equal(fr.screen,'M01-S02');
 assert.equal(en.actionDisabled,true);assert.equal(en.minimumTarget,48);
 assert.match(fr.alert,/vérifi/i);assert.match(en.actionLabel,/unavailable/i);
 assert.ok(!JSON.stringify(en).includes('patientId'));
});
