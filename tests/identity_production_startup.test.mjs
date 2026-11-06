import test from "node:test";
import assert from "node:assert/strict";
import {initializeProductionStaffIdentity,IdentityStartupError}
 from "../apps/web/lib/staff-identity-production-startup.mjs";
import {dispatchVerifiedStaffAuthRoute}
 from "../apps/web/lib/verified-staff-auth-route.mjs";
test("B0071 deployment starts disabled without touching pg, IdP or clinical data",async()=>{
 let loaded=0,visited=0;
 const result=await initializeProductionStaffIdentity({env:{},loadPg:()=>{loaded++;throw Error("unsafe");},
 fetcher:()=>{visited++;throw Error("unsafe");}});
 assert.deepEqual(result,{enabled:false,ready:false});
 assert.equal(loaded,0);assert.equal(visited,0);
 const response=await dispatchVerifiedStaffAuthRoute("start",
   new Request("https://staff.synqoria.org/api/auth/oidc/start"));
 assert.equal(response.status,503);assert.equal(response.headers.get("set-cookie"),null);
});
test("B0071 forbidden feature flag fails closed, no arbitrary bootstrap bypass",async()=>{
 await assert.rejects(()=>initializeProductionStaffIdentity({
  env:{SYNQORIA_IDENTITY_BOOTSTRAP_ENABLED:"yes"},
  loadPg:()=>({Pool:class {}})}),IdentityStartupError);
 const response=await dispatchVerifiedStaffAuthRoute("callback",
   new Request("https://staff.synqoria.org/api/auth/oidc/callback?code=hostile"));
 assert.equal(response.status,503);
});
test("B0071 enabled but missing production safety gates denies before DB/transport",async()=>{
 let loaded=0,visited=0;
 await assert.rejects(()=>initializeProductionStaffIdentity({
  env:{SYNQORIA_IDENTITY_BOOTSTRAP_ENABLED:"1"},
  loadPg:()=>{loaded++;return {};},
  fetcher:()=>{visited++;}
 }),IdentityStartupError);
 assert.equal(loaded,0);assert.equal(visited,0);
 await assert.rejects(()=>initializeProductionStaffIdentity({
  env:{SYNQORIA_IDENTITY_BOOTSTRAP_ENABLED:"1"},
  loadPg:()=>{loaded++;return {};}
 }),IdentityStartupError);
 assert.equal(loaded,0);
});
