const {test}=require('node:test');const a=require('node:assert/strict');
const {ClinicalApiClient}=require('../dist');
const id='123e4567-e89b-42d3-a456-426614174000',token='A_VALID_TEST_BEARER_12345';
test('P02-B0014 rejects insecure origin and malformed identifiers',async()=>{
 a.throws(()=>new ClinicalApiClient('http://clinical.example/'));a.throws(()=>new ClinicalApiClient('https://clinical.example/secret'));
 const client=new ClinicalApiClient('https://clinical.example/');a.equal((await client.readPatient('../all',token)).status,'invalid-request');
 a.equal((await client.readPatient(id,'short')).status,'invalid-request');
});
test('P02-B0014 backend status and validation govern clinical reads',async()=>{
 let options;
 const transport=async(url,opts)=>{options=opts;return new Response(JSON.stringify({patientId:id,identityStatus:'verified',revision:2}),{status:200,headers:{'content-type':'application/json'}})};
 const client=new ClinicalApiClient('https://clinical.example/',transport);
 a.equal((await client.readPatient(id,token)).status,'ok');a.equal(options.cache,'no-store');a.equal(options.credentials,'omit');a.equal(options.redirect,'error');
 for(const [status,expected] of [[401,'authentication-required'],[404,'not-disclosed'],[503,'unavailable']]){
  a.equal((await new ClinicalApiClient('https://clinical.example/',async()=>new Response('',{status})).readPatient(id,token)).status,expected);
 }
 a.equal((await new ClinicalApiClient('https://clinical.example/',async()=>new Response(JSON.stringify({patientId:'wrong',revision:1,identityStatus:'verified'}),{headers:{'content-type':'application/json'}})).readPatient(id,token)).status,'unavailable');
 a.equal((await new ClinicalApiClient('https://clinical.example/',async()=>{throw new Error('offline')}).readPatient(id,token)).status,'unavailable');
});
