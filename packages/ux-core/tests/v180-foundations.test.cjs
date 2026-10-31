const test=require('node:test');const assert=require('node:assert/strict');
const {safeStaffRouteIntent,safeStaffReturnPath,ForegroundReauthorization,privacyNotification,lockedRegistrationJourney,lockedNavigationAccessibility}=require('../dist');
test('P02-B0048 staff navigation allows only static roots and never patient identifiers',()=>{
 for(const key of ['work','patients','tasks','more'])assert.equal(safeStaffRouteIntent(key,false).kind,'navigate');
 for(const key of ['patients/123','/workforce/patients?patient=123','https://evil.example','__proto__','constructor',null,{},'review'])assert.equal(safeStaffRouteIntent(key,false).kind,'locked');
 assert.equal(safeStaffRouteIntent('patients',true).kind,'locked');
 assert.equal(safeStaffReturnPath('/workforce/patients'),'/workforce/patients');
 for(const key of ['/workforce/patients/123','/workforce/patients?patient=123','//evil','/workforce/%2e%2e','/workforce#token'])assert.equal(safeStaffReturnPath(key),null);
});
test('P02-B0049 foreground reauthorization denies stale, offline, revoked and wrong generation',()=>{
 const g=new ForegroundReauthorization();assert.equal(g.canDisplayProtectedData,false);
 assert.equal(g.resume({generation:0,online:true,serverVerified:true,grantFresh:true}),true);
 assert.equal(g.canDisplayProtectedData,true);g.background();assert.equal(g.canDisplayProtectedData,false);
 assert.equal(g.resume({generation:0,online:true,serverVerified:true,grantFresh:true}),false);
 assert.equal(g.resume({generation:1,online:false,serverVerified:true,grantFresh:true}),false);
 assert.equal(g.resume({generation:1,online:true,serverVerified:false,grantFresh:true}),false);
 assert.equal(g.resume({generation:1,online:true,serverVerified:true,grantFresh:false}),false);
 assert.equal(g.resume({generation:1,online:true,serverVerified:true,grantFresh:true}),true);
 g.revoke();assert.equal(g.canDisplayProtectedData,false);
});
test('P02-B0050 device notifications never contain patient or appointment data',()=>{
 for(const locale of ['en','fr'])for(const kind of ['new-task','appointment-update','access-expired','sync-review']){
  const n=privacyNotification(kind,locale);assert.equal(n.title,'SYNQORIA');assert.deepEqual(n.data,{});assert.equal(n.containsClinicalData,false);assert.ok(n.body.length>15);
  assert.equal(/patient name|diagnosis|medical record|nom du patient/i.test(n.body),false);
 }
 assert.equal(privacyNotification('patient_123','en'),null);assert.equal(privacyNotification('__proto__','en'),null);
 assert.notEqual(privacyNotification('new-task','en').body,privacyNotification('new-task','fr').body);
});
test('P02-B0051 registration journey is readonly and bilingual at every step',()=>{
 const en=lockedRegistrationJourney('en'),fr=lockedRegistrationJourney('fr');
 assert.deepEqual(en.map(s=>s.id),['access','facility','duplicate-review','registration']);
 assert.deepEqual(en.map(s=>s.id),fr.map(s=>s.id));
 for(const s of [...en,...fr]){assert.equal(s.canSubmit,false);assert.equal(s.canOpen,false);assert.equal(s.containsPatientData,false);assert.equal(s.status,'blocked');assert.ok(s.label.length>8);}
 assert.notEqual(en[0].label,fr[0].label);
});
test('P02-B0052 both mobile apps have four distinct disabled accessible bottom tabs',()=>{
 for(const app of ['staff','patient'])for(const locale of ['en','fr']){
  const tabs=lockedNavigationAccessibility(app,locale);assert.equal(tabs.length,4);assert.equal(new Set(tabs.map(x=>x.accessibilityLabel)).size,4);
  assert.ok(tabs.every(t=>t.disabled&&!t.selected&&t.minTouchTarget>=48&&t.accessibilityHint.length>10));
 }
 assert.equal(lockedNavigationAccessibility('staff','en')[3].accessibilityLabel,'More');
 assert.equal(lockedNavigationAccessibility('patient','fr')[3].accessibilityLabel,'Plus');
});
