const {test}=require('node:test'),a=require('node:assert/strict');
const {TriageClinicalStaffSession}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const row=rev=>({caseId:U(7),stage:'WAITING',revision:rev,station:null,assignee:null,arrivedAt:'2026-10-31T12:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
function fixture(){let rev=1,online=true,writes=0;const transport={token:async()=> 'synthetic_verified_token_abcdefghijklmnopqrst',isOnline:()=>online,
 fetch:async(url,options)=>{
  let body;if(url.includes('/views/'))body={cases:[row(rev)]};
  else if(url.includes('/history?'))body={history:[],nextAfterRevision:null};
  else if(url.endsWith('/commands')){writes++;rev++;body={revision:rev,eventDigest:'a'.repeat(64),replay:false};}
  else body={case:row(rev)};
  return {status:200,headers:{get:key=>key==='Cache-Control'?'no-store, private':key==='Content-Type'?'application/json':null},json:async()=>body};}};
 const session=new TriageClinicalStaffSession('https://cell.example/',transport,{isOnline:()=>online,newCommandId:()=>U(11),now:()=>2000});
 const lease={scope:{tenantId:U(1),facilityId:U(2),actorId:U(3),shiftId:U(4),epoch:'verified-session-1'},grants:['triage:queue'],issuedAtMs:1000,expiresAtMs:4000};
 return {session,lease,online:v=>online=v,writes:()=>writes};}
test('B0391: verified staff session loads only explicit patient worklist before opening case',async()=>{
 const f=fixture();await a.rejects(f.session.worklist(U(6),'FACILITY_QUEUE',[U(7)]),e=>e.category==='reauthenticate');
 f.session.bind(f.lease);await f.session.worklist(U(6),'FACILITY_QUEUE',[U(7)]);
 a.equal(f.session.snapshot().state.worklist.length,1);
 await f.session.select(U(7));a.equal(f.session.snapshot().state.selectedCase.revision,1);
 f.session.dispose();
});
test('B0391: command writes and refreshes the selected patient case without optimistic metadata',async()=>{
 const f=fixture();f.session.bind(f.lease);await f.session.worklist(U(6),'FACILITY_QUEUE',[U(7)]);await f.session.select(U(7));
 await f.session.perform('QUEUE_CALL');a.equal(f.writes(),1);a.equal(f.session.snapshot().state.selectedCase.revision,2);
 a.equal(f.session.snapshot().state.worklist.length,0);f.session.dispose();
});
test('B0391: backgrounding and facility switch wipe all patient context, reauthentication required',async()=>{
 const f=fixture();f.session.bind(f.lease);await f.session.worklist(U(6),'FACILITY_QUEUE',[U(7)]);await f.session.select(U(7));
 f.session.onOfflineOrBackground();a.equal(f.session.snapshot().bound,false);a.equal(f.session.snapshot().state.selectedCase,null);
 await a.rejects(f.session.perform('QUEUE_CALL'));
 f.session.bind({...f.lease,scope:{...f.lease.scope,epoch:'new-session-2'}});f.session.onFacilityShiftOrActorChanged();
 a.equal(f.session.snapshot().state.patientId,null);f.session.dispose();
});
