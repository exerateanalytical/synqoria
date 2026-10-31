const test=require('node:test'),assert=require('node:assert/strict');
const {NativePrivacyCurtain,nativeAccessStatus,nativeStaffScope,nativeProxyGrantStatus,nativePrivacyAnnouncement}=require('../dist');
test('P02-B0068: background covers private display and stale re-entry cannot unlock',()=>{
 const c=new NativePrivacyCurtain();assert.equal(c.isCovered,true);
 const initial=c.epoch;assert.equal(c.confirm(initial,true),false);
 c.resume();assert.equal(c.confirm(initial,true),true);assert.equal(c.isCovered,false);
 c.background();assert.equal(c.isCovered,true);c.resume();assert.equal(c.confirm(initial,true),false);
 assert.equal(c.confirm(c.epoch,false),false);assert.equal(c.confirm(c.epoch,true),true);
 c.background();assert.equal(c.isCovered,true);
});
test('P02-B0069: bilingual safe access statuses never expose clinical identifiers',()=>{
 for(const code of ['NO_SESSION','SESSION_EXPIRED','FACILITY_REQUIRED','PROXY_REVOKED','OFFLINE']){
  const en=nativeAccessStatus('en',code),fr=nativeAccessStatus('fr',code);
  assert.equal(en.code,code);assert.equal(fr.code,code);assert.notEqual(en.text,fr.text);
  assert.equal(en.accessibilityRole,'alert');assert.equal(fr.allowFontScaling,true);
  assert.ok(!en.text.includes('patient_id')&&!fr.text.includes('patient_id'));
 }
 assert.throws(()=>nativeAccessStatus('en','INVALID'));
});
test('P02-B0070: staff facility/role/shift scope requires verified current lease and safe labels',()=>{
 const v={facilityName:'Douala General',roleName:'Nurse',shiftName:'Morning',verifiedAt:1000,expiresAt:2000};
 assert.equal(nativeStaffScope('en',v,1500,true).verified,true);
 assert.equal(nativeStaffScope('en',v,1500,false).verified,false);
 assert.equal(nativeStaffScope('fr',v,2000,true).verified,false);
 assert.equal(nativeStaffScope('en',v,999,true).verified,false);
 assert.equal(nativeStaffScope('en',{...v,facilityName:'<script>alert(1)</script>'},1500,true).verified,false);
 assert.equal(nativeStaffScope('en',{...v,expiresAt:1000+13*3600000},1500,true).verified,false);
 assert.equal(nativeStaffScope('fr',null,1500,true).label,'Établissement et service non vérifiés');
});
test('P02-B0071: proxy access refuses wrong subject, wrong proxy, revoked and stale epoch',()=>{
 const grant={subjectId:'synthetic-subject',proxyId:'synthetic-proxy',verifiedAt:1000,expiresAt:2000,revoked:false,epoch:3};
 const check=(g,subject='synthetic-subject',proxy='synthetic-proxy',epoch=3,now=1500,verified=true)=>nativeProxyGrantStatus(g,subject,proxy,epoch,now,verified);
 assert.equal(check(grant).allowed,true);
 assert.equal(check(grant,'other').allowed,false);
 assert.equal(check(grant,'synthetic-subject','other').allowed,false);
 assert.equal(check({...grant,revoked:true}).allowed,false);
 assert.equal(check(grant,'synthetic-subject','synthetic-proxy',4).allowed,false);
 assert.equal(check(grant,'synthetic-subject','synthetic-proxy',3,2000).allowed,false);
 assert.equal(check(grant,'synthetic-subject','synthetic-proxy',3,1500,false).allowed,false);
 assert.equal(check(null).reason,'ACCESS_UNAVAILABLE');
});
test('P02-B0072: privacy announcement is accessible and does not reveal identifiers',()=>{
 for(const locale of ['en','fr'])for(const covered of [true,false]){
  const a=nativePrivacyAnnouncement(locale,covered,'NO_SESSION');
  assert.equal(a.accessibilityRole,'alert');assert.equal(a.accessibilityLiveRegion,'polite');
  assert.equal(a.allowFontScaling,true);assert.equal(a.maxFontSizeMultiplier,3);
  assert.ok(a.text.length>20);assert.ok(!a.text.includes('synthetic-subject'));
 }
 assert.match(nativePrivacyAnnouncement('fr',true,'OFFLINE').text,/masqué/);
 assert.match(nativePrivacyAnnouncement('en',true,'OFFLINE').text,/hidden/);
});
