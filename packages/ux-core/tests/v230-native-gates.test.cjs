const {test}=require('node:test');const assert=require('node:assert/strict');
const {nativeDeepLinkGate,NativeScopeTransition,NativeResponseEpoch,nativeCriticalField,NativeOfflineWriteQueue}=require('../dist');
test('P02-B0073: native deep link gate rejects encoded traversal, query, unauthorized root and locked session',()=>{
 for(const path of ['/work/%2e%2e','/work?patient=1','//work','/work/../more','/patients/123','/work%252Fmore','/work\\x','/work#x'])
  assert.equal(nativeDeepLinkGate(path,'staff',true).allowed,false,path);
 assert.deepEqual(nativeDeepLinkGate('/work','staff',false),{allowed:false,reason:'LOCKED'});
 assert.deepEqual(nativeDeepLinkGate('/work','staff',true),{allowed:true,route:'/work'});
 assert.equal(nativeDeepLinkGate('/work','patient',true).allowed,false);
 assert.equal(nativeDeepLinkGate('/appointments','patient',true).allowed,true);
});
test('P02-B0074: facility/role/shift transition wipes staged sensitive state and invalidates epochs',()=>{
 const t=new NativeScopeTransition();const a=t.switchTo('actor','facilityA','nurse','shiftA');
 t.stage('draft-intake','synthetic-only');assert.equal(t.read('draft-intake',a),'synthetic-only');
 assert.equal(t.switchTo('actor','facilityA','nurse','shiftA'),a);
 const b=t.switchTo('actor','facilityB','nurse','shiftA');assert.ok(b>a);assert.equal(t.draftCount,0);
 assert.equal(t.read('draft-intake',a),null);t.stage('draft-intake','other');t.suspend();assert.equal(t.draftCount,0);
 assert.equal(t.read('draft-intake',b),null);assert.throws(()=>t.stage('draft-intake','x'));
});
test('P02-B0075: late native async response is aborted and discarded on scope switch',()=>{
 const t=new NativeResponseEpoch();const a=t.start('facility:patientA');assert.equal(t.accept(a.version,'facility:patientA',true),true);
 assert.equal(t.accept(a.version,'facility:patientA',false),false);
 const b=t.start('facility:patientB');assert.equal(a.signal.aborted,true);
 assert.equal(t.accept(a.version,'facility:patientA',true),false);
 assert.equal(t.accept(b.version,'facility:patientB',true),true);t.invalidate();assert.equal(b.signal.aborted,true);
 assert.equal(t.activeRequests,0);assert.equal(t.accept(b.version,'facility:patientB',true),false);
});
test('P02-B0076: critical clinical field policy never clips dose, medication or allergy labels',()=>{
 for(const kind of ['drug','dose','allergy'])for(const locale of ['en','fr']){
  const p=nativeCriticalField(kind,locale);assert.equal(p.allowFontScaling,true);
  assert.equal(p.maxFontSizeMultiplier,3);assert.equal(p.numberOfLines,undefined);
  assert.equal(p.ellipsizeMode,undefined);assert.equal(p.style.flexWrap,'wrap');assert.ok(p.style.minHeight>=48);
 }
 assert.notEqual(nativeCriticalField('dose','en').label,nativeCriticalField('dose','fr').label);
 assert.throws(()=>nativeCriticalField('invalid','en'));
});
test('P02-B0077: offline queue always rejects clinical writes and cannot replay',()=>{
 const q=new NativeOfflineWriteQueue();for(const intent of ['registration','medication','allergy','triage','encounter','appointment']){
  assert.equal(q.request(intent,false,false).queued,false);assert.equal(q.request(intent,true,true).queued,false);
 }
 assert.equal(q.pendingWrites,0);assert.equal(q.replay().sent,0);
 assert.equal(q.request('read_only_shell',false,false).reason,'SHELL_ONLY_NO_CLINICAL_DATA');
 assert.throws(()=>q.request('unsupported',true,true));
});
