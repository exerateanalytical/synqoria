const {test}=require('node:test');
const assert=require('node:assert/strict');
const {triageDecodeWorklist,triageClient,TriageTransportError}=require('../dist');
const P='00000000-0000-0000-0000-000000000003';
const C='00000000-0000-0000-0000-000000000006';
const ST='00000000-0000-0000-0000-000000000005';
const CMD='11111111-1111-1111-1111-111111111111';
const row={caseId:C,stage:'WAITING',revision:1,station:ST,assignee:null,arrivedAt:'2026-10-30T14:30:00Z',paused:false,safetyHold:false,manualEscalation:false};
const valid=()=>({cases:[{...row}]});
const reject=(x)=>assert.throws(()=>triageDecodeWorklist(x,[C]),e=>e instanceof TriageTransportError&&e.category==='unavailable');
const scenarios={
 'P06-B0352':()=>{assert.deepEqual(triageDecodeWorklist(valid(),[C]).cases[0],row);reject({...valid(),patientName:'SECRET'});reject({cases:[{...row,diagnosis:'SECRET'}]});reject({cases:'wrong'});},
 'P06-B0353':()=>{reject({cases:[row,row]});reject({cases:[{...row,caseId:ST}]});assert.equal(triageDecodeWorklist({cases:[]},[C]).cases.length,0);},
 'P06-B0354':()=>{reject({cases:[{...row,stage:'UNKNOWN'}]});reject({cases:[{...row,stage:'CLOSED\nSECRET'}]});},
 'P06-B0355':()=>{for(const revision of [0,-1,2147483648,1.2,'2',null])reject({cases:[{...row,revision}]});},
 'P06-B0356':()=>{for(const station of ['bad',123,"AAAAAAAA-0000-0000-0000-000000000005",undefined])reject({cases:[{...row,station}]});assert.equal(triageDecodeWorklist({cases:[{...row,station:null}]},[C]).cases[0].station,null);},
 'P06-B0357':()=>{for(const assignee of ['bad',123,"AAAAAAAA-0000-0000-0000-000000000005",undefined])reject({cases:[{...row,assignee}]});assert.equal(triageDecodeWorklist({cases:[{...row,assignee:ST}]},[C]).cases[0].assignee,ST);},
 'P06-B0358':()=>{for(const arrivedAt of ['yesterday','2026-10-30T14:30:00+01:00',123,'2026-99-30T14:30:00Z'])reject({cases:[{...row,arrivedAt}]});},
 'P06-B0359':()=>{for(const paused of [0,1,'false',null])reject({cases:[{...row,paused}]});},
 'P06-B0360':()=>{for(const safetyHold of [0,1,'true',null])reject({cases:[{...row,safetyHold}]});},
 'P06-B0361':()=>{for(const manualEscalation of [0,1,'false',null])reject({cases:[{...row,manualEscalation}]});},
 'P06-B0362':async()=>{
  let status=201;
  const t={token:async()=> 'syntheticVerifiedToken_12345',isOnline:()=>true,
   fetch:async()=>({status,headers:{get:()=> 'no-store, private'},json:async()=>valid()})};
  const client=triageClient('https://hospital.example/',t);
  await assert.rejects(client.command({patientId:P,caseId:C,expectedRevision:1},CMD,'QUEUE_PAUSE'),e=>e instanceof TriageTransportError&&e.status===201);
  await assert.rejects(client.worklist(P,'FACILITY_QUEUE',[C]),e=>e instanceof TriageTransportError&&e.status===201);
  status=200;assert.equal((await client.worklist(P,'FACILITY_QUEUE',[C])).cases.length,1);
 }
};
for(const [id,fn] of Object.entries(scenarios))test(id+' strict M03 transport contract',fn);
