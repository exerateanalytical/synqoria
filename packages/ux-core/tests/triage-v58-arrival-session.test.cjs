const {test}=require('node:test');const assert=require('node:assert/strict');const {TriageArrivalSession}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const scope={tenantId:U(1),facilityId:U(2),actorId:U(3),shiftId:U(4),epoch:'synthetic-trusted-1'};
const P=U(5),C=U(6),ID=U(7),APPT=U(8);
const summary=(revision=1)=>({case:{caseId:C,stage:'ARRIVED',revision,station:null,assignee:null,arrivedAt:'2026-10-31T08:00:00Z',paused:false,safetyHold:false,manualEscalation:false}});
const receipt=(replay=false)=>({revision:1,eventDigest:'a'.repeat(64),replay});
function fixture(handler){const calls=[];const transport={token:async()=> 'synthetic_trusted_token_12345',isOnline:()=>true,fetch:async(url,options)=>{
 calls.push({url,options});const value=await handler(url,options);
 return {status:value.status??(url.endsWith('/intake')?201:200),headers:{get:n=>n==='Cache-Control'?'no-store, private':null},json:async()=>value.body??value};
 }};return {session:new TriageArrivalSession('https://cell.example/',transport),calls};}
test('B0371: verified context and canonical IDs required before intake',async()=>{
 const {session,calls}=fixture(url=>url.endsWith('/intake')?receipt():summary());
 await assert.rejects(session.start(P,C,ID));session.bind(scope);
 await assert.rejects(session.start(P,'invalid',ID));assert.equal(calls.length,0);session.dispose();
});
test('B0371: successful intake must be followed by server-authoritative scoped case read',async()=>{
 const {session,calls}=fixture(url=>url.endsWith('/intake')?receipt():summary());
 session.bind(scope);const result=await session.start(P,C,ID,APPT);
 assert.equal(result.caseId,C);assert.equal(session.snapshot().status,'confirmed');
 assert.equal(calls.length,2);assert.equal(JSON.parse(calls[0].options.body).appointment,APPT);
 assert.equal(JSON.parse(calls[0].options.body).commandId,ID);session.dispose();
});
test('B0371: uncertain receipt never returns a phantom confirmed arrival',async()=>{
 const {session}=fixture(url=>{if(url.endsWith('/intake'))throw Error('connection reset');return summary();});
 session.bind(scope);await assert.rejects(session.start(P,C,ID));
 assert.equal(session.snapshot().confirmedCase,null);assert.equal(session.snapshot().status,'uncertain');session.dispose();
});
test('B0371: retry preserves original command ID and does not create a duplicate',async()=>{
 let attempt=0;const {session,calls}=fixture(url=>{
  if(url.endsWith('/intake')){attempt++;if(attempt===1)throw Error('timeout');return receipt(true);}return summary();
 });session.bind(scope);await assert.rejects(session.start(P,C,ID));
 assert.equal((await session.retry()).revision,1);assert.equal(session.snapshot().status,'confirmed');
 assert.equal(calls.filter(x=>x.url.endsWith('/intake')).length,2);
 assert.deepEqual(calls.filter(x=>x.url.endsWith('/intake')).map(x=>JSON.parse(x.options.body).commandId),[ID,ID]);session.dispose();
});
test('B0371: case reread failure cannot falsely confirm a successful write',async()=>{
 const {session}=fixture(url=>{if(url.endsWith('/intake'))return receipt();throw Error('unavailable read');});
 session.bind(scope);await assert.rejects(session.start(P,C,ID));
 assert.equal(session.snapshot().status,'uncertain');session.dispose();
});
test('B0371: identity switch during an in-flight arrival discards stale acknowledgement',async()=>{
 let finish;const {session}=fixture(url=>url.endsWith('/intake')?new Promise(resolve=>{finish=()=>resolve(receipt());}):summary());
 session.bind(scope);const pending=session.start(P,C,ID);await new Promise(resolve=>setImmediate(resolve));
 session.bind({...scope,epoch:'new-verified-session'});finish();
 await assert.rejects(pending,e=>e.category==='unavailable');
 assert.equal(session.snapshot().confirmedCase,null);assert.equal(session.snapshot().status,'ready');session.dispose();
});
test('B0371: auth revocation purges case and session evidence without retry',async()=>{
 const {session}=fixture(url=>url.endsWith('/intake')?{status:401,body:{error:'auth_required'}}:summary());
 session.bind(scope);await assert.rejects(session.start(P,C,ID),e=>e.category==='reauthenticate');
 assert.deepEqual(session.snapshot(),{status:'locked',patientId:null,caseId:null,confirmedCase:null});
 await assert.rejects(session.retry());session.dispose();
});
test('B0371: invalid identity replacement wipes prior confirmed arrival',async()=>{
 const {session}=fixture(url=>url.endsWith('/intake')?receipt():summary());
 session.bind(scope);await session.start(P,C,ID);
 assert.throws(()=>session.bind({...scope,actorId:'malformed'}));
 assert.equal(session.snapshot().confirmedCase,null);assert.equal(session.snapshot().status,'locked');session.dispose();
});
