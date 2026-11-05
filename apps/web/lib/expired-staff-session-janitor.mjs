/**
 * SYNQORIA P03-B0046: bounded, isolated expired-session janitor.
 *
 * The injected SQL client must use synqoria_staff_session_janitor only.
 * It cannot read ciphertext or active staff tickets under forced RLS.
 * No runtime grant is acquired and no patient/PHI operations are performed.
 */
const DIGEST=/^[0-9a-f]{64}$/;
export class StaffJanitorError extends Error{
 constructor(){super("staff_janitor_unavailable");this.name="StaffJanitorError";}
}
const deny=()=>{throw new StaffJanitorError();};
export const STAFF_SESSION_SWEEP_SQL=
 "WITH doomed AS ("+
 "SELECT session_digest FROM synqoria_identity.staff_sessions "+
 "WHERE expires_at <= clock_timestamp() "+
 "ORDER BY expires_at,session_digest LIMIT $1 FOR UPDATE SKIP LOCKED"+
 ") DELETE FROM synqoria_identity.staff_sessions AS s USING doomed "+
 "WHERE s.session_digest=doomed.session_digest "+
 "RETURNING s.session_digest";

export function createExpiredStaffSessionJanitor(client){
 if(!client||typeof client.query!=="function")deny();
 return Object.freeze({
  async sweep({limit=100}={}){
   if(!Number.isSafeInteger(limit)||limit<1||limit>500)deny();
   let result;
   try{result=await client.query(STAFF_SESSION_SWEEP_SQL,[limit]);}catch{deny();}
   if(!result||!Number.isSafeInteger(result.rowCount)||result.rowCount<0||
      result.rowCount>limit||!Array.isArray(result.rows)||
      result.rows.length!==result.rowCount||
      new Set(result.rows.map(x=>x.session_digest)).size!==result.rowCount||
      result.rows.some(x=>!x||Object.keys(x).sort().join(",")!=="session_digest"||
       typeof x.session_digest!=="string"||!DIGEST.test(x.session_digest)))deny();
   // Do not return digests or other correlatable session identifiers.
   return Object.freeze({removed:result.rowCount,mayHaveMore:result.rowCount===limit});
  }
 });
}
