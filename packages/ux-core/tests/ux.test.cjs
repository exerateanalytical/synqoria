const test = require('node:test');
const assert = require('node:assert/strict');
const { colors, contrastRatio, approvedTextPairs, workspaces, allowedWorkspaces, staffMobileTabs, patientMobileTabs, requestPatientSwitch, syncOutcomeLabels } = require('../dist');
test('approved colour pairs meet 4.5:1 text contrast', () => {
  for (const [fg,bg] of approvedTextPairs) assert.ok(contrastRatio(fg,bg)>=4.5,`${fg} on ${bg}`);
});
test('design tokens preserve approved palette',()=>{
  assert.equal(colors.canvas,'#F7F5F0');assert.equal(colors.navy,'#073656');assert.equal(colors.primary,'#1664D4');
});
test('navigation only includes licensed and granted stable workspace roots',()=>{
  const caps=new Set(['workspace:patients','workspace:clinical','platform:cells']);
  const licensed=new Set(['M01','M03','M39']);
  assert.deepEqual(allowedWorkspaces({shell:'workforce',grantedCapabilities:caps,licensedModules:licensed}).map(x=>x.id),['clinical-work','patients']);
  assert.deepEqual(allowedWorkspaces({shell:'portal',grantedCapabilities:caps,licensedModules:licensed}),[]);
  assert.deepEqual(allowedWorkspaces({shell:'platform',grantedCapabilities:caps,licensedModules:licensed}).map(x=>x.id),['cells']);
  assert.deepEqual(allowedWorkspaces({shell:'workforce',grantedCapabilities:new Set(),licensedModules:licensed}),[]);
});
test('sidebar roots contain no details/edit/create routes or duplicate paths',()=>{
  assert.equal(new Set(workspaces.map(x=>x.href)).size,workspaces.length);
  assert.ok(workspaces.every(x=>!/(\/new|\/edit|\/detail|\/review|\/dialog|\/\[)/.test(x.href)));
});
test('native mobile navigation has four labelled destinations and More',()=>{
  for (const tabs of [staffMobileTabs,patientMobileTabs]) {
    assert.equal(tabs.length,4);assert.equal(tabs[3].id,'more');
    assert.ok(tabs.every(t=>t.labelEn && t.labelFr));
  }
});
test('unsaved patient switch requires decision; clean switch reauthorizes and clears cache',()=>{
  assert.equal(requestPatientSwitch({hasUnsavedWork:true}).kind,'confirm-unsaved');
  assert.deepEqual(requestPatientSwitch({hasUnsavedWork:false}),{kind:'reauthorize',clearPatientCaches:true,clearSensitiveTelemetry:true});
});
test('synchronization labels distinguish outcome unknown from confirmed success',()=>{
  assert.equal(Object.keys(syncOutcomeLabels).length,5);
  assert.notEqual(syncOutcomeLabels['outcome-unknown'].en,syncOutcomeLabels.synchronized.en);
});
