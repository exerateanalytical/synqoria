/**
 * SYNQORIA P03-B0064: explicit, verified, server-only Next.js identity bridge.
 * This process-local binding does not create credentials, DB connections or
 * authorize users. Deployment must bootstrap a verified runtime from trusted
 * TLS DB clients/IdP configuration before enabling the auth API routes.
 * A missing bootstrap always returns a private 503, never demo identity.
 */
import {assertVerifiedStaffIdentityRuntime}
 from "./staff-identity-runtime.mjs";
const SAFE_HEADERS=Object.freeze({
 "Cache-Control":"no-store, private",
 "Pragma":"no-cache",
 "Referrer-Policy":"no-referrer",
 "X-Content-Type-Options":"nosniff"
});
export class StaffRouteBootstrapError extends Error{
 constructor(){super("staff_route_bootstrap_denied");this.name="StaffRouteBootstrapError";}
}
const deny=()=>{throw new StaffRouteBootstrapError();};
let installed=null;

export function bindVerifiedStaffRouteRuntime(runtime){
 try{assertVerifiedStaffIdentityRuntime(runtime);}catch{deny();}
 if(!runtime?.http||!["start","callback","logout"].every(x=>typeof runtime.http[x]==="function"))deny();
 if(installed!==null&&installed!==runtime)deny();
 installed=runtime;
 return Object.freeze({bound:true});
}
export function staffRouteNotReady(){
 return new Response(null,{status:503,headers:SAFE_HEADERS});
}
export async function dispatchVerifiedStaffAuthRoute(operation,request){
 if(!["start","callback","logout"].includes(operation)||!request||typeof request.url!=="string")deny();
 const current=installed;
 if(current===null)return staffRouteNotReady();
 try{
  const answer=await current.http[operation](request);
  if(!(answer instanceof Response)||![200,204,303,401,403,405,503].includes(answer.status))deny();
  return answer;
 }catch{
  return staffRouteNotReady();
 }
}
/** No production route is allowed to call this reset function. For tests,
 * isolate Node modules in a fresh process rather than disabling a live binding.
 */
