const test=require('node:test');const assert=require('node:assert/strict');
const {privateReturnRoute,PatientProxyContext,MobileVisibilityFence,accessibleLockedStatus}=require('../dist/index.js');
test('P02-B0044 strict private navigation rejects patient ids, open redirects, mixed shell and encoded paths',()=>{
 assert.equal(privateReturnRoute('/workforce/patients','workforce'),'/workforce/patients');
 assert.equal(privateReturnRoute('/portal','portal'),'/portal');
 assert.equal(privateReturnRoute('/platform','platform'),'/platform');
 for(const path of ['https://evil.example','//evil','/workforce/patients?patient=1','/workforce/patients/%2e%2e','/workforce/patients#123','/workforce/patients/123','/workforce/../platform','/workforce\\patients'])assert.equal(privateReturnRoute(path,'workforce'),null,path);
 assert.equal(privateReturnRoute('/portal','workforce'),null);
 assert.equal(privateReturnRoute(null,'workforce'),null);
});
test('P02-B0045 proxy context expires, revokes and clears on subject change and background',()=>{
 const c=new PatientProxyContext();const now=1000000;
 const r={tenant:'tenant_12345',subject:'subject_12345',proxy:'proxy_12345',patient:'patient_12345',grantRevision:2,expiresAt:now+60000,serverVerified:true};
 assert.equal(c.activate(r,now),true);assert.equal(c.snapshot(now).kind,'active');
 assert.equal(c.snapshot(now+60000).kind,'locked');
 assert.equal(c.activate({...r,serverVerified:false},now),false);
 assert.equal(c.activate({...r,patient:'proxy_12345'},now),false);
 assert.equal(c.activate({...r,grantRevision:0},now),false);
 assert.equal(c.activate(r,now),true);c.background();assert.equal(c.snapshot(now).kind,'locked');
 assert.equal(c.activate(r,now),true);c.switchSubject();assert.equal(c.snapshot(now).kind,'locked');
 assert.equal(c.activate(r,now),true);c.revoke();assert.equal(c.snapshot(now).kind,'locked');
});
test('P02-B0046 mobile foreground cannot reuse a stale protected-view generation',()=>{
 const f=new MobileVisibilityFence();assert.equal(f.visible,false);
 assert.equal(f.showAfterVerifiedForeground({verified:true,online:true,serverSessionCurrent:true,epoch:0}),true);
 const old=f.generation;f.hide();assert.equal(f.visible,false);
 assert.equal(f.showAfterVerifiedForeground({verified:true,online:true,serverSessionCurrent:true,epoch:old}),false);
 assert.equal(f.showAfterVerifiedForeground({verified:true,online:false,serverSessionCurrent:true,epoch:f.generation}),false);
 assert.equal(f.showAfterVerifiedForeground({verified:true,online:true,serverSessionCurrent:false,epoch:f.generation}),false);
 assert.equal(f.showAfterVerifiedForeground({verified:true,online:true,serverSessionCurrent:true,epoch:f.generation}),true);
});
test('P02-B0047 bilingual accessible release status is semantic, nonidentifying and never authorizes submit',()=>{
 for(const locale of ['en','fr'])for(const reason of ['AUTH_REQUIRED','FACILITY_REQUIRED','OFFLINE','REVIEW_REQUIRED','CLINICAL_RELEASE_LOCKED']){
  const s=accessibleLockedStatus(locale,reason);
  assert.equal(s.canSubmit,false);assert.equal(s.containsPatientData,false);assert.equal(s.role,'alert');assert.equal(s.live,'assertive');assert.ok(s.message.length>15);assert.equal(s.minimumTouchTarget,48);
 }
 assert.notEqual(accessibleLockedStatus('en','OFFLINE').message,accessibleLockedStatus('fr','OFFLINE').message);
});
