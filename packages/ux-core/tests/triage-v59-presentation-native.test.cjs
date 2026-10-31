const {test}=require('node:test');const assert=require('node:assert/strict');
const {triageWorkspaceModel,TriageNativePrivacySession,triageReceiptEvidence,TriageTransportError}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const row=(flags={})=>({caseId:U(1),stage:'WAITING',revision:4,station:null,assignee:null,arrivedAt:'2026-10-31T00:00:00Z',paused:false,safetyHold:false,manualEscalation:false,...flags});
test('B0383: workspace returns no protected fields or actions before session verification',()=>{
 const v=triageWorkspaceModel(row(),false,['triage:queue']);
 assert.equal(v.status,'locked');assert.deepEqual(v.actions,[]);assert.equal(v.revision,null);
 assert.deepEqual(Object.keys(v),['status','title','stageLabel','actions','alert','revision']);
});
test('B0383: bilingual status labels and authorized action groups are capability-bound',()=>{
 const en=triageWorkspaceModel(row(),true,['triage:queue']);const fr=triageWorkspaceModel(row(),true,['triage:queue'],'fr');
 assert.equal(en.stageLabel,'Waiting');assert.equal(fr.stageLabel,'En attente');
 assert.ok(en.actions.some(a=>a.action==='QUEUE_CALL'));assert.equal(en.actions.some(a=>a.action==='ASSESSMENT_ATTEST'),false);
});
test('B0383: active safety hold and pause hide contradictory controls',()=>{
 const hold=triageWorkspaceModel(row({safetyHold:true}),true,['triage:manage','triage:queue']);
 assert.equal(hold.status,'on-hold');assert.equal(hold.actions.some(x=>x.action==='SAFETY_HOLD'),false);
 assert.equal(hold.actions.some(x=>x.action==='SAFETY_HOLD_RELEASE'),true);
 const paused=triageWorkspaceModel(row({paused:true}),true,['triage:queue']);
 assert.equal(paused.actions.some(x=>x.action==='QUEUE_PAUSE'),false);
 assert.equal(paused.actions.some(x=>x.action==='QUEUE_RESUME'),true);
});
test('B0383: closed cases never show routine queue actions',()=>{
 const v=triageWorkspaceModel(row({stage:'CLOSED'}),true,['triage:queue','triage:sign']);
 assert.equal(v.status,'closed');assert.deepEqual(v.actions.map(a=>a.action),['ASSESSMENT_ADDENDUM']);
});
test('B0384: native session remains locked when app foregrounds, until verified lease is supplied',()=>{
 let state={mode:'locked',selectedCase:null};const activity=[];
 const workflow={snapshot:()=>state};const tasks={clear:()=>{activity.push('clear');state={mode:'locked',selectedCase:null};},bind:()=>{state={mode:'case',selectedCase:row()};activity.push('bind');}};
 const native=new TriageNativePrivacySession(workflow,tasks);
 native.onForeground();assert.equal(native.snapshot().visible,false);native.unlock({scope:{},grants:[],issuedAtMs:1,expiresAtMs:2});
 assert.equal(native.snapshot().visible,true);assert.equal(native.snapshot().hasClinicalData,true);
 native.onBackground();assert.equal(native.snapshot().visible,false);assert.equal(native.snapshot().hasClinicalData,false);
 assert.deepEqual(activity,['clear','bind','clear']);
});
test('B0384: session forbids clinical unlock while device backgrounded',()=>{
 const native=new TriageNativePrivacySession({snapshot:()=>({mode:'locked',selectedCase:null})},{clear:()=>{},bind:()=>{throw Error('unexpected');}});
 assert.throws(()=>native.unlock({}));native.dispose();assert.equal(native.snapshot().visible,false);
});
test('B0384: facility switch and logout hide case and require fresh verification',()=>{
 let state={mode:'case',selectedCase:row()};const tasks={clear:()=>{state={mode:'locked',selectedCase:null};},bind:()=>{state={mode:'case',selectedCase:row()};}};
 const native=new TriageNativePrivacySession({snapshot:()=>state},tasks);native.onForeground();native.unlock({});
 native.onShiftOrFacilityChange();assert.equal(native.snapshot().visible,false);
 native.onForeground();native.unlock({});native.onLogout();assert.equal(native.snapshot().hasClinicalData,false);
});
test('B0385: audit receipt is proven against matching revision, digest and operation',()=>{
 const receipt={revision:2,eventDigest:'b'.repeat(64),replay:false};const history=[{revision:2,operation:'QUEUE_CALL',digest:'b'.repeat(64),occurredAt:'2026-10-31T00:00:00Z'}];
 const v=triageReceiptEvidence(receipt,'QUEUE_CALL',history);
 assert.deepEqual(v,{revision:2,digest:'b'.repeat(64),operation:'QUEUE_CALL'});
 assert.equal(JSON.stringify(v).includes('patient'),false);
});
test('B0385: forged digest and duplicate audit revision cannot confirm a clinical write',()=>{
 const receipt={revision:2,eventDigest:'b'.repeat(64),replay:false};const event={revision:2,operation:'QUEUE_CALL',digest:'b'.repeat(64),occurredAt:'2026-10-31T00:00:00Z'};
 assert.throws(()=>triageReceiptEvidence(receipt,'QUEUE_CALL',[{...event,digest:'a'.repeat(64)}]));
 assert.throws(()=>triageReceiptEvidence(receipt,'QUEUE_CALL',[event,event]));
 assert.throws(()=>triageReceiptEvidence(receipt,'QUEUE_RECALL',[event]));
});
