import test from "node:test";
import assert from "node:assert/strict";
import {parseStaffRolePolicy,verifyRoleBoundWorkforceGrant,StaffRolePolicyError} from "../apps/web/lib/staff-role-capability-policy.mjs";
const tenant="11111111-1111-4111-8111-111111111111";
const json=JSON.stringify({tenantId:tenant,version:12,roles:{nurse:["patient:view","facility:view"],physician:["patient:view","encounter:write"]}});
const policy=parseStaffRolePolicy(json,tenant);
test("B0062 verified tenant role ceiling permits only its declared capabilities",()=>{
 const grant={tenantId:tenant,role:"nurse",capabilities:["patient:view"]};
 assert.equal(verifyRoleBoundWorkforceGrant(grant,policy),grant);
 assert.equal(policy.version,12);
 assert.equal(Object.isFrozen(policy.roles),true);
});
test("B0062 unexpected role and capability cannot escalate staff privileges",()=>{
 for(const g of [
  {tenantId:tenant,role:"nurse",capabilities:["encounter:write"]},
  {tenantId:tenant,role:"unknown",capabilities:["patient:view"]},
  {tenantId:"22222222-2222-4222-8222-222222222222",role:"nurse",capabilities:["patient:view"]}
 ])assert.throws(()=>verifyRoleBoundWorkforceGrant(g,policy),StaffRolePolicyError);
});
test("B0062 missing tenant, duplicate or unsupported role grants reject matrix",()=>{
 for(const bad of [
  {tenantId:tenant,version:0,roles:{nurse:["patient:view"]}},
  {tenantId:tenant,version:12,roles:{nurse:["patient:view","patient:view"]}},
  {tenantId:tenant,version:12,roles:{nurse:[]}}
 ])assert.throws(()=>parseStaffRolePolicy(JSON.stringify(bad),tenant),StaffRolePolicyError);
});
