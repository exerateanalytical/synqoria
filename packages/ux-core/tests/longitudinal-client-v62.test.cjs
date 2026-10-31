const {test}=require('node:test');
const assert=require('node:assert/strict');
const {longitudinalClient,clinicalNoteIndex,clinicalObservationLineage}=require('../dist');
const U=n=>`10000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const scope={patientId:U(3),encounterId:U(5),revision:2};
const goodIndex={notes:[{id:U(8),status:'DRAFT',title:'Synthetic case note',confidential:false}],nextId:null};
const original={id:U(9),code:'local:pulse',value:'80',unit:'/min',measuredAt:'2026-10-31T10:00:00Z',status:'CORRECTED',replaces:null};
const updated={...original,id:U(10),value:'81',measuredAt:'2026-10-31T10:02:00Z',status:'CAPTURED',replaces:U(9)};
const goodLineage={lineage:[original,updated]};
const response=(value,status=200)=>({status,headers:{get:k=>k==='Cache-Control'?'no-store, private':k==='Content-Type'?'application/json; charset=utf-8':null},json:async()=>value});
const fixture=(fetch)=>longitudinalClient('https://synqoria.example/',{online:()=>true,token:async()=> 'VERIFIED_SYNTHETIC_SESSION_TOKEN_1234',fetch});
test('B0091: note index hides free text and requires exact bounded fields',()=>{
 assert.equal(clinicalNoteIndex(goodIndex).notes.length,1);
 assert.throws(()=>clinicalNoteIndex({...goodIndex,patientName:'injected'}));
 assert.throws(()=>clinicalNoteIndex({...goodIndex,notes:[{...goodIndex.notes[0],body:'PHI'}]}));
 assert.throws(()=>clinicalNoteIndex({...goodIndex,notes:[goodIndex.notes[0],goodIndex.notes[0]]}));
 assert.throws(()=>clinicalNoteIndex({...goodIndex,nextId:U(3)}));
});
test('B0092: correction lineage requires selected observation, full parents and stable chronology',()=>{
 assert.equal(clinicalObservationLineage(goodLineage,U(10)).lineage.length,2);
 assert.throws(()=>clinicalObservationLineage(goodLineage,U(11)));
 assert.throws(()=>clinicalObservationLineage({lineage:[updated]},U(10)));
 assert.throws(()=>clinicalObservationLineage({lineage:[updated,original]},U(10)));
 assert.throws(()=>clinicalObservationLineage({lineage:[original,updated,{...updated,id:U(12),note:'PHI'}]},U(10)));
});
test('B0091: authorized scoped note index GET requires no-store and no client scope headers',async()=>{
 let sent;const c=fixture(async(url,opts)=>{sent={url,opts};return response(goodIndex);});
 const v=await c.noteIndex(scope,undefined,10);assert.equal(v.notes.length,1);
 assert.equal(sent.opts.method,'GET');assert.equal(sent.opts.credentials,'omit');
 assert.equal(sent.url,`https://synqoria.example/api/v1/clinical/patients/${scope.patientId}/encounters/${scope.encounterId}/note-index?limit=10`);
 assert.deepEqual(Object.keys(sent.opts.headers).sort(),['Accept','Authorization'].sort());
 await assert.rejects(c.noteIndex(scope,U(99),0),e=>e.kind==='invalid');
});
test('B0092: correction lineage scoped client rejects foreign/corrupt response',async()=>{
 const c=fixture(async()=>response(goodLineage));
 assert.equal((await c.observationLineage(scope,U(10))).lineage.length,2);
 await assert.rejects(c.observationLineage(scope,U(13)),e=>e.kind==='unavailable');
 await assert.rejects(c.observationLineage(scope,'noncanonical'),e=>e.kind==='invalid');
 const poisoned=fixture(async()=>response({...goodLineage,patientId:U(99)}));
 await assert.rejects(poisoned.observationLineage(scope,U(10)),e=>e.kind==='unavailable');
});
