/**
 * P03-B0097..B0100: REAL least-privilege PostgreSQL login → NOLOGIN role gate.
 *
 * Source identity migrations intentionally create NOLOGIN RLS roles.
 * The transport connection uses its own restricted LOGIN principal, then
 * transaction-scoped SET LOCAL ROLE to the exact capability role.
 * Every business statement is executed on that same TLS-attested backend.
 * COMMIT/ROLLBACK resets role selection before pool checkout is released.
 */
export class IdentityAttestedQueryError extends Error{
 constructor(){super("identity_attested_query_denied");this.name="IdentityAttestedQueryError";}
}
const deny=()=>{throw new IdentityAttestedQueryError();};
const bound=Object.freeze({
 synqoria_oidc_replay_runtime:"synqoria_oidc_replay_login",
 synqoria_staff_session_runtime:"synqoria_staff_session_login",
 synqoria_identity_audit_writer:"synqoria_identity_audit_login"
});
const cipher=/^[A-Za-z0-9_-]{8,128}$/;
function assertAuthenticatedTls(client){
 const socket=client?.connection?.stream;
 if(!socket||socket.encrypted!==true||socket.authorized!==true||
    typeof socket.getProtocol!=="function"||typeof socket.getCipher!=="function"||
    typeof socket.getPeerCertificate!=="function")deny();
 const protocol=socket.getProtocol(),info=socket.getCipher(),cert=socket.getPeerCertificate();
 if(!["TLSv1.2","TLSv1.3"].includes(protocol)||
    typeof info?.name!=="string"||!cipher.test(info.name)||
    /(?:NULL|EXPORT|RC4|MD5|3DES|DES)/i.test(info.name)||
    !cert?.raw||!Buffer.isBuffer(cert.raw)||cert.raw.length<256)deny();
}
export const IDENTITY_CONNECTION_ATTEST_SQL=`
SELECT current_user AS role_name,
 session_user AS login_name,
 current_setting('row_security') AS row_security,
 runtime.rolsuper AS superuser,
 runtime.rolbypassrls AS bypass_rls,
 runtime.rolcreaterole AS create_role,
 runtime.rolcreatedb AS create_db,
 login.rolcanlogin AS login_can_login,
 login.rolinherit AS login_inherit,
 login.rolsuper AS login_superuser,
 login.rolbypassrls AS login_bypass_rls,
 login.rolcreaterole AS login_create_role,
 login.rolcreatedb AS login_create_db,
 EXISTS(SELECT 1 FROM pg_auth_members m
   WHERE m.member=login.oid AND m.roleid=runtime.oid)
   AS login_has_expected_membership,
 EXISTS(SELECT 1 FROM pg_auth_members m
   WHERE m.member=login.oid AND m.roleid<>runtime.oid)
   AS login_has_extra_membership,
 EXISTS(SELECT 1 FROM pg_auth_members m
   JOIN pg_roles other ON other.oid=m.roleid
   WHERE m.member=runtime.oid AND other.rolname IN (
     'synqoria_oidc_replay_runtime',
     'synqoria_staff_session_runtime',
     'synqoria_identity_audit_writer') AND other.oid<>runtime.oid)
   AS cross_role_member
FROM pg_roles runtime
JOIN pg_roles login ON login.rolname=session_user
WHERE runtime.rolname=current_user`.trim();
const keys="bypass_rls,create_db,create_role,cross_role_member,login_bypass_rls,login_can_login,login_create_db,login_create_role,login_has_expected_membership,login_has_extra_membership,login_inherit,login_name,login_superuser,role_name,row_security,superuser";
function attest(result,role,principal){
 if(result?.rowCount!==1||!Array.isArray(result.rows)||result.rows.length!==1)deny();
 const r=result.rows[0];
 if(!r||Object.keys(r).sort().join(",")!==keys||
   r.role_name!==role||r.login_name!==principal||r.row_security!=="on"||
   r.superuser!==false||r.bypass_rls!==false||
   r.create_role!==false||r.create_db!==false||
   r.cross_role_member!==false||
   r.login_can_login!==true||r.login_inherit!==false||
   r.login_superuser!==false||r.login_bypass_rls!==false||
   r.login_create_role!==false||r.login_create_db!==false||
   r.login_has_expected_membership!==true||r.login_has_extra_membership!==false)deny();
}
const statementAllowed=x=>typeof x==="string"&&x.length>0&&x.length<=20000&&
 /^\s*(?:SELECT|INSERT|DELETE)\b/i.test(x)&&
 !/[;]/.test(x)&&!/(?:\bSET\s+ROLE\b|\bRESET\s+ROLE\b|\bCOMMIT\b|\bROLLBACK\b)/i.test(x);
/** Enforce physical-connection identity and no multi-statement role changes. */
export function createConnectionAttestedSqlClient(pool,expectedRole,expectedLogin){
 if(!pool||typeof pool.connect!=="function"||bound[expectedRole]!==expectedLogin)deny();
 return Object.freeze({
  async query(statement,params=[]){
   if(!statementAllowed(statement)||!Array.isArray(params)||params.length>32)deny();
   let connection,transaction=false,committed=false,discard=false;
   try{
    connection=await pool.connect();
    if(!connection||typeof connection.query!=="function"||
       typeof connection.release!=="function")deny();
    assertAuthenticatedTls(connection);
    await connection.query("BEGIN");
    transaction=true;
    // Fixed canonical role: never derived from request, bearer or SQL results.
    await connection.query("SET LOCAL ROLE "+expectedRole);
    attest(await connection.query(IDENTITY_CONNECTION_ATTEST_SQL,[]),expectedRole,expectedLogin);
    const result=await connection.query(statement,params);
    await connection.query("COMMIT");
    committed=true;
    return result;
   }catch{
    // Once a privilege probe or SQL operation fails, do not reuse its backend.
    // Even after successful ROLLBACK, a transport/role failure is quarantined.
    discard=true;
    if(connection&&transaction&&!committed){
     try{await connection.query("ROLLBACK");}catch{/* failure already denied */}
    }
    deny();
   }finally{
    if(connection&&typeof connection.release==="function"){
     try{connection.release(discard);}catch{deny();}
    }
   }
  }
 });
}
