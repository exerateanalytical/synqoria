const test=require('node:test'),assert=require('node:assert/strict');
const {nativeTextSafety,nativeBottomDestinationCount,MobilePrivacyLatch,mobileScopeDisclosure}=require('../dist');
test('P02-B0059 dynamic type critical fields wrap at 200–300% without ellipsis',()=>{
 for(const kind of ['body','heading','critical','label']){const v=nativeTextSafety(kind);assert.equal(v.allowFontScaling,true);assert.equal(v.maxFontSizeMultiplier,3);assert.equal(v.numberOfLines,undefined);assert.equal(v.style.flexWrap,'wrap');assert.equal(v.style.flexShrink,1);}
 assert.equal(nativeTextSafety('critical').minTouchTarget,48);
 assert.equal(nativeBottomDestinationCount([{id:'work'},{id:'patients'},{id:'tasks'},{id:'more'}]),true);
 assert.equal(nativeBottomDestinationCount([{id:'work'},{id:'more'},{id:'more'}]),false);
 assert.equal(nativeBottomDestinationCount([{id:'work'},{id:'patients'},{id:'tasks'},{id:'more'},{id:'extra'}]),false);
});
test('P02-B0060 app background revokes private presentation and requires new grant',()=>{
 const p=new MobilePrivacyLatch();assert.equal(p.mayDisplayPrivateData,false);
 const epoch=p.generation;assert.equal(p.confirm(epoch,true,true),true);assert.equal(p.mayDisplayPrivateData,true);
 const next=p.hide();assert.equal(p.mayDisplayPrivateData,false);assert.equal(p.confirm(next,true,true),false);
 p.foreground();assert.equal(p.confirm(epoch,true,true),false);assert.equal(p.confirm(next,false,true),false);
 assert.equal(p.confirm(next,true,true),true);p.revoke();assert.equal(p.mayDisplayPrivateData,false);
});
test('P02-B0062 verified context cannot leak IDs and fails closed for offline, expiry, missing shift or proxy',()=>{
 const grant={actorId:'staff_123456',facilityId:'facility_123456',role:'nurse',shiftId:'shift_123456',expiresAt:200,serverVerified:true};
 for(const locale of ['en','fr']){
  assert.equal(mobileScopeDisclosure('staff',locale,null,100,true).verified,false);
  assert.equal(mobileScopeDisclosure('staff',locale,grant,100,false).verified,false);
  assert.equal(mobileScopeDisclosure('staff',locale,grant,200,true).verified,false);
  assert.equal(mobileScopeDisclosure('staff',locale,{...grant,shiftId:''},100,true).verified,false);
  const v=mobileScopeDisclosure('staff',locale,grant,100,true);assert.equal(v.verified,true);assert.equal(v.label.includes(grant.facilityId),false);
  assert.equal(mobileScopeDisclosure('patient',locale,{...grant,proxyPatientId:'bad'},100,true).verified,false);
 }
});
