const {test}=require('node:test');const assert=require('node:assert/strict');
const {routeDecision,validateRouteRegistry}=require('../dist');
const entry={screenId:'M01-S01',module:'M01',route:'/workforce/patients/register',shell:'workforce',implementationStatus:'tested',capability:'patient:create'};
const ctx={shell:'workforce',capabilities:new Set(['patient:create']),licensedModules:new Set(['M01'])};
test('canonical detail surface only on exact registered independently tested screen',()=>{
 assert.equal(routeDecision(entry.route,[entry],ctx),'available-in-ui');
 for(const path of ['/workforce/patients/register/123','/workforce/patients/register?x=1','//workforce/patients/register','/workforce/patients'])
  assert.equal(routeDecision(path,[entry],ctx),'not-delivered');
});
test('unimplemented screens cannot masquerade as routable pages',()=>{
 assert.equal(routeDecision(entry.route,[{...entry,implementationStatus:'implemented'}],ctx),'not-delivered');
 assert.equal(routeDecision(entry.route,[{...entry,implementationStatus:'not_implemented'}],ctx),'not-delivered');
 assert.equal(routeDecision(entry.route,[{...entry,route:null}],ctx),'not-delivered');
 assert.equal(routeDecision(entry.route,[entry,entry],ctx),'not-delivered');
});
test('wrong shell, missing capability and license fail closed',()=>{
 assert.equal(routeDecision(entry.route,[entry],{...ctx,shell:'portal'}),'not-delivered');
 assert.equal(routeDecision(entry.route,[entry],{...ctx,capabilities:new Set()}),'not-authorized');
 assert.equal(routeDecision(entry.route,[entry],{...ctx,licensedModules:new Set()}),'not-authorized');
});
test('registry validator rejects duplicate routes, duplicate IDs and fictitious path mappings',()=>{
 assert.equal(validateRouteRegistry([entry]),true);
 assert.equal(validateRouteRegistry([{...entry,implementationStatus:'not_implemented',route:null}]),true);
 assert.equal(validateRouteRegistry([entry,entry]),false);
 assert.equal(validateRouteRegistry([entry,{...entry,screenId:'M01-S02'}]),false);
 assert.equal(validateRouteRegistry([{...entry,implementationStatus:'not_implemented'}]),false);
});
