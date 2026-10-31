const {test}=require('node:test');
const assert=require('node:assert/strict');
const {TriageStaffWorkflow,TriageTransportError}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const TENANT=U(1),FACILITY=U(2),ACTOR=U(3),SHIFT=U(4),P=U(5),P2=U(6),C=U(7),C2=U(8),COMMAND=U(9);
const scope={tenantId:TENANT,facilityId:FACILITY,actorId:ACTOR,shiftId:SHIFT,epoch:'verified-identity-1'};
const row=(caseId=C,revision=1)=>({caseId,stage:'WAITING',revision,station:null,assignee:null,arrivedAt:'2026-10-31T08:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
function fixture(handler){
 const calls=[];
 const transport={token:async()=> 'synthetic_session_verification_123456',isOnline:()=>true,fetch:async(url,options)=>{
  calls.push({url,options});const data=await handler(url,options);
  return {status:data?.status??200,headers:{get:k=>k==='Cache-Control'?'no-store, private':k==='Content-Type'?'application/json':null},json:async()=>data?.body??data};
 }};
 return {w:new TriageStaffWorkflow('https://cell.example/',transport),calls};
}
test('B0369: staff scope is required and invalid trusted scope never unlocks',async()=>{
 const {w}=fixture(()=>({cases:[row()]}));
 assert.equal(w.snapshot().mode,'locked');
 await assert.rejects(w.load(P,'FACILITY_QUEUE',[C]),e=>e.category==='reauthenticate');
 assert.throws(()=>w.bind({...scope,facilityId:'from-url'}),e=>e.category==='unavailable');
 assert.equal(w.snapshot().context,null);
 w.bind(scope);assert.equal(w.snapshot().mode,'worklist');
 w.dispose();assert.equal(w.snapshot().mode,'locked');
});
test('B0369: patient-scoped worklist is the only path to selecting a case',async()=>{
 const {w}=fixture((url)=>url.includes('/views/')?{cases:[row()]}:{case:row()});
 w.bind(scope);
 await assert.rejects(w.select(C));
 await w.load(P,'FACILITY_QUEUE',[C]);
 assert.equal(w.snapshot().worklist.length,1);
 await assert.rejects(w.select(C2),e=>e.category==='denied');
 assert.equal((await w.select(C)).selectedCase.caseId,C);
 assert.equal(w.snapshot().mode,'case');
 w.dispose();assert.equal(w.snapshot().worklist.length,0);
});
test('B0370: changed patient or shift clears all cached clinical context',async()=>{
 const {w}=fixture(url=>url.includes('/views/')?{cases:[row()]}:{case:row()});
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 assert.equal(w.snapshot().patientId,P);
 w.bind({...scope,shiftId:U(13),epoch:'verified-identity-2'});
 assert.equal(w.snapshot().patientId,null);assert.equal(w.snapshot().selectedCase,null);
 assert.equal(w.snapshot().worklist.length,0);
 await assert.rejects(w.select(C));w.dispose();
});
test('B0370: stale worklist response after patient switch cannot disclose old patient',async()=>{
 let complete;const {w}=fixture((url)=>url.includes('/views/')?new Promise(resolve=>{complete=()=>resolve({cases:[row()]})}):{case:row()});
 w.bind(scope);const pending=w.load(P,'FACILITY_QUEUE',[C]);
 await new Promise(resolve=>setImmediate(resolve));
 w.bind({...scope,epoch:'shift-session-replaced'});
 complete();await assert.rejects(pending,e=>e.category==='unavailable');
 assert.equal(w.snapshot().patientId,null);assert.equal(w.snapshot().mode,'worklist');w.dispose();
});
test('B0371: successful mutation invalidates queue and reloads authoritative case revision',async()=>{
 let revision=1;const {w,calls}=fixture((url,options)=>{
  if(url.includes('/views/'))return {cases:[row()]};
  if(url.endsWith('/commands')){assert.equal(JSON.parse(options.body).expectedRevision,1);revision=2;return {revision:2,eventDigest:'a'.repeat(64),replay:false};}
  return {case:row(C,revision)};
 });
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 const result=await w.command(COMMAND,'QUEUE_CALL');
 assert.equal(result.revision,2);assert.equal(w.snapshot().selectedCase.revision,2);
 assert.equal(w.snapshot().worklist.length,0);assert.equal(w.snapshot().mode,'case');
 assert.equal(calls.filter(x=>x.url.endsWith('/commands')).length,1);w.dispose();
});
test('B0371: write accepted but authoritative reread fails => no optimistic case display',async()=>{
 let touched=false;const {w}=fixture((url)=>{
  if(url.includes('/views/'))return {cases:[row()]};
  if(url.endsWith('/commands')){touched=true;return {revision:2,eventDigest:'a'.repeat(64),replay:false};}
  if(touched)throw new Error('synthetic lost read');
  return {case:row()};
 });
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 await assert.rejects(w.command(COMMAND,'QUEUE_CALL'));
 assert.equal(w.snapshot().selectedCase,null);assert.equal(w.snapshot().mode,'refresh-required');
 assert.equal(w.snapshot().worklist.length,0);w.dispose();
});
test('B0372: authentication loss during write revokes entire workstation context',async()=>{
 const {w}=fixture(url=>url.includes('/views/')?{cases:[row()]}:url.endsWith('/commands')?{status:401}:{case:row()});
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 await assert.rejects(w.command(COMMAND,'QUEUE_CALL'),e=>e.category==='reauthenticate');
 assert.equal(w.snapshot().mode,'locked');assert.equal(w.snapshot().patientId,null);
 assert.equal(w.snapshot().worklist.length,0);w.dispose();
});
test('B0372: a patient switch while a write is pending never reopens old case',async()=>{
 let finish;const {w}=fixture((url)=>{
  if(url.includes('/views/'))return {cases:[row()]};
  if(url.endsWith('/commands'))return new Promise(resolve=>{finish=()=>resolve({revision:2,eventDigest:'a'.repeat(64),replay:false})});
  return {case:row()};
 });
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 const pending=w.command(COMMAND,'QUEUE_CALL');await new Promise(resolve=>setImmediate(resolve));
 w.bind({...scope,epoch:'new-verified-token'});finish();
 await assert.rejects(pending,e=>e.category==='unavailable');
 assert.equal(w.snapshot().mode,'worklist');assert.equal(w.snapshot().selectedCase,null);w.dispose();
});
test('B0369: malformed replacement identity revokes the previous patient context immediately',async()=>{
 const {w}=fixture(url=>url.includes('/views/')?{cases:[row()]}:{case:row()});
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 assert.throws(()=>w.bind({...scope,facilityId:'tampered'}));
 assert.deepEqual({mode:w.snapshot().mode,patient:w.snapshot().patientId},{mode:'locked',patient:null});w.dispose();
});
test('B0369: invalid patient selector clears previously authorized case before rejecting',async()=>{
 const {w}=fixture(url=>url.includes('/views/')?{cases:[row()]}:{case:row()});
 w.bind(scope);await w.load(P,'FACILITY_QUEUE',[C]);await w.select(C);
 await assert.rejects(w.load('bad-patient','FACILITY_QUEUE',[C]),e=>e.category==='invalid');
 assert.equal(w.snapshot().selectedCase,null);assert.equal(w.snapshot().patientId,null);w.dispose();
});
