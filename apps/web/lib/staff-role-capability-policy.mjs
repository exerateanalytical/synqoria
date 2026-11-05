/**
 * SYNQORIA P03-B0062: server-owned hospital role/capability ceiling.
 *
 * A signed staff token and positive workforce response must still match the
 * hospital tenant's approved role matrix. Role names and grants from an IdP,
 * browser or roster cannot invent unrestricted powers or wildcard access.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const ROLE=/^[a-z][a-z0-9_-]{1,63}$/;
const CAP=/^[a-z][a-z0-9:._-]{2,63}$/;
export class StaffRolePolicyError extends Error{
 constructor(){super("staff_role_policy_denied");this.name="StaffRolePolicyError";}
}
const deny=()=>{throw new StaffRolePolicyError();};

export function parseStaffRolePolicy(source,expectedTenantId){
 if(typeof source!=="string"||source.length>16384||!UUID.test(expectedTenantId))deny();
 let input;
 try{input=JSON.parse(source);}catch{deny();}
 if(!input||typeof input!=="object"||Array.isArray(input)||
    Object.keys(input).sort().join(",")!=="roles,tenantId,version"||
    input.tenantId!==expectedTenantId||
    !Number.isSafeInteger(input.version)||input.version<1||input.version>2147483647||
    !input.roles||typeof input.roles!=="object"||Array.isArray(input.roles))deny();
 const names=Object.keys(input.roles);
 if(names.length<1||names.length>32||!names.every(x=>ROLE.test(x) &&
   !["constructor","prototype","__proto__"].includes(x)))deny();
 const roles=Object.create(null);
 for(const role of names){
  const caps=input.roles[role];
  if(!Array.isArray(caps)||caps.length<1||caps.length>64||
     new Set(caps).size!==caps.length||!caps.every(c=>typeof c==="string"&&CAP.test(c)))deny();
  roles[role]=Object.freeze([...caps]);
 }
 return Object.freeze({tenantId:expectedTenantId,version:input.version,
  roles:Object.freeze(roles)});
}
export function verifyRoleBoundWorkforceGrant(grant,policy){
 if(!grant||!policy||typeof policy!=="object"||grant.tenantId!==policy.tenantId||
    !Number.isSafeInteger(policy.version)||policy.version<1||
    !Array.isArray(grant.capabilities)||!ROLE.test(grant.role)||
    !policy.roles||!Object.prototype.hasOwnProperty.call(policy.roles,grant.role))deny();
 const allowed=policy.roles[grant.role];
 if(!Array.isArray(allowed)||grant.capabilities.length<1||
    grant.capabilities.some(c=>!allowed.includes(c)))deny();
 return grant;
}
