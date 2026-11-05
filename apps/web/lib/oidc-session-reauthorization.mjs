/**
 * SYNQORIA P03-B0038 — reauthorize every scoped staff request.
 *
 * A live encrypted ticket is NOT a standing clinical grant. The request must
 * pass: opaque-cookie authenticity, server-side non-revoked session load,
 * active IdP token introspection, and independently fresh facility/role/shift
 * scope. Any workforce revision, role, facility or capability change denies
 * and demands reauthentication. Clinical backend must still reauthorize the
 * specific patient/encounter/command.
 */
import {STAFF_TICKET_COOKIE} from "./opaque-staff-session.mjs";
import {verifyActiveOidcAccessToken} from "./oidc-token-introspection.mjs";
import {resolveStaffWorkforceGrant} from "./oidc-workforce-grants.mjs";

const REF=/^[A-Za-z0-9_-]{43}$/;
export class StaffReauthorizationError extends Error{
 constructor(){super("staff_reauthorization_denied");this.name="StaffReauthorizationError";}
}
const deny=()=>{throw new StaffReauthorizationError();};

export function readOpaqueTicketCookie(header){
 if(typeof header!=="string"||!header.length||header.length>10000)return null;
 const hits=header.split(";").map(x=>x.trim()).filter(Boolean)
  .filter(x=>x.startsWith(STAFF_TICKET_COOKIE+"="));
 if(hits.length!==1)return null;
 const ticket=hits[0].slice(STAFF_TICKET_COOKIE.length+1);
 return REF.test(ticket)?ticket:null;
}
function equalSets(a,b){
 return Array.isArray(a)&&Array.isArray(b)&&a.length===b.length&&
  a.every(x=>b.includes(x))&&new Set(a).size===a.length&&new Set(b).size===b.length;
}
/** This context contains a bearer; use ONLY within server-side BFF handlers. */
export async function reauthorizeClinicalStaffRequest(request,{
 sessionStore,introspectionConfig,workforceConfig,fetcher=fetch,nowMs=Date.now()
}={}){
 if(!request||!request.headers||typeof request.headers.get!=="function"||
   !sessionStore||typeof sessionStore.read!=="function"||
   !introspectionConfig||!workforceConfig||typeof fetcher!=="function"||
   !Number.isSafeInteger(nowMs))deny();
 const ticket=readOpaqueTicketCookie(request.headers.get("cookie"));
 if(!ticket)deny();
 try{
  const record=await sessionStore.read(ticket);
  if(!record||!Number.isSafeInteger(record.issuedAt)||
    !Number.isSafeInteger(record.expiresAt)||record.expiresAt<=nowMs||
    typeof record.bearer!=="string"||typeof record.subject!=="string"||
    !Array.isArray(record.capabilities))deny();
  const introspected=await verifyActiveOidcAccessToken(record.bearer,
   {subject:record.subject},introspectionConfig,{fetcher,nowSeconds:Math.floor(nowMs/1000)});
  if(introspected.expiresAt*1000<=nowMs)deny();
  const fresh=await resolveStaffWorkforceGrant(
   {subject:record.subject,accessScopes:record.capabilities},
   record.bearer,workforceConfig,{fetcher,nowMs});
  if(fresh.actorId!==record.actorId||fresh.tenantId!==record.tenantId||
     fresh.facilityId!==record.facilityId||fresh.shiftId!==record.shiftId||
     fresh.role!==record.role||fresh.revision!==record.revision||
     !equalSets(fresh.capabilities,record.capabilities)||
     fresh.expiresAt<nowMs)deny();
  return Object.freeze({
   actorId:record.actorId,tenantId:record.tenantId,facilityId:record.facilityId,
   capabilities:Object.freeze([...record.capabilities]),bearer:record.bearer,
   sessionExpiresAt:Math.min(record.expiresAt,fresh.expiresAt,introspected.expiresAt*1000)
  });
 }catch{deny();}
}
/**
 * Logout revokes the opaque ticket in the shared store without requiring an
 * online identity provider. Callers MUST clear the Secure HttpOnly cookie even
 * on a DB outage, while returning failure status if revocation is uncertain.
 */
export async function revokeStaffRequestSession(request,{sessionStore}={}){
 if(!sessionStore||typeof sessionStore.revoke!=="function"||
    !request?.headers||typeof request.headers.get!=="function")deny();
 const ref=readOpaqueTicketCookie(request.headers.get("cookie"));
 if(!ref)return Object.freeze({revoked:false});
 try{return Object.freeze({revoked:await sessionStore.revoke(ref)===true});}
 catch{deny();}
}
export function clearStaffTicketCookieOptions(){
 return Object.freeze({httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:0,priority:"high"});
}
