/**
 * SYNQORIA P03-B0053 — verify the actual PostgreSQL runtime identity role.
 *
 * Distinct client objects alone do NOT prove separate DB credentials.
 * Before live staff authentication, require each SQL connection to return
 * its exact least-privilege CURRENT_USER and row-security state. Privileged
 * postgres/superuser sessions, BYPASSRLS, and cross-role membership are denied.
 */
const allowed=Object.freeze({
 replay:"synqoria_oidc_replay_runtime",
 session:"synqoria_staff_session_runtime",
 audit:"synqoria_identity_audit_writer"
});
export const IDENTITY_ROLE_PROBE_SQL=`
SELECT current_user AS role_name,
  r.rolsuper AS superuser,
  r.rolbypassrls AS bypass_rls,
  r.rolcreaterole AS create_role,
  r.rolcreatedb AS create_db,
  current_setting('row_security') AS row_security,
  EXISTS(
    SELECT 1 FROM pg_auth_members m
    JOIN pg_roles other ON other.oid = m.roleid
    WHERE m.member = r.oid
      AND other.rolname IN (
        'synqoria_oidc_replay_runtime',
        'synqoria_staff_session_runtime',
        'synqoria_identity_audit_writer'
      )
      AND other.rolname <> r.rolname
  ) AS cross_role_member
FROM pg_roles r WHERE r.rolname = current_user
`.trim();

export class IdentityRoleVerificationError extends Error {
 constructor(){super("identity_sql_roles_not_isolated");this.name="IdentityRoleVerificationError";}
}
const deny=()=>{throw new IdentityRoleVerificationError();};

async function check(client,expectation){
 if(!client||typeof client.query!=="function")deny();
 let result;
 try{result=await client.query(IDENTITY_ROLE_PROBE_SQL,[]);}catch{deny();}
 if(!result||result.rowCount!==1||!Array.isArray(result.rows)||result.rows.length!==1)deny();
 const row=result.rows[0];
 if(!row||Object.keys(row).sort().join(",")!==
   "bypass_rls,create_db,create_role,cross_role_member,role_name,row_security,superuser"||
   row.role_name!==expectation||row.superuser!==false||row.bypass_rls!==false||
   row.create_role!==false||row.create_db!==false||
   row.cross_role_member!==false||row.row_security!=="on")deny();
}
export async function verifyIdentitySqlClients({replaySql,sessionSql,auditSql}={}){
 if(!replaySql||!sessionSql||!auditSql||replaySql===sessionSql||
   replaySql===auditSql||sessionSql===auditSql)deny();
 await check(replaySql,allowed.replay);
 await check(sessionSql,allowed.session);
 await check(auditSql,allowed.audit);
 return Object.freeze({verified:true,roles:Object.freeze({...allowed})});
}
