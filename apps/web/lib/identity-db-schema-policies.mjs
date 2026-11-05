/**
 * SYNQORIA P03-B0055: security-policy schema attestation.
 *
 * An authenticated restricted DB role is not enough: the three identity
 * tables must also have FORCE ROW LEVEL SECURITY and only expected, scoped
 * policy names/roles/commands. This rejects accidental grants or admin-created
 * permissive PUBLIC policies before production login begins.
 */
export class IdentitySchemaPolicyError extends Error{
 constructor(){super("identity_schema_policy_denied");this.name="IdentitySchemaPolicyError";}
}
const deny=()=>{throw new IdentitySchemaPolicyError();};
export const IDENTITY_SCHEMA_POLICY_SQL=`
SELECT c.relname AS table_name,
 c.relrowsecurity AS row_security,
 c.relforcerowsecurity AS force_row_security,
 EXISTS (
   SELECT 1 FROM aclexplode(COALESCE(c.relacl,acldefault('r',c.relowner))) acl
   WHERE acl.grantee=0
 ) AS public_grant,
 COALESCE((
   SELECT json_agg(json_build_object(
     'name', p.polname,
     'command', p.polcmd,
     'permissive', p.polpermissive,
     'using', pg_get_expr(p.polqual,p.polrelid),
     'with_check', pg_get_expr(p.polwithcheck,p.polrelid),
     'roles', (SELECT array_agg(r.rolname ORDER BY r.rolname)
               FROM pg_roles r WHERE r.oid=ANY(p.polroles))
   ) ORDER BY p.polname)
   FROM pg_policy p WHERE p.polrelid=c.oid
 ),'[]'::json) AS policies
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='synqoria_identity'
 AND c.relname IN ('oidc_replay_state','staff_sessions','staff_security_audit')
 ORDER BY c.relname`.trim();

const spec={
 oidc_replay_state:{
  oidc_replay_runtime_read_live:["r","synqoria_oidc_replay_runtime"],
  oidc_replay_runtime_create_live:["a","synqoria_oidc_replay_runtime"],
  oidc_replay_runtime_consume_live:["d","synqoria_oidc_replay_runtime"],
  oidc_replay_janitor_read_expired:["r","synqoria_oidc_replay_janitor"],
  oidc_replay_janitor_delete_expired:["d","synqoria_oidc_replay_janitor"]
 },
 staff_sessions:{
  staff_session_active_read:["r","synqoria_staff_session_runtime"],
  staff_session_new_secure:["a","synqoria_staff_session_runtime"],
  staff_session_revoke_once:["d","synqoria_staff_session_runtime"],
  staff_session_janitor_read_expired:["r","synqoria_staff_session_janitor"],
  staff_session_janitor_delete_expired:["d","synqoria_staff_session_janitor"]
 },
 staff_security_audit:{
  identity_security_audit_append_only:["a","synqoria_identity_audit_writer"]
 }
};
/**
 * P03-B0061. Compare the compiled policy predicate, not only its name.
 * A DBA could otherwise replace a correctly named policy with USING(true).
 * Only the exact reviewed live/expired TTL and append-only expressions pass.
 */
const expectedPredicates={
 oidc_replay_runtime_read_live:"expires_at>clock_timestamp",
 oidc_replay_runtime_create_live:"expires_at>clock_timestampandexpires_at<=clock_timestamp+'00:05:00'::interval",
 oidc_replay_runtime_consume_live:"expires_at>clock_timestamp",
 oidc_replay_janitor_read_expired:"expires_at<=clock_timestamp",
 oidc_replay_janitor_delete_expired:"expires_at<=clock_timestamp",
 staff_session_active_read:"expires_at>clock_timestamp",
 staff_session_new_secure:"expires_at>clock_timestampandexpires_at<=clock_timestamp+'00:10:00'::interval",
 staff_session_revoke_once:"expires_at>clock_timestamp",
 staff_session_janitor_read_expired:"expires_at<=clock_timestamp",
 staff_session_janitor_delete_expired:"expires_at<=clock_timestamp",
 identity_security_audit_append_only:"occurred_at<=clock_timestamp+'00:01:00'::intervalandoccurred_at>=clock_timestamp-'00:02:00'::interval"
};
function normalizedPredicate(x){
 return typeof x==="string" ? x.toLowerCase().replace(/[()\s]/g,"") : null;
}
export async function verifyIdentitySchemaPolicies(client){
 if(!client||typeof client.query!=="function")deny();
 let data;
 try{data=await client.query(IDENTITY_SCHEMA_POLICY_SQL,[]);}catch{deny();}
 if(!data||data.rowCount!==3||!Array.isArray(data.rows)||
    data.rows.length!==3||new Set(data.rows.map(r=>r?.table_name)).size!==3)deny();
 for(const row of data.rows){
  const expected=spec[row?.table_name];
  if(!expected||row.row_security!==true||row.force_row_security!==true||
     row.public_grant!==false||!Array.isArray(row.policies)||
     row.policies.length!==Object.keys(expected).length)deny();
  const seen=new Set();
  for(const policy of row.policies){
   const match=expected[policy?.name];
   if(!match||seen.has(policy.name)||policy.permissive!==true||
      policy.command!==match[0]||!Array.isArray(policy.roles)||
      policy.roles.length!==1||policy.roles[0]!==match[1])deny();
   const raw=policy.command==="a"?policy.with_check:policy.using;
   if((policy.command==="a"&&policy.using!==null)||
      (policy.command!=="a"&&policy.with_check!==null)||
      normalizedPredicate(raw)!==expectedPredicates[policy.name])deny();
   seen.add(policy.name);
  }
 }
 return Object.freeze({verified:true,tableCount:3,policyCount:11});
}
