/**
 * SYNQORIA P03-B0045: audited, origin-bound session termination.
 *
 * A logout operation must revoke the server-held opaque ticket first. Only
 * the trusted server-side encrypted record supplies tenant/actor audit fields.
 * Records never contain patient data. An audit outage prevents claiming
 * successful audited logout; the caller must still expire browser cookies.
 */
import {randomUUID} from "node:crypto";
import {readOpaqueTicketCookie} from "./oidc-session-reauthorization.mjs";

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class AuditedStaffLogoutError extends Error {
 constructor(){super("staff_logout_not_confirmed");this.name="AuditedStaffLogoutError";}
}
const deny=()=>{throw new AuditedStaffLogoutError();};

export function requireAuditedStaffLogout(service,sessionStore,auditWriter,{
 publicOrigin,createCorrelationId=randomUUID
}={}){
 let expected;
 try{
  const url=new URL(publicOrigin);
  if(url.protocol!=="https:"||url.origin+"/"!==publicOrigin||url.username||url.password||
     url.search||url.hash||url.port||!url.hostname.includes("."))deny();
  expected=url.origin;
 }catch{deny();}
 if(!service||typeof service.logout!=="function"||
    !sessionStore||typeof sessionStore.read!=="function"||
    !auditWriter||typeof auditWriter.append!=="function"||
    typeof createCorrelationId!=="function")deny();

 return Object.freeze({
  async logout(request){
   if(request?.method!=="POST"||request?.headers?.get?.("origin")!==expected)deny();
   const ticket=readOpaqueTicketCookie(request.headers.get("cookie"));
   let record=null;
   try{
    if(ticket)record=await sessionStore.read(ticket);
    const outcome=await service.logout(request); // revokes first
    if(outcome?.revoked!==true){
     if(record!==null)deny(); // inconsistent store state
     return outcome;
    }
    if(!record||!uuid.test(record.actorId)||!uuid.test(record.tenantId))deny();
    const correlationId=createCorrelationId();
    if(!uuid.test(correlationId))deny();
    const logged=await auditWriter.append({
     actorId:record.actorId,tenantId:record.tenantId,
     correlationId,kind:"SESSION_REVOKED",outcome:"PERMITTED"
    });
    if(!logged||!uuid.test(logged.eventId))deny();
    return outcome;
   }catch{deny();}
  }
 });
}
