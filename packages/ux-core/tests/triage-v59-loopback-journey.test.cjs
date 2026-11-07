const {test}=require('node:test');const assert=require('node:assert/strict');
const {TriageSecuredCommandJourney,triageActionPresentation}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const digest='a'.repeat(64);const row=(revision)=>({caseId:U(2),stage:'WAITING',revision,station:null,assignee:null,arrivedAt:'2026-10-31T12:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
function fixture(opts={}){
 const urls=[];let rev=1,writes=0;
 const transport={token:async()=> 'synthetic_verified_token_abcdefghijklmnopqrstuvwxyz',isOnline:()=>true,
  fetch:async(url,options)=>{
   urls.push(url);let body;
   if(url.endsWith('/commands')){writes++;rev=opts.commit===false?1:2;
    if(opts.failWrite)throw new Error('synthetic network loss');
    body={revision:2,eventDigest:digest,replay:false};}
   else if(url.includes('history?'))body={history:rev===2?[{revision:2,operation:'QUEUE_CALL',occurredAt:'2026-10-31T12:01:00Z',digest}]:[],nextAfterRevision:null};
   else body={case:row(rev)};
   return {status:200,headers:{get:k=>k==='Cache-Control'?'no-store, private':k==='Content-Type'?'application/json':null},json:async()=>body};}
 };
 const journey=new TriageSecuredCommandJourney('https://cell.example/',transport);
 journey.bind(U(1),U(2),'identity-version-1',['triage:queue']);
 return {journey,urls,writes:()=>writes};
}
test('B0389: real triage HTTP client roundtrip verifies audit and server-authoritative revision',async()=>{
 const f=fixture();const receipt=await f.journey.submit(U(3),'QUEUE_CALL');
 assert.equal(receipt.revision,2);assert.equal(f.writes(),1);
 assert.ok(f.urls.some(x=>x.endsWith('/commands')));
 assert.ok(f.urls.some(x=>x.includes('history?afterRevision=1')));
 assert.equal(f.journey.snapshot().operation,'confirmed');
});
test('B0389: HTTP journey never permits missing capability even with a valid token',async()=>{
 const f=fixture();await assert.rejects(f.journey.submit(U(3),'ASSESSMENT_ATTEST'),e=>e.category==='denied');
 assert.equal(f.writes(),0);
});
test('B0389: lost HTTP acknowledgement cannot be called success or blindly retried',async()=>{
 const f=fixture({failWrite:true});await assert.rejects(f.journey.submit(U(3),'QUEUE_CALL'));
 assert.equal(f.journey.snapshot().operation,'uncertain');
 assert.equal(await f.journey.recover(),'unresolved');assert.equal(f.writes(),1);
 await assert.rejects(f.journey.retry());assert.equal(f.writes(),1);
});
test('B0390: action presentation is blank until verified session exists',()=>{
 assert.deepEqual(triageActionPresentation(['triage:queue'],'en',false),[]);
});
test('B0390: action presentation matches capability, input kind, confirmation requirements',()=>{
 const a=triageActionPresentation(['triage:queue','triage:sign'],'en',true);
 assert.ok(a.some(x=>x.action==='QUEUE_CALL'&&x.input==='none'));
 assert.ok(a.some(x=>x.action==='ASSESSMENT_ATTEST'&&x.confirmationRequired===true));
 assert.equal(a.some(x=>x.action==='HANDOVER_REQUEST'),false);
});
test('B0390: bilingual critical action labels are descriptive and accessible',()=>{
 const fr=triageActionPresentation(['triage:manage'],'fr',true);
 assert.equal(fr.find(x=>x.action==='SAFETY_HOLD_RELEASE').label,'Lever la mesure de sécurité');
 assert.ok(fr.every(x=>x.accessibleHint.includes('Vérification')));
});
