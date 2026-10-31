const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {triageActions,triageAction,triageCommandBody,triageReadBody,triageViewNames,triageClient,TriageTransportError}=require('../dist');
const P='00000000-0000-0000-0000-000000000003',C='00000000-0000-0000-0000-000000000006',
  ST='00000000-0000-0000-0000-000000000005',A='00000000-0000-0000-0000-000000000004',
  CMD='11111111-1111-1111-1111-111111111111',OBSID='22222222-2222-2222-2222-222222222222';
const scope={patientId:P,caseId:C,expectedRevision:1};
function data(a){switch(a.kind){case 'text':return {text:'clinician-provided evidence'};case 'reference':return {reference:ST};case 'observation':return {observation:{id:OBSID,code:'clinician-source-code',value:'37.2',unit:'recorded-unit',takenAt:'2026-10-30T14:30:00Z',...(a.action==='OBSERVATION_CORRECTION'?{replaces:ST}:{})}};default:return {};}}
const backend=fs.readFileSync(path.join(__dirname,'../../../services/clinical-core/src/main/java/org/synqoria/triage/TriageAuthenticatedCommandGateway.java'),'utf8');
const actionBranch=backend.split('UnaryOperator<TriageCase> action=switch(input.action()) {')[1].split('   default ->')[0];
const backendActions=[...actionBranch.matchAll(/case "([A-Z_]+)"/g)].map(m=>m[1]).sort();
test('catalog aligns exactly with 49 actual Java M03 mutation handlers',()=>{
 assert.equal(triageActions.length,49);
 assert.deepEqual(triageActions.map(a=>a.action).sort(),backendActions);
 assert.equal(new Set(triageActions.map(a=>a.action)).size,49);
});
for(const a of triageActions){
 test('transport contract: '+a.action,()=>{
  const body=triageCommandBody(a.action,scope,CMD,data(a));
  assert.equal(body.action,a.action);assert.equal(body.commandId,CMD);assert.equal(body.expectedRevision,1);
  assert.deepEqual(Object.keys(body).sort(),['commandId','expectedRevision','action',...(a.kind!=='none'?[a.kind]:[])].sort());
  assert.equal(body.tenantId,undefined);assert.equal(body.actorId,undefined);assert.equal(body.facilityId,undefined);
  assert.throws(()=>triageCommandBody(a.action,scope,CMD,{...data(a),bad:'attacker'}));
  assert.throws(()=>triageCommandBody(a.action,{...scope,patientId:ST+'evil'},CMD,data(a)));
  if(a.kind!=='none')assert.throws(()=>triageCommandBody(a.action,scope,CMD,{}));
 });
}
for(const view of triageViewNames){
 test('patient-scoped bounded worklist: '+view,()=>{
  const f=view==='STATION_QUEUE'?{stationOrClinician:ST}:view==='CLINICIAN_WORKLIST'?{stationOrClinician:A}:view==='ARRIVALS_SINCE'?{after:'2026-10-30T14:00:00Z'}:undefined;
  assert.equal(triageReadBody(view,[C],f).caseIds[0],C);
  assert.throws(()=>triageReadBody(view,[C,C],f));
  assert.throws(()=>triageReadBody(view,[],f));
  assert.throws(()=>triageReadBody(view,Array(51).fill(C),f));
  assert.throws(()=>triageReadBody(view,[ST+'evil'],f));
 });
}
test('observation correction requires lineage, not pseudo-medical inference',()=>{
 const a=triageAction('OBSERVATION_RECORD');assert.equal(a.capability,'triage:assess');
 assert.throws(()=>triageCommandBody('OBSERVATION_CORRECTION',scope,CMD,{observation:{...data(a).observation}}));
 assert.throws(()=>triageCommandBody('OBSERVATION_RECORD',scope,CMD,{observation:{...data(a).observation,replaces:ST}}));
});
test('client rejects offline clinical writes and never invokes network',async()=>{
 let calls=0;const t={token:async()=>{calls++;return 'syntheticVerifiedToken_12345'},fetch:async()=>{calls++;throw Error('network was called')},isOnline:()=>false};
 const client=triageClient('https://synqoria.example/',t);
 await assert.rejects(client.command(scope,CMD,'ASSESSMENT_BEGIN'),e=>e instanceof TriageTransportError&&e.category==='unavailable');assert.equal(calls,0);
});
test('network transport uses volatile bearer, no credentials, no-store, redirect denial',async()=>{
 let observed=null;
 const t={token:async()=> 'syntheticVerifiedToken_12345',isOnline:()=>true,
  fetch:async(url,init)=>{observed={url,init};return {status:200,headers:{get:k=>k.toLowerCase()==='cache-control'?'no-store, private':null},json:async()=>({revision:2,eventDigest:'a'.repeat(64),replay:false})}}};
 const client=triageClient('https://hospital.example/',t);
 const answer=await client.command(scope,CMD,'ARRIVAL_REASON',{text:'clinician-provided evidence'});
 assert.equal(answer.revision,2);assert.equal(answer.replay,false);
 assert.equal(observed.url,`https://hospital.example/api/v1/triage/patients/${P}/cases/${C}/commands`);
 assert.equal(observed.init.cache,'no-store');assert.equal(observed.init.credentials,'omit');assert.equal(observed.init.redirect,'error');
 assert.equal(observed.init.headers.Authorization,'Bearer syntheticVerifiedToken_12345');
 assert.ok(!observed.init.body.includes('tenantId'));assert.ok(!observed.init.body.includes('actorId'));
});
test('client rejects wrong network status and malformed receipts',async()=>{
 let status=401,json={revision:2,eventDigest:'a'.repeat(64),replay:false};
 const t={token:async()=> 'syntheticVerifiedToken_12345',isOnline:()=>true,
  fetch:async()=>({status,headers:{get:()=> 'no-store, private'},json:async()=>json})};
 const client=triageClient('https://hospital.example/',t);
 for(const [s,category] of [[401,'reauthenticate'],[404,'denied'],[409,'conflict'],[412,'conflict'],[422,'invalid'],[503,'unavailable']]){
  status=s;await assert.rejects(client.command(scope,CMD,'QUEUE_PAUSE'),e=>e.category===category);
 }
 status=200;json={revision:2,eventDigest:'bad',replay:false};
 await assert.rejects(client.command(scope,CMD,'QUEUE_PAUSE'),e=>e.category==='unavailable');
});
test('worklist path never includes case IDs or free text in URL',async()=>{
 let requested;
 const t={token:async()=> 'syntheticVerifiedToken_12345',isOnline:()=>true,
  fetch:async(u,options)=>{requested={u,options};return {status:200,headers:{get:()=> 'no-store, private'},json:async()=>({cases:[{caseId:C,stage:'WAITING',revision:1,station:ST,assignee:null,arrivedAt:'2026-10-30T14:30:00Z',paused:false,safetyHold:false,manualEscalation:false}]})}}};
 await triageClient('https://hospital.example/',t).worklist(P,'FACILITY_QUEUE',[C]);
 assert.ok(requested.u.includes(P)&&!requested.u.includes(C));assert.ok(requested.options.body.includes(C));
});
test('HTTP origin must be secure except local integration fixture',()=>{
 const t={token:async()=>'',isOnline:()=>false,fetch:async()=>{throw Error('no')}};
 for(const bad of ['http://hospital.example/','https://user:password@hospital.example/','https://hospital.example/path','https://hospital.example/?token=unsafe'])assert.throws(()=>triageClient(bad,t));
 assert.doesNotThrow(()=>triageClient('http://127.0.0.1:9999/',t));
});
