/**
 * SYNQORIA P03-B0074..B0077: per-operation physical PostgreSQL connection gate.
 *
 * Attest actual TLS protocol, cipher and least-privilege role on the SAME
 * physical connection as the statement. A pooled reconnect or role drift must
 * never inherit a startup-only SQL authorization decision. SQL credentials
 * and query error details are never returned to browser-facing callers.
 */
export class IdentityAttestedQueryError extends Error{
 constructor(){super("identity_attested_query_denied");this.name="IdentityAttestedQueryError";}
}
const deny=()=>{throw new IdentityAttestedQueryError();};
export const IDENTITY_CONNECTION_ATTEST_SQL=String.raw`
SELECT current_user AS role_name,
 current_setting('row_security') AS row_security,
 r.rolsuper AS superuser,r.rolbypassrls AS bypass_rls,
 r.rolcreaterole AS create_role,r.rolcreatedb AS create_db,
 ssl.ssl AS tls_active,ssl.version AS tls_version,ssl.cipher AS tls_cipher,
 EXISTS(
 SELECT 1 FROM pg_auth_members m JOIN pg_roles other ON other.oid=m.roleid
 WHERE m.member=r.oid AND other.rolname IN (
 'synqoria_oidc_replay_runtime','synqoria_staff_session_runtime',
 'synqoria_identity_audit_writer') AND other.rolname<>r.rolname
 ) AS cross_role_member
FROM pg_roles r
JOIN pg_stat_ssl ssl ON ssl.pid=pg_backend_pid()
WHERE r.rolname=current_user`;
const ROLE=/^synqoria_(?:oidc_replay_runtime|staff_session_runtime|identity_audit_writer)$/;
const CIPHER=/^[A-Za-z0-9_-]{8,128}$/;
function attest(result,expected){
 if(!result||result.rowCount!==1||!Array.isArray(result.rows)||result.rows.length!==1)deny();
 const row=result.rows[0];
 if(!row||Object.keys(row).sort().join(",")!==
   "bypass_rls,create_db,create_role,cross_role_member,role_name,row_security,superuser,tls_active,tls_cipher,tls_version"||
   row.role_name!==expected||row.row_security!=="on"||
   row.superuser!==false||row.bypass_rls!==false||row.cross_role_member!==false||
   row.create_role!==false||row.create_db!==false||
   row.tls_active!==true||!["TLSv1.2","TLSv1.3"].includes(row.tls_version)||
   typeof row.tls_cipher!=="string"||!CIPHER.test(row.tls_cipher)||
   /(?:NULL|EXPORT|RC4|MD5|3DES|DES)/i.test(row.tls_cipher))deny();
}
export function createConnectionAttestedSqlClient(pool,expectedRole){
 if(!pool||typeof pool.connect!=="function"||!ROLE.test(expectedRole))deny();
 return Object.freeze({
  async query(statement,params=[]){
   if(typeof statement!=="string"||!statement.trim()||statement.length>20000||
      !Array.isArray(params)||params.length>32)deny();
   let connection;
   try{
    connection=await pool.connect();
    if(!connection||typeof connection.query!=="function"||
       typeof connection.release!=="function")deny();
    attest(await connection.query(IDENTITY_CONNECTION_ATTEST_SQL,[]),expectedRole);
    return await connection.query(statement,params);
   }catch{deny();}
   finally{
    if(connection&&typeof connection.release==="function"){
     try{connection.release();}catch{deny();}
    }
   }
  }
 });
}
