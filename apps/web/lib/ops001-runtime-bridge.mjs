/**
 * SYNQ-OPS-001 — server process integration for the first eight shared screens.
 *
 * An infrastructure owner must explicitly install a verified, tenant-scoped
 * P03 identity runtime during trusted server initialization, after vault,
 * PostgreSQL role/RLS and policy attestation. No demo IdP, auto-login, or
 * request-supplied runtime factory. Until installed, all routes fail closed.
 */
import {assertVerifiedStaffIdentityRuntime} from "./staff-identity-runtime.mjs";
import {createVerifiedOps001Lookup} from "./ops001-patient-lookup.mjs";
import {requireClinicalIdentitySafety} from "./clinical-identity-safety.mjs";

let configured=null;
const SAFE={"Cache-Control":"no-store, private","Pragma":"no-cache",
 "Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff",
 "Content-Type":"application/json; charset=utf-8"};
const deny=(status,code)=>new Response(JSON.stringify({status,code}),{status,headers:SAFE});
export class Ops001RuntimeInstallationError extends Error {
 constructor(){super("ops001_runtime_not_verified");this.name="Ops001RuntimeInstallationError";}
}
const fail=()=>{throw new Ops001RuntimeInstallationError();};

export function installOps001VerifiedRuntime(runtime,env,options={}){
 if(configured!==null)fail();
 try{assertVerifiedStaffIdentityRuntime(runtime);requireClinicalIdentitySafety(env);}
 catch{fail();}
 if(!runtime.http||typeof runtime.http.start!=="function"||
    typeof runtime.http.callback!=="function"||typeof runtime.http.logout!=="function")fail();
 const lookup=createVerifiedOps001Lookup(runtime,env,options);
 configured=Object.freeze({identity:runtime.http,lookup});
 return Object.freeze({ready:true});
}
export function ops001RuntimeStatus(){
 return Object.freeze({ready:configured!==null});
}
/** No untrusted capability, patient identifier or bearer in public service status. */
export function ops001PublicStatusResponse(){
 return new Response(JSON.stringify({ready:configured!==null,clinicalRelease:false}),{
  status:configured===null?503:200,headers:SAFE
 });
}
export async function ops001ServerAction(action,request){
 const c=configured;
 if(c===null)return deny(503,"verified_staff_service_not_deployed");
 try{
  if(action==="login"&&request?.method==="GET")return await c.identity.start(request);
  if(action==="callback"&&request?.method==="GET")return await c.identity.callback(request);
  if(action==="logout"&&request?.method==="POST")return await c.identity.logout(request);
  if(action==="patient-lookup"&&request?.method==="POST")return await c.lookup.lookup(request);
  return deny(405,"method_not_allowed");
 }catch{return deny(503,"verified_staff_service_unavailable");}
}
