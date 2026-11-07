/**
 * SYNQORIA P03-B0036 — opaque browser session and encrypted server-only record.
 *
 * A browser receives only 256-bit random ticket reference. The JWT, staff
 * facility, role and privileges are AES-256-GCM-encrypted for storage in the
 * identity database, bound with AAD to that exact ticket digest. This is a
 * record codec, not a database, rotation, revocation or login implementation.
 */
import {createCipheriv,createDecipheriv,createHash,randomBytes} from "node:crypto";
export const STAFF_TICKET_COOKIE="__Host-synqoria-session";
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const CAP=/^[a-z][a-z0-9:._-]{2,63}$/;
const TOKEN=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const REF=/^[A-Za-z0-9_-]{43}$/;
const DIGEST=/^[0-9a-f]{64}$/;
const ROLE=/^[a-z][a-z0-9_-]{1,63}$/;
const EXPECTED_KEYS="actorId,bearer,capabilities,expiresAt,facilityId,issuedAt,revision,role,shiftId,subject,tenantId";
const MAX_LIFETIME=600000;
const MAX_PAYLOAD=12000;
const AAD="synqoria-staff-ticket-v1:";
const b64=x=>Buffer.from(x).toString("base64url");
const decode=(s,max)=>{
 if(typeof s!=="string"||!/^[A-Za-z0-9_-]+$/.test(s))return null;
 const out=Buffer.from(s,"base64url");
 return out.length>0&&out.length<=max&&out.toString("base64url")===s?out:null;
};
export class StaffTicketError extends Error {
 constructor(){super("staff_ticket_denied");this.name="StaffTicketError";}
}
const deny=()=>{throw new StaffTicketError();};
function key(env){
 const raw=env?.SYNQORIA_STAFF_TOKEN_WRAP_KEY;
 if(typeof raw!=="string"||!/^[0-9a-f]{64}$/i.test(raw))deny();
 const v=Buffer.from(raw,"hex");if(v.length!==32)deny();return v;
}
function validate(record,now,isIssuance=false){
 if(!record||typeof record!=="object"||Array.isArray(record)||
  Object.keys(record).sort().join(",")!==EXPECTED_KEYS||
  ![record.actorId,record.tenantId,record.facilityId,record.shiftId].every(x=>typeof x==="string"&&UUID.test(x))||
  typeof record.subject!=="string"||record.subject.length<2||record.subject.length>255||
  !TOKEN.test(record.bearer)||record.bearer.length>8192||
  typeof record.role!=="string"||!ROLE.test(record.role)||
  !Number.isSafeInteger(record.revision)||record.revision<1||
  !Number.isSafeInteger(record.issuedAt)||!Number.isSafeInteger(record.expiresAt)||
  record.issuedAt>now+5000||record.issuedAt<now-(isIssuance?60000:MAX_LIFETIME)||
  record.expiresAt<=now||record.expiresAt>record.issuedAt+MAX_LIFETIME||
  !Array.isArray(record.capabilities)||record.capabilities.length<1||record.capabilities.length>64||
  new Set(record.capabilities).size!==record.capabilities.length||
  !record.capabilities.every(x=>typeof x==="string"&&CAP.test(x)))deny();
}
export function staffTicketCookieOptions(){
 return Object.freeze({httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:600,priority:"high"});
}
export function staffTicketReferenceDigest(reference){
 if(typeof reference!=="string"||!REF.test(reference)||decode(reference,32)?.length!==32)deny();
 return createHash("sha256").update(reference,"ascii").digest("hex");
}
/** Use only after authenticated IdP and authoritative workforce gates pass. */
export function sealOpaqueStaffTicket(record,{env=process.env,now=Date.now()}={}){
 if(!Number.isSafeInteger(now))deny();
 const wrappingKey=key(env);
 validate(record,now,true);
 const serialized=Buffer.from(JSON.stringify(record),"utf8");
 if(serialized.length>MAX_PAYLOAD)deny();
 const reference=b64(randomBytes(32)),digest=staffTicketReferenceDigest(reference);
 const iv=randomBytes(12),cipher=createCipheriv("aes-256-gcm",wrappingKey,iv);
 cipher.setAAD(Buffer.from(AAD+digest,"utf8"));
 const encrypted=Buffer.concat([cipher.update(serialized),cipher.final()]);
 const ciphertext=["v1",b64(iv),b64(encrypted),b64(cipher.getAuthTag())].join(".");
 return Object.freeze({reference,digest,ciphertext,expiresAt:record.expiresAt});
}
/** Server-side only; never return this object to the browser. */
export function openOpaqueStaffTicket(reference,ciphertext,{env=process.env,now=Date.now()}={}){
 if(!Number.isSafeInteger(now)||typeof ciphertext!=="string"||ciphertext.length>20000)deny();
 const wrappingKey=key(env),digest=staffTicketReferenceDigest(reference);
 const parts=ciphertext.split(".");
 if(parts.length!==4||parts[0]!=="v1")deny();
 const iv=decode(parts[1],12),ct=decode(parts[2],MAX_PAYLOAD+32),tag=decode(parts[3],16);
 if(!iv||iv.length!==12||!ct||!tag||tag.length!==16)deny();
 try{
  const cipher=createDecipheriv("aes-256-gcm",wrappingKey,iv);
  cipher.setAAD(Buffer.from(AAD+digest,"utf8"));cipher.setAuthTag(tag);
  const bytes=Buffer.concat([cipher.update(ct),cipher.final()]);
  if(bytes.length>MAX_PAYLOAD)deny();
  const record=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
  validate(record,now);
  return Object.freeze({...record,capabilities:Object.freeze([...record.capabilities])});
 }catch{deny();}
}
