const {test}=require('node:test');const a=require('node:assert/strict');
const {TriageBoundedWorklist,TriageAuditReview,TriagePriorityDesk,TriageArrivalEvidenceDesk,TriageTransportError}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const patient=U(1),caseId=U(2);
const row=id=>({caseId:id,stage:'ARRIVED',revision:1,station:null,assignee:null,arrivedAt:'2026-10-31T00:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
test('B0379: worklist fetches exactly 50 scoped cases per request and preserves requested order',async()=>{
 const calls=[],ids=Array.from({length:137},(_,i)=>U(100+i));
 const w=new TriageBoundedWorklist({worklist:async(patientId,view,part)=>{
  a.equal(patientId,patient);a.equal(view,'FACILITY_QUEUE');a.ok(part.length<=50);
  calls.push([...part]);return {cases:[...part].reverse().map(row)};}});
 const result=await w.load(patient,'FACILITY_QUEUE',ids);
 a.equal(result.length,137);a.deepEqual(result.map(x=>x.caseId),ids);a.deepEqual(calls.map(x=>x.length),[50,50,37]);
});
test('B0379: invalid or duplicate case ID is denied before network request',async()=>{
 let calls=0;const w=new TriageBoundedWorklist({worklist:async()=>{calls++;return {cases:[]};}});
 await a.rejects(w.load(patient,'FACILITY_QUEUE',[U(1),U(1)]),e=>e.category==='invalid');
 await a.rejects(w.load(patient,'FACILITY_QUEUE',["bad"]));a.equal(calls,0);
});
test('B0379: a malicious server cannot inject an unrequested case',async()=>{
 const w=new TriageBoundedWorklist({worklist:async()=>({cases:[row(U(999))]})});
 await a.rejects(w.load(patient,'FACILITY_QUEUE',[caseId]),e=>e.category==='unavailable');
});
test('B0379: context switch while loading aborts old patient data',async()=>{
 let complete;const w=new TriageBoundedWorklist({worklist:async()=>new Promise(resolve=>complete=resolve)});
 const pending=w.load(patient,'FACILITY_QUEUE',[caseId]);await new Promise(r=>setImmediate(r));
 w.clear();complete({cases:[row(caseId)]});await a.rejects(pending,e=>e.category==='unavailable');
 a.equal(w.snapshot,undefined);
});
test('B0380: audit review reads strictly ordered, bounded revision evidence',async()=>{
 const calls=[];const digest='a'.repeat(64);
 const port={history:async(_p,_c,after)=>{
  calls.push(after);
  const count=after===0?100:1;
  return {history:Array.from({length:count},(_,i)=>({revision:after+i+1,operation:'QUEUE_CALL',occurredAt:'2026-10-31T00:00:00Z',digest})),nextAfterRevision:after===0?100:null};}};
 const r=new TriageAuditReview(port);r.bind(patient,caseId);await r.nextPage();
 a.equal(r.snapshot().events.length,100);a.equal(r.snapshot().hasMore,true);
 await r.nextPage();a.equal(r.snapshot().events.length,101);a.equal(r.snapshot().hasMore,false);
 a.deepEqual(calls,[0,100]);await a.rejects(r.nextPage());
});
test('B0380: audit review rejects skipped sequence and clears untrusted data',async()=>{
 const r=new TriageAuditReview({history:async()=>({history:[{revision:3,digest:'b'.repeat(64),operation:'QUEUE_CALL'}],nextAfterRevision:null})});
 r.bind(patient,caseId);await a.rejects(r.nextPage(),e=>e.category==='unavailable');a.equal(r.snapshot().events.length,0);
});
test('B0380: switching patient while audit pending does not reveal returned records',async()=>{
 let finish;const r=new TriageAuditReview({history:async()=>new Promise(resolve=>finish=resolve)});r.bind(patient,caseId);
 const p=r.nextPage();await new Promise(r=>setImmediate(r));r.clear();finish({history:[],nextAfterRevision:null});
 await a.rejects(p);a.equal(r.snapshot().events.length,0);a.equal(r.snapshot().caseId,null);
});
test('B0381: clinician priority set/revise only transmits explicit entered decisions and rationale',async()=>{
 const calls=[];const task={perform:async(action,payload)=>{calls.push({action,payload});return {revision:calls.length+1,eventDigest:'a'.repeat(64),replay:false};}};
 const desk=new TriagePriorityDesk(task);
 await desk.setClinicianPriority('Clinician entered priority','Recorded clinical context');
 await desk.reviseClinicianPriority('Clinician amended priority','Clinical reassessment');
 await desk.linkClinicalEvidence('Assessment record reference');
 a.deepEqual(calls.map(x=>x.action),['CLINICIAN_PRIORITY_SET','CLINICIAN_PRIORITY_REVISED','PRIORITY_EVIDENCE_LINK']);
 a.equal(calls[1].payload.rationale,'Clinical reassessment');
});
test('B0381: priority change with missing rationale does not write',async()=>{
 let writes=0;const desk=new TriagePriorityDesk({perform:async()=>{writes++;}});
 a.throws(()=>desk.setClinicianPriority('priority',''));a.throws(()=>desk.reviseClinicianPriority('','because'));
 a.equal(writes,0);
});
test('B0382: intake evidence types remain explicit human-entered, not auto-filled consent',async()=>{
 const calls=[];const desk=new TriageArrivalEvidenceDesk({perform:async(action,payload)=>{calls.push({action,payload});return {revision:calls.length+1,eventDigest:'a'.repeat(64),replay:false};}});
 await desk.reason('Attended referral');await desk.language('French');await desk.interpreter('Requested');
 await desk.accessibility('Assistance requested');await desk.identityEvidence('Manual verification pending');
 await desk.channel('Walk-in');await desk.referral('Referral number');await desk.attendant('Authorized contact');await desk.consentEvidence('Paper consent captured');
 a.deepEqual(calls.map(x=>x.action),['ARRIVAL_REASON','LANGUAGE','INTERPRETER_REQUEST','ACCESSIBILITY_SUPPORT','IDENTITY_VERIFICATION','ARRIVAL_CHANNEL','REFERRAL_REFERENCE','ATTENDANT_CONTACT','CONSENT_EVIDENCE']);
 a.equal(calls.every(x=>x.payload.text.trim().length>0),true);
});
test('B0382: refuses blank consent and identity evidence instead of inventing it',async()=>{
 let calls=0;const desk=new TriageArrivalEvidenceDesk({perform:async()=>{calls++;}});
 a.throws(()=>desk.consentEvidence(' '));a.throws(()=>desk.identityEvidence(''));a.equal(calls,0);
});
