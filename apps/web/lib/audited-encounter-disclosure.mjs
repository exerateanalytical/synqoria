/**
 * SYNQORIA P03-B0048: encounter-disclosure denial auditing.
 *
 * Writes no encounter ID, patient ID, note body, diagnosis, token or
 * requester-supplied arbitrary fields into the identity audit table.
 * An unavailable or forged disclosure decision denies the sensitive read.
 * The route must provide a server-owned, pseudonymous audit writer.
 */
import {randomUUID} from "node:crypto";
import {authorizeEncounterDisclosure} from "./encounter-disclosure-authorization.mjs";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class AuditedEncounterDisclosureError extends Error{
 constructor(){super("encounter_disclosure_denied");this.name="AuditedEncounterDisclosureError";}
}
const deny=()=>{throw new AuditedEncounterDisclosureError();};

export async function authorizeAuditedEncounterDisclosure(staff,patientId,encounterId,resource,{
 auditWriter,createCorrelationId=randomUUID,...policy
}={}){
 if(!staff||!uuid.test(staff.actorId)||!uuid.test(staff.tenantId)||
   !auditWriter||typeof auditWriter.append!=="function"||
   typeof createCorrelationId!=="function")deny();
 try{return await authorizeEncounterDisclosure(staff,patientId,encounterId,resource,policy);}
 catch{
  try{
   const correlationId=createCorrelationId();
   if(!uuid.test(correlationId))deny();
   const result=await auditWriter.append({
    actorId:staff.actorId,tenantId:staff.tenantId,
    correlationId,kind:"ENCOUNTER_DISCLOSURE_DENIED",outcome:"DENIED"
   });
   if(!result||!uuid.test(result.eventId))deny();
  }catch{deny();}
  deny();
 }
}
