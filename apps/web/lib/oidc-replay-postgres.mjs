/**
 * PostgreSQL-backed, shared atomic one-use OIDC state store.
 *
 * Requires a trusted service-owned SQL client with query(text, values) returning
 * {rowCount, rows}. Use restricted credentials for a DEDICATED identity DB role.
 * Do not connect to an unreviewed PostgreSQL cell or reuse clinical PHI grants.
 * Pool acquisition, DB TLS, migrations, availability and operations are external.
 *
 * Both operations are one SQL statement and tolerate app instance concurrency.
 * DB clock (not browser, IdP or application node clock) controls expiry.
 */
const KEY=/^synqoria:oidc:state:v1:[0-9a-f]{64}$/;
const MAX_TTL_MS=300000;
const MAX_CLOCK_SKEW_MS=60000;
export class OidcReplayStoreError extends Error {
 constructor(){super("oidc_replay_store_unavailable");this.name="OidcReplayStoreError";}
}
const deny=()=>{throw new OidcReplayStoreError();};
export const OIDC_REPLAY_RESERVE_SQL=
 "INSERT INTO synqoria_identity.oidc_replay_state(state_key,expires_at) "+
 "SELECT $1,clock_timestamp()+($2 * INTERVAL '1 millisecond') "+
 "WHERE $2 > 0 AND $2 <= 300000 "+
 "ON CONFLICT(state_key) DO NOTHING RETURNING state_key";
export const OIDC_REPLAY_CONSUME_SQL=
 "DELETE FROM synqoria_identity.oidc_replay_state "+
 "WHERE state_key=$1 AND expires_at > clock_timestamp() RETURNING state_key";
export function postgresOidcReplayStore(client,{now=()=>Date.now()}={}){
 if(!client||typeof client.query!=="function"||typeof now!=="function")deny();
 const safeQuery=async(text,values)=>{
  try{
   const result=await client.query(text,values);
   if(!result||!Number.isSafeInteger(result.rowCount)||result.rowCount<0||
     result.rowCount>1||!Array.isArray(result.rows)||result.rows.length!==result.rowCount)deny();
   return result.rowCount===1;
  }catch{deny();}
 };
 const validKey=key=>typeof key==="string"&&KEY.test(key);
 return Object.freeze({
  reserve:async(key,expiresAt)=>{
   const current=now();
   const ttl=expiresAt-current;
   if(!validKey(key)||!Number.isSafeInteger(current)||!Number.isSafeInteger(expiresAt)||
     !Number.isSafeInteger(ttl)||ttl<=0||ttl>MAX_TTL_MS+MAX_CLOCK_SKEW_MS)deny();
   // The DB controls relative expiry. A cross-node clock difference greater
   // than MAX_CLOCK_SKEW_MS is operationally blocked, not silently accepted.
   return safeQuery(OIDC_REPLAY_RESERVE_SQL,[key,Math.min(ttl,MAX_TTL_MS)]);
  },
  consume:async(key,time)=>{
   if(!validKey(key)||!Number.isSafeInteger(time)||time<=0)deny();
   return safeQuery(OIDC_REPLAY_CONSUME_SQL,[key]);
  }
 });
}
