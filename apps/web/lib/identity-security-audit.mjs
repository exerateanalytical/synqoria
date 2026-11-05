/**
 * SYNQORIA P03-B0043 — privacy-preserving identity security audit writer.
 *
 * Append-only security metadata, no patient/clinical content or raw staff
 * identifiers. Actor and tenant are separately HMAC-pseudonymized with a
 * vault-managed pepper. All fields are closed allowlists; no free-text reason,
 * token, location, name, IP or other identifying detail may enter the audit.
 */
import {createHmac,randomUUID} from "node:crypto";
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const kinds=new Set([
 "LOGIN_GRANTED","LOGIN_DENIED","SESSION_REVOKED","SESSION_EXPIRED",
 "WORKFORCE_GRANT_DENIED","PATIENT_DISCLOSURE_DENIED","ENCOUNTER_DISCLOSURE_DENIED"
]);
const outcomes=new Set(["PERMITTED","DENIED"]);
export class IdentitySecurityAuditError extends Error{
 constructor(){super("identity_audit_unavailable");this.name="IdentitySecurityAuditError";}
}
const deny=()=>{throw new IdentitySecurityAuditError();};
const digest=(secret,kind,value)=>createHmac("sha256",secret)
 .update("synqoria:identity:audit:v1:"+kind+"\0"+value,"utf8").digest("hex");
export const IDENTITY_AUDIT_INSERT_SQL=
 "INSERT INTO synqoria_identity.staff_security_audit("+
 "event_id,actor_digest,tenant_digest,kind,outcome,correlation_id,occurred_at) "+
 "VALUES ($1,$2,$3,$4,$5,$6,to_timestamp($7/1000.0))";
export function createIdentitySecurityAuditWriter(client,{pepper,now=Date.now}={}){
 if(!client||typeof client.query!=="function"||typeof now!=="function"||
   typeof pepper!=="string"||!/^[0-9a-f]{64}$/i.test(pepper))deny();
 const secret=Buffer.from(pepper,"hex");
 return Object.freeze({
  async append(input={}){
   if(!input||typeof input!=="object"||Array.isArray(input)||
      Object.keys(input).sort().join(",")!=="actorId,correlationId,kind,outcome,tenantId")deny();
   const {actorId,tenantId,kind,outcome,correlationId}=input;
   if(![actorId,tenantId,correlationId].every(x=>typeof x==="string"&&UUID.test(x))||
     !kinds.has(kind)||!outcomes.has(outcome)||
     (kind==="LOGIN_GRANTED"&&outcome!=="PERMITTED")||
     (kind!=="LOGIN_GRANTED"&&outcome==="PERMITTED"&&kind!=="SESSION_REVOKED"))deny();
   const timestamp=now();
   if(!Number.isSafeInteger(timestamp)||timestamp<1)deny();
   const id=randomUUID();
   const args=[id,digest(secret,"actor",tenantId+"|"+actorId),digest(secret,"tenant",tenantId),
     kind,outcome,correlationId,timestamp];
   try{
    const result=await client.query(IDENTITY_AUDIT_INSERT_SQL,args);
    if(!result||result.rowCount!==1||!Array.isArray(result.rows)||
       result.rows.length!==0)deny();
   }catch{deny();}
   return Object.freeze({eventId:id,createdAt:timestamp});
  }
 });
}
