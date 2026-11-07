/**
 * SYNQORIA P03-B0047: privacy-preserving denied patient-disclosure audit.
 *
 * A missing patient policy, DENY decision, source outage, malformed response
 * or missing audit authority NEVER permits a patient read. Audit records only
 * tenant/actor pseudonyms and an independent random correlation UUID.
 * Patient ID and bearer token are NEVER written to the audit event.
 */
import {randomUUID} from "node:crypto";
import {authorizePatientDisclosure} from "./patient-disclosure-authorization.mjs";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class AuditedPatientDisclosureError extends Error{
 constructor(){super("patient_disclosure_denied");this.name="AuditedPatientDisclosureError";}
}
const deny=()=>{throw new AuditedPatientDisclosureError();};
export async function authorizeAuditedPatientDisclosure(staff,patientId,resource,{
 auditWriter,createCorrelationId=randomUUID,...policy
}={}){
 if(!staff||!uuid.test(staff.actorId)||!uuid.test(staff.tenantId)||
    !auditWriter||typeof auditWriter.append!=="function"||
    typeof createCorrelationId!=="function")deny();
 try{return await authorizePatientDisclosure(staff,patientId,resource,policy);}
 catch{
  try{
   const correlationId=createCorrelationId();
   if(!uuid.test(correlationId))deny();
   const result=await auditWriter.append({
    actorId:staff.actorId,tenantId:staff.tenantId,
    kind:"PATIENT_DISCLOSURE_DENIED",outcome:"DENIED",correlationId
   });
   if(!result||!uuid.test(result.eventId))deny();
  }catch{deny();}
  deny();
 }
}
