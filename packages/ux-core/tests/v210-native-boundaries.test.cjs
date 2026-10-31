const test=require('node:test'),assert=require('node:assert/strict');
const {lockedNativeRoute,nativeReturnAllowed,nativeLockedAnnouncement,NativeForegroundEpoch,nativeOfflineAction}=require('../dist');
test('P02-B0063: unverified navigation denies deep links and even valid roots',()=>{
 assert.deepEqual(lockedNativeRoute({shell:'staff',root:'patients'}),{allowed:false,reason:'UNVERIFIED_SESSION'});
 assert.equal(lockedNativeRoute({shell:'patient',root:'patients'}).reason,'INVALID_ROUTE');
 assert.equal(lockedNativeRoute({shell:'staff',root:'patients',detail:'patient-id'}).reason,'INVALID_ROUTE');
 assert.equal(lockedNativeRoute(null).allowed,false);
});
test('P02-B0064: return tickets expire and bind scope epoch and shell',()=>{
 const t={shell:'staff',scopeEpoch:2,issuedAt:1000,expiresAt:5000};
 assert.equal(nativeReturnAllowed(t,'staff',2,1001),true);
 for(const [shell,epoch,now] of [['patient',2,2000],['staff',3,2000],['staff',2,5000],['staff',2,999]])assert.equal(nativeReturnAllowed(t,shell,epoch,now),false);
 assert.equal(nativeReturnAllowed({...t,expiresAt:100000},'staff',2,2000),false);
});
test('P02-B0065: native status is bilingual and screen-reader accessible',()=>{
 for(const kind of ['staff','patient'])for(const locale of ['en','fr']){
  const v=nativeLockedAnnouncement(kind,locale);assert.equal(v.accessibilityRole,'alert');assert.equal(v.allowFontScaling,true);assert.equal(v.maxFontSizeMultiplier,3);assert.ok(v.text.length>30);assert.ok(!v.text.includes('12345'));
 }
});
test('P02-B0066: background invalidates previous authorization epoch',()=>{
 const e=new NativeForegroundEpoch();const first=e.resume();assert.equal(e.accepts(first,true),true);
 e.background();assert.equal(e.accepts(first,true),false);const second=e.resume();assert.equal(e.accepts(first,true),false);assert.equal(e.accepts(second,false),false);assert.equal(e.accepts(second,true),true);
});
test('P02-B0067: offline and online clinical writes are release-locked',()=>{
 for(const action of ['patient_register','prescribe','administer_medication','sign_note','review_approve']){
  assert.equal(nativeOfflineAction(action,false,false).allowed,false);assert.equal(nativeOfflineAction(action,true,true).allowed,false);
 }
 assert.equal(nativeOfflineAction('view_shell',false,false).allowed,true);assert.equal(nativeOfflineAction('view_cached_nonclinical',false,false).allowed,false);
});
