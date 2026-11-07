import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync} from "node:crypto";
import {createStaffIdentityRuntime,createVerifiedStaffIdentityRuntime}
 from "../apps/web/lib/staff-identity-runtime.mjs";
import {createVerifiedClinicalRuntimeBinding,ClinicalRuntimeBindingError}
 from "../apps/web/lib/verified-clinical-runtime-binding.mjs";

const tenant="11111111-1111-4111-8111-111111111111";
const facility="22222222-2222-4222-8222-222222222222";
const issuer="https://identity.synqoria.org/realms/hospital";
const publicOrigin="https://staff.synqoria.org/";
const {publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const env={
 SYNQORIA_RUNTIME_MODE:"production",SYNQORIA_PUBLIC_ORIGIN:publicOrigin,
 SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.synqoria.org/",
 SYNQORIA_EXPECTED_TENANT_ID:tenant,SYNQORIA_EXPECTED_FACILITY_ID:facility,
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN:"1",
 SYNQORIA_REQUIRE_UPSTREAM_NO_STORE:"1",
 SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"1",SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE:"1",SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_ROLE_POLICY_JSON:JSON.stringify({tenantId:tenant,version:1,roles:{nurse:["patient:view"]}}),
 SYNQORIA_OIDC_ISSUER:issuer,SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT:issuer+"/protocol/openid-connect/auth",
 SYNQORIA_OIDC_TOKEN_ENDPOINT:issuer+"/protocol/openid-connect/token",
 SYNQORIA_OIDC_INTROSPECTION_ENDPOINT:issuer+"/protocol/openid-connect/token/introspect",
 SYNQORIA_WORKFORCE_ORIGIN:"https://workforce.synqoria-clinical.org/",
 SYNQORIA_OIDC_CLIENT_ID:"synqoria-staff",
 SYNQORIA_OIDC_REDIRECT_URI:publicOrigin+"api/auth/oidc/callback",
 SYNQORIA_OIDC_CLIENT_SECRET:"VaultSecret"+"x".repeat(50),
 SYNQORIA_OIDC_FLOW_KEY:"a".repeat(64),SYNQORIA_STAFF_TOKEN_WRAP_KEY:"b".repeat(64),
 SYNQORIA_IDENTITY_AUDIT_PEPPER:"c".repeat(64),
 SYNQORIA_OIDC_API_AUDIENCE:"synqoria-clinical-core",
 SYNQORIA_OIDC_JWKS_JSON:JSON.stringify({keys:[{
  ...publicKey.export({format:"jwk"}),kid:"key-2026",alg:"RS256",use:"sig"
 }]})
};
const expected={
 oidc_replay_state:{
  oidc_replay_runtime_read_live:["r","synqoria_oidc_replay_runtime","expires_at>clock_timestamp()"],
  oidc_replay_runtime_create_live:["a","synqoria_oidc_replay_runtime","expires_at>clock_timestamp() AND expires_at<=clock_timestamp()+'00:05:00'::interval"],
  oidc_replay_runtime_consume_live:["d","synqoria_oidc_replay_runtime","expires_at>clock_timestamp()"],
  oidc_replay_janitor_read_expired:["r","synqoria_oidc_replay_janitor","expires_at<=clock_timestamp()"],
  oidc_replay_janitor_delete_expired:["d","synqoria_oidc_replay_janitor","expires_at<=clock_timestamp()"]
 },
 staff_sessions:{
  staff_session_active_read:["r","synqoria_staff_session_runtime","expires_at>clock_timestamp()"],
  staff_session_new_secure:["a","synqoria_staff_session_runtime","expires_at>clock_timestamp() AND expires_at<=clock_timestamp()+'00:10:00'::interval"],
  staff_session_revoke_once:["d","synqoria_staff_session_runtime","expires_at>clock_timestamp()"],
  staff_session_janitor_read_expired:["r","synqoria_staff_session_janitor","expires_at<=clock_timestamp()"],
  staff_session_janitor_delete_expired:["d","synqoria_staff_session_janitor","expires_at<=clock_timestamp()"]
 },
 staff_security_audit:{
  identity_security_audit_append_only:["a","synqoria_identity_audit_writer","occurred_at<=clock_timestamp()+'00:01:00'::interval AND occurred_at>=clock_timestamp()-'00:02:00'::interval"]
 }
};
const rows=Object.entries(expected).map(([table_name,definitions])=>({
 table_name,row_security:true,force_row_security:true,public_grant:false,
 policies:Object.entries(definitions).map(([name,[command,role,expr]])=>({
  name,command,permissive:true,roles:[role],
  using:command==="a"?null:expr,with_check:command==="a"?expr:null
 }))
}));
const role=role_name=>({role_name,superuser:false,bypass_rls:false,
 create_role:false,create_db:false,row_security:"on",cross_role_member:false});
const client=name=>({query:async sql=>sql.includes("SELECT c.relname AS table_name")?
 {rowCount:3,rows}:{rowCount:1,rows:[role(name)]}});
const options=()=>({env:{...env},
 replaySql:client("synqoria_oidc_replay_runtime"),
 sessionSql:client("synqoria_staff_session_runtime"),
 auditSql:client("synqoria_identity_audit_writer"),
 fetcher:async()=>{throw Error("mock source unavailable");}});
test("B0063 unverified and hand-crafted staff runtime instances cannot enter clinical adapter",()=>{
 assert.throws(()=>createVerifiedClinicalRuntimeBinding({
  authorize:async()=>{},auditWriter:{append:async()=>{}},
  configuration:{publicOrigin,expectedTenantId:tenant,expectedFacilityId:facility}
 },env),ClinicalRuntimeBindingError);
});
test("B0063 positively attested staff runtime supplies only fail-closed clinical read handlers",async()=>{
 const runtime=await createVerifiedStaffIdentityRuntime(options());
 assert.deepEqual({
  publicOrigin:runtime.configuration.publicOrigin,
  tenantId:runtime.configuration.expectedTenantId,
  facilityId:runtime.configuration.expectedFacilityId,
  auditType:typeof runtime.auditWriter?.append,
  authorizeType:typeof runtime.authorize
 },{publicOrigin,tenantId:tenant,facilityId:facility,auditType:"function",authorizeType:"function"});
 let called=0;
 const binding=createVerifiedClinicalRuntimeBinding(runtime,{...env},{
  fetcher:async()=>{called++;throw Error("should not contact clinical backend without auth");}
 });
 assert.equal(Object.isFrozen(binding),true);
 const result=await binding.readFacility(new Request(publicOrigin+"api/clinical/facility"));
 assert.equal(result.status,401);
 assert.equal(called,0);
});
test("B0063 mismatched deployed facility and missing clinical safety settings refuse wiring",async()=>{
 const runtime=await createVerifiedStaffIdentityRuntime(options());
 assert.throws(()=>createVerifiedClinicalRuntimeBinding(runtime,{
  ...env,SYNQORIA_EXPECTED_FACILITY_ID:"33333333-3333-4333-8333-333333333333"
 }),ClinicalRuntimeBindingError);
 assert.throws(()=>createVerifiedClinicalRuntimeBinding(runtime,{
  ...env,SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"0"
 }),ClinicalRuntimeBindingError);
});
