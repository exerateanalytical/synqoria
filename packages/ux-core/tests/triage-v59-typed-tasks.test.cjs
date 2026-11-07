const {test}=require('node:test');const assert=require('node:assert/strict');
const {TriageAuthorizedTasks,TriageObservationDesk,TriageQueueDesk,TriageHandoverDesk,
 TriageEscalationDesk,TriageSafetyDesk,TriageTransportError}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const scope={tenantId:U(1),facilityId:U(2),actorId:U(3),shiftId:U(4),epoch:'signed-session-epoch-1'};
const row=(revision=1)=>({caseId:U(7),stage:'WAITING',revision,station:null,assignee:null,
 arrivedAt:'2026-10-31T10:00:00Z',paused:false,safetyHold:false,manualEscalation:false});
function fixture(grants=['triage:queue','triage:assess','triage:manage','triage:handover','triage:receive','triage:prioritize','triage:sign']){
 let time=2000,online=true,rev=1,busy=false,fail=false,commandN=0,context=true;
 const calls=[];const workflow={bind(s){assert.equal(s.tenantId,scope.tenantId);context=true;},reset(){context=false;},
  snapshot:()=>({mode:context?'case':'locked',patientId:U(6),selectedCase:context?row(rev):null}),
  async command(id,action,payload){calls.push({id,action,payload});if(fail)throw new TriageTransportError('denied',403);
   if(busy)await new Promise(resolve=>setTimeout(resolve,20));rev++;return {revision:rev,eventDigest:'a'.repeat(64),replay:false};}}
 const tasks=new TriageAuthorizedTasks(workflow,{isOnline:()=>online,newCommandId:()=>U(10+commandN++),now:()=>time});
 tasks.bind({scope,grants,issuedAtMs:1000,expiresAtMs:5000});
 return {tasks,calls,workflow,time:v=>time=v,online:v=>online=v,busy:v=>busy=v,fail:v=>fail=v};
}
test('B0373: verified capability lease permits only explicitly granted actions',async()=>{
 const f=fixture(['triage:queue']);assert.equal(f.tasks.allowed('QUEUE_CALL'),true);
 assert.equal(f.tasks.allowed('SAFETY_HOLD'),false);
 await assert.rejects(f.tasks.perform('SAFETY_HOLD'),e=>e.category==='denied');
 await f.tasks.perform('QUEUE_CALL');assert.equal(f.calls.length,1);assert.equal(f.calls[0].action,'QUEUE_CALL');
});
test('B0373: shift lease expiry and offline status disable clinical mutations',async()=>{
 const f=fixture();f.time(5000);assert.equal(f.tasks.allowed('QUEUE_CALL'),false);
 await assert.rejects(f.tasks.perform('QUEUE_CALL'));
 f.time(2000);f.online(false);assert.equal(f.tasks.allowed('QUEUE_CALL'),false);
 await assert.rejects(f.tasks.perform('QUEUE_CALL'));assert.equal(f.calls.length,0);
});
test('B0373: denies reentry while first write in flight and clears session on authorization loss',async()=>{
 const f=fixture();f.busy(true);const pending=f.tasks.perform('QUEUE_CALL');
 await assert.rejects(f.tasks.perform('QUEUE_RECALL'),e=>e.category==='denied');
 await pending;f.fail(true);await assert.rejects(f.tasks.perform('QUEUE_CALL'),e=>e.category==='denied');
 assert.equal(f.workflow.snapshot().mode,'locked');
});
test('B0373: patient and session switch while command runs cannot return stale success',async()=>{
 const f=fixture();f.busy(true);const pending=f.tasks.perform('QUEUE_CALL');
 f.tasks.clear();await assert.rejects(pending,e=>e.category==='unavailable');
});
test('B0374: observation record is evidence-only without any automatic clinical inference',async()=>{
 const f=fixture();const desk=new TriageObservationDesk(f.tasks);
 await desk.record({id:U(11),code:'pulse',value:'89',unit:'bpm',takenAt:'2026-10-31T10:01:00Z'});
 assert.equal(f.calls[0].action,'OBSERVATION_RECORD');assert.equal(f.calls[0].payload.observation.value,'89');
 await assert.rejects(desk.record({id:U(12),code:'pulse',value:'89',unit:'bpm',takenAt:'2026-10-31T10:01:00Z',replaces:U(11)}));
});
test('B0374: correction requires original observation and human rationale',async()=>{
 const f=fixture();const desk=new TriageObservationDesk(f.tasks);
 await assert.rejects(desk.correct({id:U(12),code:'pulse',value:'90',unit:'bpm',takenAt:'2026-10-31T10:01:00Z'},'typo'));
 await assert.rejects(desk.correct({id:U(12),code:'pulse',value:'90',unit:'bpm',takenAt:'2026-10-31T10:01:00Z',replaces:U(11)},''));
 await desk.correct({id:U(12),code:'pulse',value:'90',unit:'bpm',takenAt:'2026-10-31T10:01:00Z',replaces:U(11)},'Corrected source');
 assert.equal(f.calls[0].action,'OBSERVATION_CORRECTION');assert.equal(f.calls[0].payload.rationale,'Corrected source');
});
test('B0374: declined measurement and device evidence have explicit structured operations',async()=>{
 const f=fixture(),desk=new TriageObservationDesk(f.tasks);await desk.declined('Patient declined');await desk.deviceSource('Device model X');
 assert.deepEqual(f.calls.map(x=>x.action),['MEASUREMENT_DECLINED','ASSESSMENT_DEVICE_SOURCE']);
});
test('B0375: queue movement is recorded as a reference, not a station name inferred from UI',async()=>{
 const f=fixture(),desk=new TriageQueueDesk(f.tasks);await desk.enqueue(U(17));await desk.move(U(18));
 assert.deepEqual(f.calls.map(x=>x.payload.reference),[U(17),U(18)]);
 await assert.rejects(desk.move('unverified-station'));
});
test('B0375: queue call, recall and return are separately persisted operations',async()=>{
 const f=fixture(),desk=new TriageQueueDesk(f.tasks);await desk.call();await desk.recall();await desk.returnToQueue();
 assert.deepEqual(f.calls.map(x=>x.action),['QUEUE_CALL','QUEUE_RECALL','QUEUE_RETURN']);
});
test('B0375: queue pause and unavailable require a user rationale',async()=>{
 const f=fixture(),desk=new TriageQueueDesk(f.tasks);
 assert.throws(()=>desk.pause(''));assert.throws(()=>desk.unavailable(''));
 await desk.pause('Waiting for authorized attendant');await desk.unavailable('Not physically present');
 assert.deepEqual(f.calls.map(x=>x.action),['QUEUE_PAUSE','QUEUE_PATIENT_UNAVAILABLE']);
});
test('B0376: clinician assignment and reassignment are scoped reference writes',async()=>{
 const f=fixture(),desk=new TriageHandoverDesk(f.tasks);
 await desk.assign(U(20));assert.throws(()=>desk.reassign(U(21),''));await desk.reassign(U(21),'Clinician change');
 assert.deepEqual(f.calls.map(x=>x.payload.reference),[U(20),U(21)]);
});
test('B0376: handover records request, note, destination and finalize as separate acknowledged commands',async()=>{
 const f=fixture(),desk=new TriageHandoverDesk(f.tasks);
 await desk.request();await desk.note('Handover summary provided');await desk.destination('Receiving unit confirmed');await desk.finalize();
 assert.deepEqual(f.calls.map(x=>x.action),['HANDOVER_REQUEST','HANDOVER_NOTE','HANDOVER_DESTINATION','HANDOVER_FINALIZED']);
});
test('B0376: receiving party can accept or reject with an explicit reason',async()=>{
 const f=fixture(),desk=new TriageHandoverDesk(f.tasks);await desk.receive();assert.throws(()=>desk.reject(''));
 await desk.reject('Receiving team not available');await desk.acknowledge();
 assert.deepEqual(f.calls.map(x=>x.action),['HANDOVER_ACCEPT','HANDOVER_REJECT','RECEIVER_ACKNOWLEDGEMENT']);
});
test('B0377: escalation documents explicit human rationale, acknowledgement and resolution',async()=>{
 const f=fixture(),desk=new TriageEscalationDesk(f.tasks);
 assert.throws(()=>desk.raise(''));await desk.raise('Clinician concern recorded');await desk.acknowledge();
 await desk.resolve('Clinician reassessed');
 assert.deepEqual(f.calls.map(x=>x.action),['ESCALATION_RAISED','ESCALATION_ACKNOWLEDGED','ESCALATION_RESOLVED']);
});
test('B0377: reassessment lifecycle never calculates patient priority',async()=>{
 const f=fixture(),desk=new TriageEscalationDesk(f.tasks);
 await desk.requestReassessment();await desk.startReassessment();await desk.documentReassessment('Clinician assessment');await desk.completeReassessment();
 assert.deepEqual(f.calls.map(x=>x.action),['REASSESSMENT_REQUESTED','REASSESSMENT_STARTED','REASSESSMENT_DOCUMENTED','REASSESSMENT_COMPLETED']);
});
test('B0378: safety holds cannot be placed or released without rationale',async()=>{
 const f=fixture(),desk=new TriageSafetyDesk(f.tasks);
 assert.throws(()=>desk.hold(''));assert.throws(()=>desk.release(''));
 await desk.hold('Clinician request');await desk.release('Clinical team reviewed');
 assert.deepEqual(f.calls.map(x=>x.action),['SAFETY_HOLD','SAFETY_HOLD_RELEASE']);
});
test('B0378: signed assessment and addendum must hold sign grant',async()=>{
 const f=fixture(['triage:assess']),desk=new TriageSafetyDesk(f.tasks);
 await desk.startAssessment();await desk.narrative('Clinician-entered narrative');
 await assert.rejects(desk.attest(),e=>e.category==='denied');await assert.rejects(desk.addendum('Correction'));
 assert.equal(f.calls.length,2);
});
test('B0378: signed assessment operation uses server permission and no prefilled clinical text',async()=>{
 const f=fixture(['triage:sign']),desk=new TriageSafetyDesk(f.tasks);
 await desk.attest();await desk.addendum('Clinician authenticated addendum');
 assert.deepEqual(f.calls.map(x=>x.action),['ASSESSMENT_ATTEST','ASSESSMENT_ADDENDUM']);
});
