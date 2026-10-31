const {test}=require('node:test');const assert=require('node:assert/strict');
const {makeTriageWorklistSession,makeTriageCaseSession,TriageAuditPager}=require('../dist');
const P='00000000-0000-0000-0000-000000000003',C='00000000-0000-0000-0000-000000000006',D='00000000-0000-0000-0000-000000000007';
const row=(id=C,rev=1)=>({caseId:id,stage:'WAITING',revision:rev,station:null,assignee:null,arrivedAt:'2026-10-30T14:30:00Z',paused:false,safetyHold:false,manualEscalation:false});
const event=rev=>({revision:rev,operation:'ARRIVAL_REASON',occurredAt:'2026-10-30T14:31:00Z',digest:'a'.repeat(64)});
function setup(respond){const calls=[];const transport={token:async()=> 'syntheticSession_1234567890',isOnline:()=>true,fetch:async(url,options)=>{calls.push({url,options});const response=await respond(url,options);return {status:response?.status??200,headers:{get:k=>k==='Cache-Control'?'no-store, private':null},json:async()=>response?.json??response};}};return {transport,calls};}
test('worklist limits case membership and requires patient scope for selection',async()=>{
 const f=setup(()=>({cases:[row(C)]}));const w=makeTriageWorklistSession('https://cell.example/',f.transport);
 await w.load(P,'FACILITY_QUEUE',[C,D]);assert.equal(w.select(C).caseId,C);
 assert.throws(()=>w.select(D),e=>e.category==='denied');assert.equal(w.snapshot().selectedCaseId,C);
 assert.equal(f.calls[0].url,`https://cell.example/api/v1/triage/patients/${P}/views/FACILITY_QUEUE`);
 assert.ok(!f.calls[0].url.includes(C));assert.ok(f.calls[0].options.body.includes(C));
 w.invalidate();assert.deepEqual(w.snapshot(),{state:'empty',patientId:null,cases:[],selectedCaseId:null});
});
test('worklist rejects backend over-disclosure and does not expose data across loads',async()=>{
 let data={cases:[row(C)]};const f=setup(()=>data);const w=makeTriageWorklistSession('https://cell.example/',f.transport);
 await w.load(P,'FACILITY_QUEUE',[C]);assert.equal(w.snapshot().cases.length,1);
 data={cases:[row(D)]};await assert.rejects(w.load(P,'FACILITY_QUEUE',[C]));
 assert.equal(w.snapshot().cases.length,0);assert.equal(w.snapshot().patientId,null);
});
test('worklist rejects older asynchronous results after another patient/worklist selection',async()=>{
 let finish,calls=0;const f=setup(async()=>{calls++;if(calls===1)return new Promise(resolve=>{finish=()=>resolve({cases:[row(C)]})});return {cases:[row(D)]};});
 const w=makeTriageWorklistSession('https://cell.example/',f.transport);
 const pending=w.load(P,'FACILITY_QUEUE',[C]);await new Promise(resolve=>setImmediate(resolve));
 await w.load(P,'FACILITY_QUEUE',[D]);finish();await assert.rejects(pending,e=>e.category==='unavailable');
 assert.deepEqual(w.snapshot().cases,[row(D)]);
});
test('audit pager reads bounded pages without local persistence',async()=>{
 let page=0;const f=setup((url)=>{
  if(url.includes('/history')){page++;return page===1?{history:Array.from({length:100},(_,i)=>event(i+1)),nextAfterRevision:100}:{history:[event(101)],nextAfterRevision:null};}
  return {case:row()};
 });
 const s=makeTriageCaseSession('https://cell.example/',f.transport);await s.open(P,C);
 const pager=new TriageAuditPager(s);assert.equal((await pager.loadNext()).length,100);
 assert.equal((await pager.loadNext()).length,101);assert.equal(pager.snapshot().nextAfterRevision,null);
 await assert.rejects(pager.loadNext());assert.equal(page,2);pager.dispose();assert.equal(pager.snapshot().history.length,0);
});
test('audit pager clears after patient switch and fails closed on stale async history',async()=>{
 let finish;const f=setup(url=>{
  if(url.includes('/history'))return new Promise(resolve=>{finish=()=>resolve({history:[event(1)],nextAfterRevision:null})});
  return {case:row(url.includes(D)?D:C)};
 });
 const s=makeTriageCaseSession('https://cell.example/',f.transport);await s.open(P,C);
 const pager=new TriageAuditPager(s);const old=pager.loadNext();await new Promise(resolve=>setImmediate(resolve));
 await s.open(P,D);finish();await assert.rejects(old,e=>e.category==='unavailable');
 assert.equal(pager.snapshot().history.length,0);assert.equal(pager.snapshot().nextAfterRevision,0);pager.dispose();
});
test('audit pager cannot read history during writes or while patient scope is closed',async()=>{
 const f=setup(()=>({case:row()}));const s=makeTriageCaseSession('https://cell.example/',f.transport);
 const pager=new TriageAuditPager(s);await assert.rejects(pager.loadNext());
 await s.open(P,C);s.close();await assert.rejects(pager.loadNext());pager.dispose();
});
