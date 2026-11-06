/**
 * SYNQORIA P03-B0068: explicit deployment-owned identity bootstrap.
 *
 * Never create staff identities from the browser or a fallback cookie.
 * Deployment injects three independently restricted TLS Postgres clients,
 * server-only secrets and trusted IdP/roster HTTP transport. Verify DB roles,
 * RLS predicates and clinical gates BEFORE binding any HTTP route.
 *
 * Failure does not install an unverified runtime. Bootstrap must be called
 * from an authorized server initializer, never from an HTTP request handler.
 */
import {createVerifiedStaffIdentityRuntime} from "./staff-identity-runtime.mjs";
import {bindVerifiedStaffRouteRuntime} from "./verified-staff-auth-route.mjs";

export class StaffRouteInitializationError extends Error{
 constructor(){super("staff_identity_initialization_denied");this.name="StaffRouteInitializationError";}
}
const deny=()=>{throw new StaffRouteInitializationError();};
let initialized=null;
let ongoing=null;

export async function initializeVerifiedStaffRoutes({
 env,replaySql,sessionSql,auditSql,fetcher,now=Date.now
}={}){
 if(initialized!==null)return initialized;
 if(ongoing!==null)return ongoing;
 if(!env||typeof env!=="object"||
    !replaySql||!sessionSql||!auditSql||
    replaySql===sessionSql||replaySql===auditSql||sessionSql===auditSql||
    typeof fetcher!=="function"||typeof now!=="function")deny();
 const promise=(async()=>{
  try{
   const runtime=await createVerifiedStaffIdentityRuntime({
    env,replaySql,sessionSql,auditSql,fetcher,now
   });
   bindVerifiedStaffRouteRuntime(runtime);
   initialized=runtime;
   return runtime;
  }catch{deny();}
 })();
 ongoing=promise;
 try{return await promise;}finally{if(ongoing===promise)ongoing=null;}
}
