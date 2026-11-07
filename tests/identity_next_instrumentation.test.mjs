import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dispatchVerifiedStaffAuthRoute} from "../apps/web/lib/verified-staff-auth-route.mjs";
test("B0072 Next startup is Node-only; no per-request secret or runtime creation",()=>{
 const source=readFileSync(new URL("../apps/web/instrumentation.ts",import.meta.url),"utf8");
 assert.match(source,/export async function register\(\)/);
 assert.match(source,/NEXT_RUNTIME!=="nodejs"/);
 assert.match(source,/initializeProductionStaffIdentity\(\)/);
 assert.doesNotMatch(source,/request:|localStorage|Bearer |window\.|SYNQORIA_IDENTITY_DB_/);
});
test("B0072 process startup absent still denies all three auth routes with private 503",async()=>{
 for(const operation of ["start","callback","logout"]){
  const request=new Request(
   "https://staff.synqoria.org/api/auth/"+(operation==="logout"?"logout":"oidc/"+operation),
   {method:operation==="logout"?"POST":"GET"}
  );
  const res=await dispatchVerifiedStaffAuthRoute(operation,request);
  assert.equal(res.status,503);assert.equal(res.headers.get("set-cookie"),null);
  assert.equal(res.headers.get("cache-control"),"no-store, private");
 }
});
