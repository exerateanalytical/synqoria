import test from "node:test";
import assert from "node:assert/strict";
import {resolveStaffWorkforceGrant,WorkforceAuthorizationError} from "../apps/web/lib/oidc-workforce-grants.mjs";
import {parseStaffRolePolicy} from "../apps/web/lib/staff-role-capability-policy.mjs";
const tenant="11111111-1111-4111-8111-111111111111";
const facility="22222222-2222-4222-8222-222222222222";
const issuer="https://identity.synqoria.org/realms/hospital";
const policy=parseStaffRolePolicy(JSON.stringify({tenantId:tenant,version:12,
 roles:{nurse:["patient:view","facility:view"]}}),tenant);
const dto={actorId:"33333333-3333-4333-8333-333333333333",tenantId:tenant,facilityId:facility,
 shiftId:"44444444-4444-4444-8444-444444444444",role:"nurse",status:"ACTIVE",
 issuer,subject:"staff-identity-001",revision:3,
 shiftStartsAt:"2026-11-05T18:00:00Z",shiftEndsAt:"2026-11-06T02:00:00Z",
 capabilities:["patient:view"]};
const run=grant=>resolveStaffWorkforceGrant({subject:dto.subject,
 accessScopes:["patient:view","facility:view","encounter:write"]},"aaa.bbb.ccc",
 {issuer,workforceOrigin:"https://workforce.synqoria-clinical.org/",
 expectedTenantId:tenant,expectedFacilityId:facility,rolePolicy:policy},{
 nowMs:Date.parse("2026-11-05T21:50:00Z"),
 fetcher:async()=>new Response(JSON.stringify(grant),{headers:{
  "Content-Type":"application/json","Cache-Control":"no-store"
 }})
});
test("B0062 positive signed scope also requires hospital-approved role policy",async()=>{
 const result=await run(dto);
 assert.equal(result.role,"nurse");
 assert.deepEqual(result.capabilities,["patient:view"]);
});
test("B0062 roster cannot add a clinician permission outside their role matrix",async()=>{
 await assert.rejects(()=>run({...dto,capabilities:["encounter:write"]}),WorkforceAuthorizationError);
 await assert.rejects(()=>run({...dto,role:"unapproved"}),WorkforceAuthorizationError);
});
