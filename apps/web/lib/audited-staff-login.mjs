/**
 * SYNQORIA P03-B0044: mandatory durable audit of completed staff login.
 *
 * Calls the existing authoritative login service, reads the just-written
 * encrypted session from the trusted server-side store, then writes a
 * pseudonymous append-only identity audit. A browser ticket is released only
 * after the audit transaction acknowledges persistence. If any check fails,
 * revoke the orphan ticket and do not return it to the browser.
 *
 * This adapter DOES NOT add any new clinician permissions, patient access,
 * token serialization or bypass for unconfigured identity infrastructure.
 */
import {randomUUID} from "node:crypto";

export class AuditedStaffLoginError extends Error {
 constructor(){super("staff_login_audit_required");this.name="AuditedStaffLoginError";}
}
const deny=()=>{throw new AuditedStaffLoginError();};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const ticket=/^[A-Za-z0-9_-]{43}$/;

export function requireAuditedStaffLogin(loginService,sessionStore,auditWriter,{
 createCorrelationId=randomUUID
}={}){
 if(!loginService||typeof loginService.complete!=="function"||
    !sessionStore||typeof sessionStore.read!=="function"||
    typeof sessionStore.revoke!=="function"||
    !auditWriter||typeof auditWriter.append!=="function"||
    typeof createCorrelationId!=="function")deny();

 return Object.freeze({
  async complete(request){
   let reference=null;
   try{
    const issued=await loginService.complete(request);
    reference=issued?.sessionCookie?.value;
    if(!ticket.test(reference))deny();
    const context=await sessionStore.read(reference);
    if(!context||!uuid.test(context.actorId)||!uuid.test(context.tenantId)||
       !Number.isSafeInteger(context.expiresAt)||
       context.expiresAt<=Date.now())deny();
    const correlationId=createCorrelationId();
    if(!uuid.test(correlationId))deny();
    const logged=await auditWriter.append({
     actorId:context.actorId,tenantId:context.tenantId,
     correlationId,kind:"LOGIN_GRANTED",outcome:"PERMITTED"
    });
    if(!logged||!uuid.test(logged.eventId))deny();
    // Browser only receives the opaque reference; no bearer or audit context.
    return issued;
   }catch{
    if(reference!==null&&ticket.test(reference)){
     try{await sessionStore.revoke(reference);}catch{/* safe denial, no cookie issued */}
    }
    deny();
   }
  }
 });
}
