const test=require('node:test');const assert=require('node:assert/strict');
const {criticalTextSafety,FacilitySwitchBarrier,proxyMayShowRecord,safeClinicalRouteRoot,registrationReleasePresentation}=require('../dist');
test('P02-B0053 critical field text never truncates and touch targets remain safe',()=>{
 for(const field of ['drug','dose','allergy'])for(const scale of [1,2,3]){const x=criticalTextSafety(field,scale);assert.equal(x.minTouchTarget,48);assert.equal(x.numberOfLines,undefined);assert.equal(x.allowFontScaling,true);assert.equal(x.flexShrink,1);assert.equal(x.flexWrap,'wrap');assert.equal(x.lineHeight,undefined);}
 assert.equal(criticalTextSafety('dose',NaN),null);assert.equal(criticalTextSafety('dose',0),null);
});
test('P02-B0054 facility switch revokes access before clear and requires matching new generation',()=>{
 const x=new FacilitySwitchBarrier();let observed=true;const generation=x.beginSwitch(()=>{observed=x.mayDisplayClinicalData;});assert.equal(observed,false);
 assert.equal(x.confirmNewScope(generation-1,true,true),false);assert.equal(x.confirmNewScope(generation,true,false),false);
 assert.equal(x.confirmNewScope(generation,true,true),true);x.revoke();assert.equal(x.mayDisplayClinicalData,false);
 const next=x.beginSwitch(()=>{throw Error('clear failed')});assert.equal(x.mayDisplayClinicalData,false);assert.equal(x.confirmNewScope(next,true,true),false);assert.ok(next>generation);
});
test('P02-B0055 proxy grant must match exact patient, facility, proxy, expiry and scope',()=>{
 const p='patient_12345678',u='proxy_12345678',f='facility_12345678';const g={patientId:p,proxyId:u,facilityId:f,expiresAt:200,scopes:['patient:read:proxy'],revoked:false};
 assert.equal(proxyMayShowRecord(g,p,u,f,100,true),true);
 for(const [grant,patient,proxy,facility,now,online] of [[null,p,u,f,100,true],[g,p,u,f,200,true],[g,p,u,f,100,false],[g,p+'x',u,f,100,true],[g,p,u+'x',f,100,true],[g,p,u,f+'x',100,true],[{...g,revoked:true},p,u,f,100,true],[{...g,scopes:[]},p,u,f,100,true],[{...g,scopes:null},p,u,f,100,true]])assert.equal(proxyMayShowRecord(grant,patient,proxy,facility,now,online),false);
});
test('P02-B0056 only authorized static roots can appear as sidebar destinations',()=>{
 for(const [s,r] of [['workforce','/workforce'],['portal','/portal'],['platform','/platform']])assert.equal(safeClinicalRouteRoot(s,r,true),r);
 for(const r of ['/workforce/patients/123','/workforce?patient=123','//evil','/workforce/%2e%2e','/workforce#x',null,{}])assert.equal(safeClinicalRouteRoot('workforce',r,true),null);
 assert.equal(safeClinicalRouteRoot('workforce','/workforce',false),null);assert.equal(safeClinicalRouteRoot('invalid','/workforce',true),null);
});
test('P02-B0057 every registration state remains non-actionable in EN and FR',()=>{
 for(const locale of ['en','fr'])for(const gate of ['identity','database','clinical','offline']){
  const x=registrationReleasePresentation(locale,gate);assert.equal(x.status,'blocked');assert.equal(x.canSubmit,false);assert.equal(x.canSave,false);assert.equal(x.canApprove,false);assert.equal(x.ariaLive,'polite');assert.ok(x.reason.length>15);
 }
 assert.notEqual(registrationReleasePresentation('en','offline').reason,registrationReleasePresentation('fr','offline').reason);
});
