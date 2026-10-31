const {test}=require('node:test');const assert=require('node:assert/strict');const {fieldDisplayPolicy}=require('../dist');
test('drug, dose, allergy, route, ID and critical instruction never truncate',()=>{
 for(const kind of ['medication','dose','route','allergy','identifier','clinical-instruction']){
  const x=fieldDisplayPolicy(kind,true);assert.equal(x.allowEllipsis,false);assert.equal(x.wrap,true);assert.equal(x.minFontPx,16);
 }
});
test('noncritical text ellipsis requires accessible expansion',()=>{
 assert.equal(fieldDisplayPolicy('noncritical-note',true).allowEllipsis,true);
 assert.equal(fieldDisplayPolicy('noncritical-note',false).allowEllipsis,false);
 assert.throws(()=>fieldDisplayPolicy('unknown',true));
});
