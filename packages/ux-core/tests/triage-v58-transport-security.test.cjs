const {test}=require('node:test');const assert=require('node:assert/strict');
const {triageClient}=require('../dist');const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const scope={patientId:U(2),caseId:U(3),expectedRevision:1};const ID=U(4);
const reply=(json,headers={},status=200)=>({status,headers:{get:name=>name in headers?headers[name]:name==='Cache-Control'?'no-store, private':null},json:async()=>json});
const transport=(fetch,token=async()=> 'synthetic_session_verified_123456')=>({isOnline:()=>true,token,fetch});
const good={revision:2,eventDigest:'a'.repeat(64),replay:false};
test('B0370: mutation responses enforce privacy no-store and application/json',async()=>{
 for(const headers of [{'Cache-Control':'public'},{'Content-Type':'text/html'},{'Content-Length':'999999'}, {'Content-Length':'-1'}]){
  const c=triageClient('https://cell.example/',transport(async()=>reply(good,headers)));
  await assert.rejects(c.command(scope,ID,'QUEUE_CALL'),e=>e.category==='unavailable');
 }
 const ok=triageClient('https://cell.example/',transport(async()=>reply(good,{'Content-Type':'application/json; charset=utf-8'})));
 assert.deepEqual(await ok.command(scope,ID,'QUEUE_CALL'),good);
});
test('B0370: excessively large mutation response is rejected even if length header omitted',async()=>{
 const c=triageClient('https://cell.example/',transport(async()=>reply({...good,extra:'x'.repeat(70000)})));
 await assert.rejects(c.command(scope,ID,'QUEUE_CALL'),e=>e.category==='unavailable');
});
test('B0370: cancelled context during asynchronous token retrieval never issues a request',async()=>{
 let finish;let sent=0;const controller=new AbortController();
 const c=triageClient('https://cell.example/',transport(async()=>{sent++;return reply(good);},()=>new Promise(resolve=>{finish=()=>resolve('synthetic_session_verified_123456');})));
 const pending=c.command(scope,ID,'QUEUE_CALL',{},controller.signal);
 await new Promise(resolve=>setImmediate(resolve));controller.abort();finish();
 await assert.rejects(pending,e=>e.category==='unavailable');assert.equal(sent,0);
});
test('B0370: cancelled context while response JSON resolves never returns PHI or accepts mutation',async()=>{
 let finish;const controller=new AbortController();
 const c=triageClient('https://cell.example/',transport(async()=>({status:200,headers:{get:n=>n==='Cache-Control'?'no-store, private':null},json:()=>new Promise(resolve=>{finish=()=>resolve(good);})})));
 const pending=c.command(scope,ID,'QUEUE_CALL',{},controller.signal);
 await new Promise(resolve=>setImmediate(resolve));controller.abort();finish();
 await assert.rejects(pending,e=>e.category==='unavailable');
});
test('B0370: failed/unparseable server JSON remains an unavailable error',async()=>{
 const c=triageClient('https://cell.example/',transport(async()=>({status:200,headers:{get:n=>n==='Cache-Control'?'no-store, private':null},json:async()=>{throw new SyntaxError('invalid');}})));
 await assert.rejects(c.command(scope,ID,'QUEUE_CALL'),e=>e.category==='unavailable');
});
test('B0370: mutation response limit counts UTF-8 bytes, not only JavaScript string length',async()=>{
 const data={...good,extra:'🩺'.repeat(20000)};
 const c=triageClient('https://cell.example/',transport(async()=>reply(data)));
 await assert.rejects(c.command(scope,ID,'QUEUE_CALL'),e=>e.category==='unavailable');
});
