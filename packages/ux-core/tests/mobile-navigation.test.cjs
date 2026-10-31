const {test}=require('node:test');const assert=require('node:assert/strict');
const {mobileTabGates,requestMobileTab}=require('../dist');
test('both apps expose exactly four stable destinations with More at end',()=>{
 for(const shell of ['staff','patient']){const items=mobileTabGates(shell,false,new Set());
  assert.equal(items.length,4);assert.equal(items[3].id,'more');
  assert.equal(new Set(items.map(x=>x.id)).size,4);
  assert.ok(items.every(x=>x.reason==='sign-in-required'&&!x.enabled));}
});
test('only verified capabilities may unlock a tab after authenticated session',()=>{
 const caps=new Set(['ui:more','patient:list']);
 const staff=mobileTabGates('staff',true,caps);
 assert.deepEqual(staff.filter(x=>x.enabled).map(x=>x.id),['patients','more']);
 assert.equal(requestMobileTab('staff','work',true,caps).kind,'denied');
 assert.deepEqual(requestMobileTab('staff','patients',true,caps),{kind:'navigate',target:'patients'});
 assert.equal(requestMobileTab('patient','patients',true,caps).kind,'denied');
 assert.equal(requestMobileTab('staff','patients',false,caps).kind,'denied');
});
test('arbitrary paths and tab name injection fail closed',()=>{
 for(const target of ['/patients/123','work/extra','../../../work','patients?next=secret','\0'])
  assert.equal(requestMobileTab('staff',target,true,new Set(['staff:work','patient:list'])).kind,'denied');
});
