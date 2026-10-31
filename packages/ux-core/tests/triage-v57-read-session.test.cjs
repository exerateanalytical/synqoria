const {test}=require('node:test');const assert=require('node:assert/strict');
const {triageClient,triageDecodeAuditPage,triageDecodeSummary,TriageTransportError,makeTriageCaseSession}=require('../dist');
const P='00000000-0000-0000-0000-000000000003',C='00000000-0000-0000-0000-000000000006',D='00000000-0000-0000-0000-000000000007';
const digest='a'.repeat(64);
const summary=(id=C,revision=1)=>({caseId:id,stage:'WAITING',revision,station:null,assignee:null,arrivedAt:'2026-10-30T14:30:00Z',paused:false,safetyHold:false,manualEscalation:false});
const history=(rev)=>({revision:rev,operation:'QUEUE_ENQUEUE',occurredAt:'2026-10-30T14:31:00Z',digest});
function fixture(){let status=200,cache='no-store, private',value={case:summary()}, online=true,token='syntheticSession_1234567890';const calls=[];
 const transport={token:async()=>token,isOnline:()=>online,fetch:async(url,init)=>{calls.push({url,init});return {status,headers:{get:(k)=>k==='Cache-Control'?cache:k==='Content-Type'?'application/json':null},json:async()=>value};}};
 return {transport,calls,set:(newValue,code=200)=>{value=newValue;status=code;},offline:()=>{online=false;},cache:(v)=>{cache=v;},token:(v)=>{token=v;}};
}
test('scoped GET summary enforces HTTPS, no bearer persistence, no-store and exact projection',async()=>{
 const f=fixture();const client=triageClient('https://cell.example/',f.transport);
 assert.deepEqual(await client.summary(P,C),summary());
 assert.equal(f.calls[0].url,`https://cell.example/api/v1/triage/patients/${P}/cases/${C}`);
 assert.equal(f.calls[0].init.method,'GET');assert.equal(f.calls[0].init.body,undefined);
 assert.equal(f.calls[0].init.credentials,'omit');assert.equal(f.calls[0].init.cache,'no-store');assert.equal(f.calls[0].init.redirect,'error');
 assert.match(f.calls[0].init.headers.Authorization,/Bearer syntheticSession/);
 assert.ok(!f.calls[0].url.includes('syntheticSession'));
});
test('summary decoder rejects mismatched identifiers, extra sensitive fields and bad state',()=>{
 for(const bad of [{case:{...summary(D)}},{case:{...summary(),freeText:'do not expose'}},{case:{...summary(),stage:'ADMIN'}},{case:{...summary(),revision:0}},{case:summary(),extra:1}])
  assert.throws(()=>triageDecodeSummary(bad,C));
});
test('audit paging validates sorted revisions, digest, operation and bound',()=>{
 assert.deepEqual(triageDecodeAuditPage({history:[history(1),history(2)],nextAfterRevision:null},0).history,[history(1),history(2)]);
 const p={history:Array.from({length:100},(_,i)=>history(i+1)),nextAfterRevision:100};
 assert.equal(triageDecodeAuditPage(p,0).nextAfterRevision,100);
 for(const bad of [{history:[history(2),history(1)],nextAfterRevision:null},{history:[history(2),history(2)],nextAfterRevision:null},{history:[{...history(1),digest:'bad'}],nextAfterRevision:null},{history:[{...history(1),operation:'untrusted text'}],nextAfterRevision:null},{history:[{...history(1),note:'clinical text'}],nextAfterRevision:null},{history:[history(1)],nextAfterRevision:1},{history:Array.from({length:101},(_,i)=>history(i+1)),nextAfterRevision:101}])assert.throws(()=>triageDecodeAuditPage(bad,0));
});
test('GET history cursor is bounded and patient/case scoped',async()=>{
 const f=fixture();f.set({history:[history(8)],nextAfterRevision:null});const c=triageClient('https://cell.example/',f.transport);
 assert.equal((await c.history(P,C,7)).history[0].revision,8);
 assert.equal(f.calls[0].url,`https://cell.example/api/v1/triage/patients/${P}/cases/${C}/history?afterRevision=7`);
 await assert.rejects(c.history(P,C,-1));await assert.rejects(c.history(P,C,2147483648));
 assert.equal(f.calls.length,1);
});
test('GET fails closed for access denial, server errors, bad cache policy and offline',async()=>{
 const f=fixture();const c=triageClient('https://cell.example/',f.transport);
 f.set({case:summary()},404);await assert.rejects(c.summary(P,C),e=>e.category==='denied');
 f.set({case:summary()},401);await assert.rejects(c.summary(P,C),e=>e.category==='reauthenticate');
 f.set({case:summary()},503);await assert.rejects(c.summary(P,C),e=>e.category==='unavailable');
 f.set({case:summary()},200);f.cache('public');await assert.rejects(c.summary(P,C),e=>e.category==='unavailable');
 f.cache('no-store, private');f.set({case:{...summary(),excess:'x'.repeat(70000)}});
 await assert.rejects(c.summary(P,C),e=>e.category==='unavailable');
 f.offline();const before=f.calls.length;
 await assert.rejects(c.summary(P,C),e=>e.category==='unavailable');assert.equal(f.calls.length,before);
});
test('case session opens scoped summary, writes with locked revision, requires authoritative refresh',async()=>{
 const f=fixture();const session=makeTriageCaseSession('https://cell.example/',f.transport);const observed=[];
 session.subscribe(s=>observed.push(s.status));await session.open(P,C);
 f.set({revision:2,eventDigest:digest,replay:false});const result=await session.command('11111111-1111-1111-1111-111111111111','QUEUE_PAUSE');
 assert.equal(result.revision,2);assert.equal(session.snapshot().status,'refresh-required');
 assert.equal(f.calls.at(-1).init.method,'POST');
 assert.equal(JSON.parse(f.calls.at(-1).init.body).expectedRevision,1);
 await assert.rejects(session.command('11111111-1111-1111-1111-111111111111','QUEUE_PAUSE'));
 f.set({case:summary(C,2)});await session.refresh();assert.equal(session.snapshot().case.revision,2);
 assert.deepEqual(observed.slice(0,3),['closed','closed','loading']);
 session.close();assert.equal(session.snapshot().patientId,null);assert.equal(session.snapshot().case,null);
});
test('case session rejects invalid revision receipt and clears mutation availability',async()=>{
 const f=fixture();const s=makeTriageCaseSession('https://cell.example/',f.transport);await s.open(P,C);
 f.set({revision:9,eventDigest:digest,replay:false});await assert.rejects(s.command('11111111-1111-1111-1111-111111111111','QUEUE_PAUSE'));
 assert.equal(s.snapshot().status,'refresh-required');assert.equal(s.snapshot().case,null);
});
test('denied command closes sensitive patient context',async()=>{
 const f=fixture();const s=makeTriageCaseSession('https://cell.example/',f.transport);await s.open(P,C);
 f.set({},403);await assert.rejects(s.command('11111111-1111-1111-1111-111111111111','QUEUE_PAUSE'),e=>e.category==='denied');
 assert.deepEqual(s.snapshot(),{status:'closed',patientId:null,case:null});
});
test('patient switch discards in-flight asynchronous summary and cannot restore stale patient',async()=>{
 let finish;let count=0;
 const transport={token:async()=> 'syntheticSession_1234567890',isOnline:()=>true,
  fetch:async()=>{count++;if(count===1)return new Promise(resolve=>{finish=()=>resolve({status:200,headers:{get:(name)=>name==='Cache-Control'?'no-store, private':null},json:async()=>({case:summary()})})});return {status:200,headers:{get:(name)=>name==='Cache-Control'?'no-store, private':null},json:async()=>({case:summary(D)})};}};
 const s=makeTriageCaseSession('https://cell.example/',transport);const pending=s.open(P,C);
 await new Promise(resolve=>setImmediate(resolve));await s.open(P,D);finish();
 await assert.rejects(pending,e=>e.category==='unavailable');assert.equal(s.snapshot().case.caseId,D);
});
test('history reads do not mutate revision or publish sensitive free text',async()=>{
 const f=fixture();const s=makeTriageCaseSession('https://cell.example/',f.transport);await s.open(P,C);
 f.set({history:[history(1)],nextAfterRevision:null});assert.equal((await s.history()).history.length,1);
 assert.equal(s.snapshot().case.revision,1);assert.equal(JSON.stringify(s.snapshot()).includes('clinical text'),false);
});
