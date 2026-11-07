/**
 * SYNQORIA P03-B0056: revoke stale server-side sessions on failed live
 * introspection or roster/shift/role reauthorization.
 *
 * Repeated requests cannot keep a stale encrypted staff ticket alive after
 * an IdP or hospital authority denial. The failure path attempts atomic
 * revocation in the shared SQL store and always denies the request; a DB
 * outage never returns stale grants. No bearer/token is logged or returned.
 */
import {readOpaqueTicketCookie,reauthorizeClinicalStaffRequest}
 from "./oidc-session-reauthorization.mjs";

export class RevokedStaffAuthorizationError extends Error{
 constructor(){super("staff_session_reauthorization_denied");this.name="RevokedStaffAuthorizationError";}
}
const deny=()=>{throw new RevokedStaffAuthorizationError();};
export async function reauthorizeOrRevokeStaff(request,options={}){
 const store=options?.sessionStore;
 if(!store||typeof store.read!=="function"||typeof store.revoke!=="function")deny();
 const ref=readOpaqueTicketCookie(request?.headers?.get?.("cookie"));
 if(!ref)deny();
 try{
  return await reauthorizeClinicalStaffRequest(request,options);
 }catch{
  try{await store.revoke(ref);}catch{/* fail-closed even if DB is unavailable */}
  deny();
 }
}
