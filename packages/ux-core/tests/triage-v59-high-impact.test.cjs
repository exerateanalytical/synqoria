const {test}=require('node:test'),a=require('node:assert/strict');
const {TriageSensitiveConfirmation,triageAuditSearch,triageStaffReadFilter}=require('../dist');
const U=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
test('B0386: sensitive action needs exact operator confirmation and same case revision',async()=>{
 const calls=[],tasks={allowed:()=>true,perform:async(action,payload)=>{calls.push({action,payload});return {revision:5,eventDigest:'a'.repeat(64),replay:false};}};
 let ctx={caseId:U(1),revision:4},time=1000;
 const c=new TriageSensitiveConfirmation(tasks,()=>ctx,()=>time);const token=c.request('ASSESSMENT_ATTEST');
 a.equal(token.revision,4);a.equal(calls.length,0);
 const result=await c.confirm(token);a.equal(result.revision,5);a.equal(calls.length,1);
 await a.rejects(c.confirm(token));
});
test('B0386: patient switch cancels high-impact confirmation',async()=>{
 let ctx={caseId:U(1),revision:4};const tasks={allowed:()=>true,perform:async()=>{throw Error('must not write');}};
 const c=new TriageSensitiveConfirmation(tasks,()=>ctx,()=>1000),token=c.request('SAFETY_HOLD_RELEASE');
 ctx={caseId:U(2),revision:4};await a.rejects(c.confirm(token),e=>e.category==='conflict');
});
test('B0386: confirmation expires and revoked permissions suppress commands',async()=>{
 let time=10;let granted=true;let writes=0;
 const tasks={allowed:()=>granted,perform:async()=>{writes++;}};
 const c=new TriageSensitiveConfirmation(tasks,()=>({caseId:U(1),revision:1}),()=>time);
 const token=c.request('HANDOVER_FINALIZED');granted=false;await a.rejects(c.confirm(token));
 granted=true;time=31000;await a.rejects(c.confirm(token));a.equal(writes,0);
});
test('B0387: audit search filters only already authorized revision metadata',()=>{
 const events=[1,2,3,4].map(revision=>({revision,operation:revision%2?'QUEUE_CALL':'QUEUE_RECALL',digest:'a'.repeat(64),occurredAt:'2026-10-31T00:00:00Z',freeText:'MUST_NOT_ESCAPE'}));
 const v=triageAuditSearch(events,{operation:'QUEUE_CALL',fromRevision:2,toRevision:4});
 a.deepEqual(v.map(x=>x.revision),[3]);a.equal(JSON.stringify(v).includes('MUST_NOT_ESCAPE'),false);
});
test('B0387: audit search is bounded and rejects invalid free-text patterns',()=>{
 a.throws(()=>triageAuditSearch([],{operation:'<script>'}));
 a.throws(()=>triageAuditSearch([],{fromRevision:10,toRevision:5}));
 a.throws(()=>triageAuditSearch([],{operation:"QUEUE_CALL"},101));
});
test('B0388: facility queue filter requires bounded explicit case list, no global discovery',()=>{
 const v=triageStaffReadFilter('FACILITY_QUEUE',[U(1),U(2)]);
 a.deepEqual(v.caseIds,[U(1),U(2)]);a.equal(v.filter,undefined);
 a.throws(()=>triageStaffReadFilter('FACILITY_QUEUE',[]));
 a.throws(()=>triageStaffReadFilter('FACILITY_QUEUE',[U(1),U(1)]));
});
test('B0388: station and clinician worklist filters require explicit verified reference',()=>{
 const v=triageStaffReadFilter('STATION_QUEUE',[U(1)],{stationOrClinician:U(2)});
 a.equal(v.filter.stationOrClinician,U(2));
 a.throws(()=>triageStaffReadFilter('CLINICIAN_WORKLIST',[U(1)]));
 a.throws(()=>triageStaffReadFilter('STATION_QUEUE',[U(1)],{stationOrClinician:'from-browser-route'}));
});
test('B0388: time-window worklist rejects malformed timestamp or extra filters',()=>{
 const v=triageStaffReadFilter('ARRIVALS_SINCE',[U(1)],{after:'2026-10-31T00:00:00Z'});
 a.equal(v.filter.after,'2026-10-31T00:00:00Z');
 a.throws(()=>triageStaffReadFilter('ARRIVALS_SINCE',[U(1)],{after:'yesterday'}));
 a.throws(()=>triageStaffReadFilter('FACILITY_QUEUE',[U(1)],{after:'2026-10-31T00:00:00Z'}));
});
