const {test}=require('node:test');const a=require('node:assert/strict');
const {ClinicalApiClient,patientHttpPorts,PatientSwitchCoordinator}=require('../dist');
const id='123e4567-e89b-42d3-a456-426614174000';
test('P02-B0015 patient switch clears sensitive data and checks actual server response',async()=>{
 const events=[];
 const client=new ClinicalApiClient('https://clinical.example/',async(url)=>{events.push('server');return new Response(JSON.stringify({patientId:id,identityStatus:'verified',revision:1}),{headers:{'content-type':'application/json'}})});
 const ports=patientHttpPorts(client,()=> 'A_VALID_TEST_BEARER_12345',async()=>{events.push('clear')});
 const coordinator=new PatientSwitchCoordinator(ports);
 const state=coordinator.request(id);
 a.equal(state.stage,'reauthorizing');a.equal((await coordinator.executeReauthorization()).stage,'active');a.deepEqual(events,['clear','server']);
 coordinator.dispose();a.equal(coordinator.snapshot().activePatientId??null,null);
});
test('P02-B0015 denial must not activate patient context',async()=>{
 const client=new ClinicalApiClient('https://clinical.example/',async()=>new Response('',{status:404}));
 const coordinator=new PatientSwitchCoordinator(patientHttpPorts(client,()=> 'A_VALID_TEST_BEARER_12345',async()=>{}));
 coordinator.request(id);const state=await coordinator.executeReauthorization();a.equal(state.stage,'active');a.equal(state.patientId,null);
});
