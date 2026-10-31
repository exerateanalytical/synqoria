const {test}=require('node:test');const assert=require('node:assert/strict');const {planActions}=require('../dist');
const actions=[{id:'patient-safety',label:'Check severe allergy',priority:'critical'},{id:'save',label:'Save for subsequent verification',priority:'primary'},{id:'print',label:'Print summary',priority:'secondary'}];
test('all tested mobile widths keep critical actions without squeeze',()=>{
 for(const width of [320,360,390,412,768,1024,1440,1920]){
  const p=planActions(actions,width,width===320?2:1);assert.ok(p.alwaysVisible.some(x=>x.id==='patient-safety'));
  assert.equal(p.wrapLabels,true);assert.equal(p.minTargetPx,48);assert.ok(p.columns===1||p.columns===2);
 }
});
test('compact layout moves only secondary actions into More',()=>{
 const p=planActions(actions,320,2);assert.equal(p.columns,1);assert.deepEqual(p.overflow.map(a=>a.id),['print']);
 assert.equal(planActions(actions,1920).overflow.length,0);
});
test('invalid action IDs and dimensions are rejected',()=>{
 assert.throws(()=>planActions([...actions,actions[0]],390));assert.throws(()=>planActions(actions,0));assert.throws(()=>planActions(actions,390,0.5));
});
