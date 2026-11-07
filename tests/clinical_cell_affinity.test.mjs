import test from "node:test";
import assert from "node:assert/strict";
import {requireClinicalCellAffinity,ClinicalCellAffinityError} from "../apps/web/lib/clinical-cell-affinity.mjs";
import {readFacility} from "../apps/web/lib/clinical-cell.mjs";
const tenant="11111111-1111-4111-8111-111111111111",facility="22222222-2222-4222-8222-222222222222";
const other="33333333-3333-4333-8333-333333333333";
const staff={actorId:other,tenantId:tenant,facilityId:facility,
 capabilities:["facility:view"],sessionExpiresAt:Date.now()+300000,bearer:"VerifiedOpaqueToken0123456789012"};
const env={SYNQORIA_RUNTIME_MODE:"production",
 SYNQORIA_EXPECTED_TENANT_ID:tenant,SYNQORIA_EXPECTED_FACILITY_ID:facility,
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.synqoria.org/"};
const req=new Request("https://staff.synqoria.org/api/clinical/facility");
test("B0060 hospital cell identity bind is exact and immutable",()=>{
 const verified=requireClinicalCellAffinity(staff,env);
 assert.deepEqual(verified,{tenantId:tenant,facilityId:facility});
 assert.equal(Object.isFrozen(verified),true);
});
test("B0060 staff from another hospital, wrong facility and missing configured cell fail closed",()=>{
 for(const [context,settings] of [
  [{...staff,tenantId:other},env],
  [{...staff,facilityId:other},env],
  [staff,{...env,SYNQORIA_EXPECTED_TENANT_ID:""}],
  [staff,{...env,SYNQORIA_EXPECTED_FACILITY_ID:other}]
 ])assert.throws(()=>requireClinicalCellAffinity(context,settings),ClinicalCellAffinityError);
});
test("B0060 actual facility BFF rejects cross-tenant session before touching hospital cell",async()=>{
 let calls=0;
 const fetcher=async()=>{calls++;throw Error("unexpected network");};
 const result=await readFacility(req,{env,fetcher,
  sessionResolver:async()=>({...staff,tenantId:other})});
 assert.equal(result.status,404);
 assert.equal(calls,0);
});
test("B0060 actual facility BFF rejects configured-facility mismatch before upstream lookup",async()=>{
 let calls=0;
 const result=await readFacility(req,{env,fetcher:async()=>{calls++;throw Error("unexpected network");},
  sessionResolver:async()=>({...staff,facilityId:other})});
 assert.equal(result.status,404);assert.equal(calls,0);
});
