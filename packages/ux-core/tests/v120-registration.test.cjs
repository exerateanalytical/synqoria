const test=require('node:test');const assert=require('node:assert/strict');
const {validateRegistrationDraft,gateRegistration,safeRegistrationStatus,safeRegistrationTelemetry,canStoreIntakeOffline}=require('../dist/index.js');
const now='2026-10-30';
const base={name:'',birthInformation:'unknown',dateOfBirth:'',estimatedAge:'',language:'en',duplicateReviewCleared:true};
const access={verified:true,online:true,facilitySelected:true,shiftCurrent:true,canRegister:true,serverConnected:true,clinicalReviewAvailable:true};
test('P02-B0027 unknown birth information and optional name are valid without fabricating demographics',()=>{
 assert.deepEqual(validateRegistrationDraft(base,now),[]);
 assert.deepEqual(validateRegistrationDraft({...base,name:'X'.repeat(201)},now),['name_too_long']);
 assert.deepEqual(validateRegistrationDraft({...base,name:'A\nB'},now),['name_too_long']);
 assert.deepEqual(validateRegistrationDraft({...base,duplicateReviewCleared:false},now),['review_required']);
});
test('P02-B0027 requires actual dates and prevents future birth dates and contradictory input',()=>{
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'known',dateOfBirth:'2026-02-30'},now),['invalid_date']);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'known',dateOfBirth:'2026-11-01'},now),['future_birth']);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'known',dateOfBirth:'2003-01-05'},now),[]);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'known',dateOfBirth:'2003-01-05',estimatedAge:'20'},now),['conflicting_birth_fields']);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'estimated',estimatedAge:'150'},now),['invalid_estimated_age']);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'estimated',estimatedAge:'0'},now),[]);
 assert.deepEqual(validateRegistrationDraft({...base,birthInformation:'estimated',estimatedAge:'2',dateOfBirth:'2005-01-01'},now),['conflicting_birth_fields']);
});
test('P02-B0028 registration submission gate denies unverified access, offline mode and service outages',()=>{
 assert.equal(gateRegistration(base,{...access,verified:false},now),'ACCESS_DENIED');
 assert.equal(gateRegistration(base,{...access,facilitySelected:false},now),'ACCESS_DENIED');
 assert.equal(gateRegistration(base,{...access,shiftCurrent:false},now),'ACCESS_DENIED');
 assert.equal(gateRegistration(base,{...access,canRegister:false},now),'ACCESS_DENIED');
 assert.equal(gateRegistration(base,{...access,online:false},now),'OFFLINE_NO_WRITE');
 assert.equal(gateRegistration(base,{...access,serverConnected:false},now),'INTEGRATION_UNAVAILABLE');
 assert.equal(gateRegistration(base,{...access,clinicalReviewAvailable:false},now),'INTEGRATION_UNAVAILABLE');
 assert.equal(gateRegistration({...base,duplicateReviewCleared:false},access,now),'INPUT_REVIEW_REQUIRED');
 assert.equal(gateRegistration(base,access,now),'READY_FOR_AUTHORIZED_TRANSPORT');
 assert.match(safeRegistrationStatus('fr','OFFLINE_NO_WRITE'),/hors ligne/);
});
test('P02-B0029 telemetry never reveals names, birthdate, identifiers or correlation IDs',()=>{
 const payload=safeRegistrationTelemetry({state:'review',patientName:'SECRET',birthDate:'2004-11-20',identifier:'HIDDEN',correlationId:'NO'});
 assert.deepEqual(payload,{state:'review'});assert.equal(Object.isFrozen(payload),true);
 assert.deepEqual(safeRegistrationTelemetry({state:'sensitive',patientName:'SECRET'}),{state:'unknown'});
 assert.equal(canStoreIntakeOffline(),false);
});
