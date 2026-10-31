const {test}=require('node:test');
const assert=require('node:assert/strict');
const {nativeMoreItems,nativeMoreCanOpen,nativeMoreCanExecute}=require('../dist/index.js');
test('P02-B0086: native More is local-only, role/locale constrained',()=>{
 for(const role of ['staff','patient'])for(const locale of ['en','fr']){
  const items=nativeMoreItems(role,locale);
  assert.equal(items.length,3);assert.equal(new Set(items.map(i=>i.id)).size,3);
  assert.ok(items.every(i=>i.requiresSession===false));
  assert.ok(items.every(i=>i.label.length>0&&i.hint.length>0));
 }
 assert.deepEqual(nativeMoreItems('admin','en'),[]);
 assert.deepEqual(nativeMoreItems('patient','es'),[]);
 for(const tab of ['home','patients','tasks','records','work','appointments'])assert.equal(nativeMoreCanOpen(tab,false),false);
 assert.equal(nativeMoreCanOpen('more',false),true);
 for(const action of ['registration','patient_search','prescribe','review_approval','clinical_write','deep_link'])assert.equal(nativeMoreCanExecute(action),false);
});
