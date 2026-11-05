/**
 * SYNQORIA P03-B0037 — restricted PostgreSQL server-side staff session store.
 *
 * Browser ticket reference is random, short and opaque; only its SHA-256 digest
 * is sent to SQL. The server stores AES-GCM ciphertext with external vault key.
 * SQL role is narrowly scoped, FORCE-RLS and cannot change roles/entitlements
 * or overwrite ciphertext. This is not a substitute for live IdP revocation
 * and workforce authorization on clinical reads.
 */
import {sealOpaqueStaffTicket,openOpaqueStaffTicket,
 staffTicketReferenceDigest,StaffTicketError} from "./opaque-staff-session.mjs";

export class StaffSessionStoreError extends Error {
 constructor(){super("staff_session_store_unavailable");this.name="StaffSessionStoreError";}
}
const deny=()=>{throw new StaffSessionStoreError();};
export const SESSION_INSERT_SQL=
 "INSERT INTO synqoria_identity.staff_sessions(session_digest,sealed_record,expires_at) "+
 "SELECT $1,$2,to_timestamp($3/1000.0) "+
 "WHERE to_timestamp($3/1000.0)>clock_timestamp() "+
 "AND to_timestamp($3/1000.0)<=clock_timestamp()+INTERVAL '10 minutes' "+
 "ON CONFLICT(session_digest) DO NOTHING";
export const SESSION_READ_SQL=
 "SELECT sealed_record,expires_at FROM synqoria_identity.staff_sessions "+
 "WHERE session_digest=$1 AND revoked_at IS NULL AND expires_at>clock_timestamp()";
export const SESSION_REVOKE_SQL=
 "UPDATE synqoria_identity.staff_sessions SET revoked_at=clock_timestamp() "+
 "WHERE session_digest=$1 AND revoked_at IS NULL AND expires_at>clock_timestamp()";
function assertResult(result,max=1){
 if(!result||!Number.isSafeInteger(result.rowCount)||result.rowCount<0||
    result.rowCount>max||!Array.isArray(result.rows)||
    (result.rows.length!==0&&result.rows.length!==result.rowCount))deny();
}
function parseExpiry(date){
 if(date instanceof Date)return date.getTime();
 if(typeof date==="string"&&date.length<50)return Date.parse(date);
 return NaN;
}
export function createPostgresStaffSessionStore(client,{
 env=process.env,now=()=>Date.now()
}={}){
 if(!client||typeof client.query!=="function"||typeof now!=="function")deny();
 async function query(sql,args){
  try{return await client.query(sql,args);}catch{deny();}
 }
 const store={
  async issue(record){
   const clock=now();
   if(!Number.isSafeInteger(clock))deny();
   let sealed;
   try{sealed=sealOpaqueStaffTicket(record,{env,now:clock});}catch{deny();}
   const result=await query(SESSION_INSERT_SQL,[sealed.digest,sealed.ciphertext,sealed.expiresAt]);
   assertResult(result);
   if(result.rowCount!==1)deny();
   // Reference is returned only after authoritative durable insert.
   return sealed.reference;
  },
  async read(reference){
   let digest;
   try{digest=staffTicketReferenceDigest(reference);}catch{deny();}
   const result=await query(SESSION_READ_SQL,[digest]);
   assertResult(result);
   if(result.rowCount===0)return null;
   if(result.rows.length!==1)deny();
   const row=result.rows[0],exp=parseExpiry(row?.expires_at),clock=now();
   if(!Number.isSafeInteger(clock)||!Number.isFinite(exp)||exp<=clock||
      typeof row?.sealed_record!=="string")deny();
   let record;
   try{record=openOpaqueStaffTicket(reference,row.sealed_record,{env,now:clock});}catch{deny();}
   if(record.expiresAt!==exp)deny();
   return record;
  },
  async revoke(reference){
   let digest;
   try{digest=staffTicketReferenceDigest(reference);}catch{deny();}
   const result=await query(SESSION_REVOKE_SQL,[digest]);
   assertResult(result);
   return result.rowCount===1;
  }
 };
 return Object.freeze(store);
}
