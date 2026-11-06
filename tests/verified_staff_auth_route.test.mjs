import test from "node:test";
import assert from "node:assert/strict";
import {dispatchVerifiedStaffAuthRoute,bindVerifiedStaffRouteRuntime,StaffRouteBootstrapError}
 from "../apps/web/lib/verified-staff-auth-route.mjs";
const req=new Request("https://staff.synqoria.org/api/auth/oidc/start");
test("B0064 no bootstrapped verified identity runtime means 503 and no cookies",async()=>{
 const r=await dispatchVerifiedStaffAuthRoute("start",req);
 assert.equal(r.status,503);
 assert.equal(r.headers.get("set-cookie"),null);
 assert.equal(r.headers.get("cache-control"),"no-store, private");
});
test("B0064 fake or unverified identity runtime cannot be installed",()=>{
 assert.throws(()=>bindVerifiedStaffRouteRuntime({http:{
  start:async()=>new Response(null,{status:303,headers:{Location:"https://evil.example"}}),
  callback:async()=>new Response(null,{status:303}),
  logout:async()=>new Response(null,{status:204})
 }}),StaffRouteBootstrapError);
 assert.throws(()=>bindVerifiedStaffRouteRuntime(null),StaffRouteBootstrapError);
});
test("B0064 unknown operation never receives a generic identity runtime fallback",async()=>{
 await assert.rejects(()=>dispatchVerifiedStaffAuthRoute("issueDebugSession",req),StaffRouteBootstrapError);
});
