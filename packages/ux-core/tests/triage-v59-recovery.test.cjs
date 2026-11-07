const {test}=require('node:test');const assert=require('node:assert/strict');
const {TriageMutationRecovery,TriageTransportError}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const patient=U(1),caseId=U(2),commandId=U(3),scope={caseId,revision:1};
const row=(rev=1)=>({caseId,stage:'WAITING',revision:rev,station:null,assignee:null,arrivedAt:'2026-10-31T00:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
const d='a'.repeat(64);
function fixture(options={}){
 let revision=1,mutations=0,reads=0,histories=0;
 const port={
  async summary(){reads++;if(options.failRead)throw new Error('read unavailable');return row(revision);},
  async history(){histories++;if(options.failAudit)throw new Error('audit unavailable');return {history:revision===2?[{revision:2,operation:'QUEUE_CALL',occurredAt:'2026-10-31T00:00:00Z',digest:d}]:[],nextAfterRevision:null};},
  async command(_scope,id,action,payload){mutations++;assert.equal(id,commandId);assert.equal(action,'QUEUE_CALL');revision=options.writeCommits===false?1:2;
   if(options.rejectWrite)throw new Error('network lost');return {revision:2,eventDigest:options.digest||d,replay:false};}
 };
 const recovery=new TriageMutationRecovery(port);recovery.bind(patient,caseId);
 return {recovery,stats:()=>({mutations,reads,histories}),setRevision:v=>revision=v};
}
test('B0372: acknowledged write requires persisted digest plus authorized reread',async()=>{
 const {recovery,stats}=fixture();const result=await recovery.submit(row(),commandId,'QUEUE_CALL');
 assert.equal(result.revision,2);assert.equal(stats().mutations,1);assert.equal(stats().histories,1);assert.equal(stats().reads,1);
 assert.equal(recovery.snapshot().state,'confirmed');assert.equal(recovery.snapshot().hasPending,false);
});
test('B0372: audit digest mismatch leaves uncertain, never claims success',async()=>{
 const {recovery}=fixture({digest:'b'.repeat(64)});await assert.rejects(recovery.submit(row(),commandId,'QUEUE_CALL'));
 assert.equal(recovery.snapshot().state,'uncertain');
});
test('B0372: request failure after commit is uncertain, no blind retry',async()=>{
 const {recovery,stats}=fixture({rejectWrite:true});await assert.rejects(recovery.submit(row(),commandId,'QUEUE_CALL'));
 assert.equal(stats().mutations,1);assert.equal(await recovery.recover(),'unresolved');assert.equal(stats().mutations,1);
 await assert.rejects(recovery.retry());assert.equal(stats().mutations,1);
});
test('B0372: explicit retry reuses command ID only if revision unchanged',async()=>{
 let attempt=0;const port={summary:async()=>row(),history:async()=>({history:[],nextAfterRevision:null}),
  command:async(_s,id)=>{assert.equal(id,commandId);attempt++;throw new Error('network lost');}};
 const recovery=new TriageMutationRecovery(port);recovery.bind(patient,caseId);
 await assert.rejects(recovery.submit(row(),commandId,'QUEUE_CALL'));
 assert.equal(await recovery.recover(),'retry-allowed');await assert.rejects(recovery.retry());assert.equal(attempt,2);
});
test('B0372: cross-patient context change aborts in-flight mutation',async()=>{
 let finish;const port={summary:async()=>row(),history:async()=>({history:[],nextAfterRevision:null}),command:()=>new Promise(resolve=>finish=resolve)};
 const recovery=new TriageMutationRecovery(port);recovery.bind(patient,caseId);
 const pending=recovery.submit(row(),commandId,'QUEUE_CALL');await new Promise(r=>setImmediate(r));
 recovery.bind(U(6),U(7));finish({revision:2,eventDigest:d,replay:false});
 await assert.rejects(pending,e=>e.category==='unavailable');assert.equal(recovery.snapshot().state,'ready');assert.equal(recovery.snapshot().hasPending,false);
});
test('B0372: multiple in-flight commands and invalid action are rejected',async()=>{
 let finish;const port={summary:async()=>row(),history:async()=>({history:[],nextAfterRevision:null}),command:()=>new Promise(resolve=>finish=resolve)};
 const recovery=new TriageMutationRecovery(port);recovery.bind(patient,caseId);
 await assert.rejects(recovery.submit(row(),U(9),'NOT_A_TRIAGE_ACTION'));
 const pending=recovery.submit(row(),commandId,'QUEUE_CALL');await assert.rejects(recovery.submit(row(),U(8),'QUEUE_RECALL'));
 recovery.clear();finish({revision:2,eventDigest:d,replay:false});await assert.rejects(pending);
});
test('B0372: denied authorization revokes pending state',async()=>{
 const recovery=new TriageMutationRecovery({summary:async()=>row(),history:async()=>({history:[],nextAfterRevision:null}),command:async()=>{throw new TriageTransportError('denied',403);}});
 recovery.bind(patient,caseId);await assert.rejects(recovery.submit(row(),commandId,'QUEUE_CALL'));
 assert.equal(recovery.snapshot().state,'locked');assert.equal(recovery.snapshot().hasPending,false);
});
