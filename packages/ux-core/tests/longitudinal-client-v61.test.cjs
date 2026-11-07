const {test}=require('node:test');
const assert=require('node:assert/strict');
const {longitudinalClient,ClinicalClientError,clinicalAuditPage,clinicalCaseView,clinicalNoteView}=require('../dist');
const U=n=>`10000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const scope={patientId:U(3),encounterId:U(5),revision:2};
const good={revision:3,auditDigest:'a'.repeat(64),replay:false};
const response=(json,status=200,extra={})=>({status,headers:{get:key=>key in extra?extra[key]:key==='Cache-Control'?'no-store, private':key==='Content-Type'?'application/json; charset=utf-8':null},json:async()=>json});
function fixture(fetch,token=async()=> 'VERIFIED_SYNTHETIC_SESSION_TOKEN_1234',online=()=>true){return longitudinalClient('https://synqoria.example/',{fetch,token,online});}
test('B0070: authenticated command carries scoped IDs only in URL and explicit command fields in JSON',async()=>{
 let sent;const c=fixture(async (url,init)=>{sent={url,init};return response(good);});
 assert.deepEqual(await c.command(scope,U(20),'encounter.record_room',{room:'Synthetic ward'}),good);
 assert.equal(sent.url,`https://synqoria.example/api/v1/clinical/patients/${scope.patientId}/encounters/${scope.encounterId}/commands`);
 assert.deepEqual(JSON.parse(sent.init.body),{commandId:U(20),expectedRevision:2,operation:'encounter.record_room',inputs:{room:'Synthetic ward'}});
 assert.equal(sent.init.credentials,'omit');assert.equal(sent.init.cache,'no-store');assert.equal(sent.init.redirect,'error');
 assert.equal(sent.init.headers.Authorization.startsWith('Bearer '),true);
});
test('B0070: client rejects unexpected server payload and insecure response metadata',async()=>{
 for(const value of [{...good,patientName:'injected'},{...good,revision:-1},{...good,auditDigest:'bad'}]){
  await assert.rejects(fixture(async()=>response(value)).command(scope,U(20),'encounter.checkin',{}),e=>e.kind==='unavailable');
 }
 await assert.rejects(fixture(async()=>response(good,200,{'Cache-Control':'public'})).command(scope,U(20),'encounter.checkin',{}),e=>e.kind==='unavailable');
});
test('B0070: stale or changed patient context cancels result even after HTTP success',async()=>{
 let finish;let current=true;let arrived;const reached=new Promise(resolve=>arrived=resolve);
 const c=fixture(async()=>({status:200,headers:{get:k=>k==='Cache-Control'?'no-store, private':k==='Content-Type'?'application/json':null},json:()=>new Promise(r=>{finish=()=>r(good);arrived();})}));
 const pending=assert.rejects(c.command(scope,U(20),'encounter.checkin',{},undefined,()=>current),e=>e.kind==='unavailable');
 await reached;await new Promise(r=>setImmediate(r));current=false;finish();
 await pending;
});
test('B0070: audit decoder rejects out-of-order revisions and clinical free text',()=>{
 const item=(n)=>({revision:n,operation:'note.sign',at:'2026-10-31T10:45:00Z',digest:'a'.repeat(64)});
 assert.equal(clinicalAuditPage({history:[item(3)],nextRevision:null},2).history.length,1);
 assert.throws(()=>clinicalAuditPage({history:[item(3),item(2)],nextRevision:null},2));
 assert.throws(()=>clinicalAuditPage({history:[{...item(3),body:'PHI'}],nextRevision:null},2));
});
test('B0070: high-risk reads do not disclose extra PHI fields to UI',()=>{
 const summary={encounterId:scope.encounterId,revision:3,stage:'ACTIVE',onLeave:false,notes:0,observations:0,auditHead:'a'.repeat(64)};
 assert.equal(clinicalCaseView(summary,scope.encounterId).revision,3);
 assert.throws(()=>clinicalCaseView({...summary,patientFullName:'synthetic'},scope.encounterId));
 const note={id:U(8),author:U(6),title:'Synthetic title',body:'Synthetic note',status:'DRAFT',confidential:false};
 assert.equal(clinicalNoteView(note,U(8)).body,'Synthetic note');
 assert.throws(()=>clinicalNoteView({...note,otherFacilityId:U(90)},U(8)));
});
test('B0070: never queue offline writes or follow redirect and validate origin',async()=>{
 let count=0;
 const c=fixture(async()=>{count++;return response(good);},undefined,()=>false);
 await assert.rejects(c.command(scope,U(20),'encounter.checkin',{}),e=>e.kind==='unavailable');
 assert.equal(count,0);
 assert.throws(()=>longitudinalClient('http://synqoria.example/',{fetch:()=>{},online:()=>true,token:()=>{}}));
});
