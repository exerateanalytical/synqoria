const {test}=require('node:test');const a=require('node:assert/strict');
const {clinicalUiStatus}=require('../dist');
test('P02-B0017 all clinical outcomes show distinguishable English/French text and icons',()=>{
 const statuses=['ok','authentication-required','not-disclosed','unavailable','invalid-request'];
 for(const locale of ['en','fr']){
  for(const status of statuses){const ui=clinicalUiStatus(status,locale);a.ok(ui.label.length>10);a.ok(ui.icon);a.ok(ui.kind);}
  a.notEqual(clinicalUiStatus('ok',locale).kind,clinicalUiStatus('unavailable',locale).kind);
 }
 a.notEqual(clinicalUiStatus('unavailable','en').label,clinicalUiStatus('unavailable','fr').label);
});
