import {installOps001VerifiedRuntime,ops001RuntimeStatus,ops001ServerAction,ops001PublicStatusResponse,Ops001RuntimeInstallationError} from "../apps/web/lib/ops001-runtime-bridge.mjs";
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

const request=(path,method="GET",body)=>new Request(publicOrigin.replace(/\/$/,"")+path,{
 method,headers:{"Host":"staff.synqoria.org","Origin":"https://staff.synqoria.org",
 "Sec-Fetch-Site":"same-origin",...(body?{"Content-Type":"application/json"}:{})},
 ...(body?{body:JSON.stringify(body)}:{})
});
test("OPS001 verified install wires live OIDC redirects, session denial and bounded patient lookup",async()=>{
 const opts=options(),base=opts.replaySql;
 let reservations=0,clinicalCalls=0;
 opts.replaySql={query:async(sql,args)=>{
  if(sql.startsWith("INSERT INTO synqoria_identity.oidc_replay_state")){
   reservations++;
   return {rowCount:1,rows:[{state_key:args[0]}]};
  }
  return base.query(sql,args);
 }};
 const runtime=await createVerifiedStaffIdentityRuntime(opts);
 assert.deepEqual(installOps001VerifiedRuntime(runtime,env,{
  fetcher:async()=>{clinicalCalls++;throw Error("no trusted patient source");}
 }),{ready:true});
 assert.deepEqual(ops001RuntimeStatus(),{ready:true});
 const ready=ops001PublicStatusResponse();
 assert.equal(ready.status,200);
 assert.deepEqual(await ready.json(),{ready:true,clinicalRelease:false});
 const start=await ops001ServerAction("login",request("/api/auth/oidc/start"));
 assert.equal(start.status,303);
 assert.equal(new URL(start.headers.get("location")).origin,new URL(issuer).origin);
 assert.match(start.headers.get("set-cookie"),/__Host-synqoria-oidc-flow=/);
 assert.ok(!start.headers.get("set-cookie").includes("access_token"));
 assert.equal(reservations,1);
 const facilityResult=await ops001ServerAction("facility",request("/api/ops001/facility"));
 assert.equal(facilityResult.status,401);
 const lookupResult=await ops001ServerAction("patient-lookup",request(
  "/api/clinical/patient-lookup","POST",{patientId:tenant}));
 assert.equal(lookupResult.status,401);
 assert.equal(clinicalCalls,0);
 const invalid=await ops001ServerAction("patient-lookup",request(
  "/api/clinical/patient-lookup","POST",{name:"Private patient"}));
 assert.equal(invalid.status,400);
 assert.equal(clinicalCalls,0);
});
test("OPS001 production runtime cannot be swapped after verified registration",()=>{
 assert.throws(()=>installOps001VerifiedRuntime({},env),Ops001RuntimeInstallationError);
});
