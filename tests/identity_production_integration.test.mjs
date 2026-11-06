/**
 * SYNQORIA B0090–B0092: hermetic, positive composition integration.
 *
 * Real Node crypto and real route/http/SQL adapter code, but simulated
 * PostgreSQL connections, TLS evidence and Keycloak/roster HTTP answers.
 * This is NOT a live clinical deployment, hardware key vault or PG test.
 */
import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {initializeProductionStaffIdentity}
 from "../apps/web/lib/staff-identity-production-startup.mjs";
import {initializeVerifiedStaffRoutes}
 from "../apps/web/lib/staff-route-bootstrap.mjs";
import {dispatchVerifiedStaffAuthRoute}
 from "../apps/web/lib/verified-staff-auth-route.mjs";
import {IDENTITY_CONNECTION_ATTEST_SQL}
 from "../apps/web/lib/identity-attested-db-query.mjs";
import {IDENTITY_ROLE_PROBE_SQL}
 from "../apps/web/lib/identity-db-role-verification.mjs";
import {IDENTITY_SCHEMA_POLICY_SQL}
 from "../apps/web/lib/identity-db-schema-policies.mjs";
import {OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL}
 from "../apps/web/lib/oidc-replay-postgres.mjs";
import {SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL}
 from "../apps/web/lib/postgres-staff-session-store.mjs";
import {IDENTITY_AUDIT_INSERT_SQL}
 from "../apps/web/lib/identity-security-audit.mjs";
const host="https://staff.synqoria.org";
const issuer="https://id.synqoria.org/realms/hospital";
const workforce="https://workforce.synqoria.org";
const at=Date.now(),seconds=Math.floor(at/1000);
const actor="11111111-1111-4111-8111-111111111111";
const tenant="22222222-2222-4222-8222-222222222222";
const facility="33333333-3333-4333-8333-333333333333";
const shift="44444444-4444-4444-8444-444444444444";
const roles=["synqoria_oidc_replay_runtime","synqoria_staff_session_runtime","synqoria_identity_audit_writer"];
const logins=["synqoria_oidc_replay_login","synqoria_staff_session_login","synqoria_identity_audit_login"];
const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const jwk={...publicKey.export({format:"jwk"}),kid:"signing-key",alg:"RS256",use:"sig"};
const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
const signToken=claims=>{
 const data=b64({alg:"RS256",kid:"signing-key",typ:"JWT"})+"."+b64(claims);
 return data+"."+sign("sha256",Buffer.from(data),privateKey).toString("base64url");
};
const iso=ms=>new Date(Math.floor(ms/1000)*1000).toISOString().replace(".000Z","Z");
const PEM="-----BEGIN CERTIFICATE-----\n"+("QUJD".repeat(35))+"\n-----END CERTIFICATE-----\n";
const env={
 SYNQORIA_IDENTITY_BOOTSTRAP_ENABLED:"1",SYNQORIA_RUNTIME_MODE:"staging",
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"1",
 SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN:"1",
 SYNQORIA_REQUIRE_UPSTREAM_NO_STORE:"1",
 SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.synqoria.org/",
 SYNQORIA_OIDC_ISSUER:issuer,
 SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT:issuer+"/protocol/openid-connect/auth",
 SYNQORIA_OIDC_TOKEN_ENDPOINT:issuer+"/protocol/openid-connect/token",
 SYNQORIA_OIDC_INTROSPECTION_ENDPOINT:issuer+"/protocol/openid-connect/token/introspect",
 SYNQORIA_PUBLIC_ORIGIN:host+"/",
 SYNQORIA_WORKFORCE_ORIGIN:workforce+"/",
 SYNQORIA_OIDC_CLIENT_ID:"synqoria-staff",
 SYNQORIA_OIDC_REDIRECT_URI:host+"/api/auth/oidc/callback",
 SYNQORIA_OIDC_CLIENT_SECRET:"Secret"+"x".repeat(60),
 SYNQORIA_OIDC_FLOW_KEY:"a".repeat(64),
 SYNQORIA_STAFF_TOKEN_WRAP_KEY:"b".repeat(64),
 SYNQORIA_IDENTITY_AUDIT_PEPPER:"c".repeat(64),
 SYNQORIA_OIDC_API_AUDIENCE:"synqoria-clinical-core",
 SYNQORIA_EXPECTED_TENANT_ID:tenant,
 SYNQORIA_EXPECTED_FACILITY_ID:facility,
 SYNQORIA_ROLE_POLICY_JSON:JSON.stringify({tenantId:tenant,version:1,roles:{
  nurse:["patient:view","facility:view"]}}),
 SYNQORIA_OIDC_JWKS_JSON:JSON.stringify({keys:[jwk]}),
 SYNQORIA_IDENTITY_DB_CA_PEM:PEM,
 SYNQORIA_IDENTITY_REPLAY_DSN:"postgresql://"+logins[0]+":"+"x".repeat(44)+"@db.synqoria.internal:5432/synqoria_identity",
 SYNQORIA_IDENTITY_SESSION_DSN:"postgresql://"+logins[1]+":"+"y".repeat(44)+"@db.synqoria.internal:5432/synqoria_identity",
 SYNQORIA_IDENTITY_AUDIT_DSN:"postgresql://"+logins[2]+":"+"z".repeat(44)+"@db.synqoria.internal:5432/synqoria_identity"
};
const policies={
 oidc_replay_state:[
 ["oidc_replay_runtime_read_live","r",roles[0]],
 ["oidc_replay_runtime_create_live","a",roles[0]],
 ["oidc_replay_runtime_consume_live","d",roles[0]],
 ["oidc_replay_janitor_read_expired","r","synqoria_oidc_replay_janitor"],
 ["oidc_replay_janitor_delete_expired","d","synqoria_oidc_replay_janitor"]],
 staff_sessions:[
 ["staff_session_active_read","r",roles[1]],
 ["staff_session_new_secure","a",roles[1]],
 ["staff_session_revoke_once","d",roles[1]],
 ["staff_session_janitor_read_expired","r","synqoria_staff_session_janitor"],
 ["staff_session_janitor_delete_expired","d","synqoria_staff_session_janitor"]],
 staff_security_audit:[
 ["identity_security_audit_append_only","a",roles[2]]
 ]};
const predicates={
 oidc_replay_runtime_read_live:"expires_at > clock_timestamp()",
 oidc_replay_runtime_create_live:"expires_at > clock_timestamp() AND expires_at <= clock_timestamp() + '00:05:00'::interval",
 oidc_replay_runtime_consume_live:"expires_at > clock_timestamp()",
 oidc_replay_janitor_read_expired:"expires_at <= clock_timestamp()",
 oidc_replay_janitor_delete_expired:"expires_at <= clock_timestamp()",
 staff_session_active_read:"expires_at > clock_timestamp()",
 staff_session_new_secure:"expires_at > clock_timestamp() AND expires_at <= clock_timestamp() + '00:10:00'::interval",
 staff_session_revoke_once:"expires_at > clock_timestamp()",
 staff_session_janitor_read_expired:"expires_at <= clock_timestamp()",
 staff_session_janitor_delete_expired:"expires_at <= clock_timestamp()",
 identity_security_audit_append_only:"occurred_at <= clock_timestamp() + '00:01:00'::interval AND occurred_at >= clock_timestamp() - '00:02:00'::interval"
};
const policyRows=()=>Object.entries(policies).map(([table_name,items])=>({
 table_name,row_security:true,force_row_security:true,public_grant:false,
 policies:items.map(([name,command,role])=>({
  name,command,permissive:true,roles:[role],
  using:command==="a"?null:predicates[name],
  with_check:command==="a"?predicates[name]:null
 }))
}));
const replay=new Map(),sessions=new Map(),audits=[],events=[],connections=[];
let currentNonce="";
const goodConnection=(role,login)=>({
 role_name:role,login_name:login,row_security:"on",
 superuser:false,bypass_rls:false,create_role:false,create_db:false,cross_role_member:false,
 login_can_login:true,login_inherit:false,login_superuser:false,login_bypass_rls:false,
 login_create_role:false,login_create_db:false,
 login_has_expected_membership:true,login_has_extra_membership:false,
 tls_active:true,tls_version:"TLSv1.3",tls_cipher:"TLS_AES_256_GCM_SHA384"
});
const goodRole=role=>({
 role_name:role,row_security:"on",superuser:false,bypass_rls:false,
 create_role:false,create_db:false,cross_role_member:false
});
class Pool{
 constructor(options){
  this.role=new URL(options.connectionString).username;
  this.options=options;
  connections.push(this);
 }
 async connect(){
  events.push("connect:"+this.role);
  const role=this.role;
  let effectiveRole=null;
  return {
   async query(sql,args){
    if(sql==="BEGIN")return {rowCount:null,rows:[]};
    if(sql.startsWith("SET LOCAL ROLE ")){
     if(sql!=="SET LOCAL ROLE "+roles[logins.indexOf(role)])throw Error("wrong local role");
     effectiveRole=sql.slice("SET LOCAL ROLE ".length);
     return {rowCount:null,rows:[]};
    }
    if(sql==="COMMIT"||sql==="ROLLBACK"){
     effectiveRole=null;return {rowCount:null,rows:[]};
    }
    if(sql===IDENTITY_CONNECTION_ATTEST_SQL)return {rowCount:1,rows:[goodConnection(effectiveRole,role)]};
    if(sql===IDENTITY_ROLE_PROBE_SQL)return {rowCount:1,rows:[goodRole(effectiveRole)]};
    if(sql===IDENTITY_SCHEMA_POLICY_SQL)return {rowCount:3,rows:policyRows()};
    if(sql===OIDC_REPLAY_RESERVE_SQL){
     const key=args[0];
     if(replay.has(key))return {rowCount:0,rows:[]};
     replay.set(key,at+args[1]);return {rowCount:1,rows:[{state_key:key}]};
    }
    if(sql===OIDC_REPLAY_CONSUME_SQL){
     const key=args[0];
     if(!replay.has(key)||replay.get(key)<=at)return {rowCount:0,rows:[]};
     replay.delete(key);return {rowCount:1,rows:[{state_key:key}]};
    }
    if(sql===SESSION_INSERT_SQL){
     if(sessions.has(args[0]))return {rowCount:0,rows:[]};
     sessions.set(args[0],{sealed_record:args[1],expires_at:new Date(args[2])});
     return {rowCount:1,rows:[]};
    }
    if(sql===SESSION_READ_SQL){
     const row=sessions.get(args[0]);
     return row?{rowCount:1,rows:[row]}:{rowCount:0,rows:[]};
    }
    if(sql===SESSION_REVOKE_SQL){
     const removed=sessions.delete(args[0]);
     return {rowCount:Number(removed),rows:[]};
    }
    if(sql===IDENTITY_AUDIT_INSERT_SQL){
     audits.push(args);
     return {rowCount:1,rows:[]};
    }
    throw Error("unexpected identity SQL operation");
   },
   release(){events.push("release:"+role);}
  };
 }
 async end(){events.push("close:"+this.role);}
}
const json=data=>new Response(JSON.stringify(data),{
 status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}
});
const metadata=()=>({
 issuer,
 authorization_endpoint:env.SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT,
 token_endpoint:env.SYNQORIA_OIDC_TOKEN_ENDPOINT,
 introspection_endpoint:env.SYNQORIA_OIDC_INTROSPECTION_ENDPOINT,
 jwks_uri:issuer+"/protocol/openid-connect/certs",
 response_types_supported:["code"],
 code_challenge_methods_supported:["S256"],
 grant_types_supported:["authorization_code"]
});
let active=true;
const network=[];
async function fetcher(url,init){
 network.push({url,method:init.method});
 if(url===issuer+"/.well-known/openid-configuration")return json(metadata());
 if(url===issuer+"/protocol/openid-connect/certs")return json({keys:[jwk]});
 if(url===env.SYNQORIA_OIDC_TOKEN_ENDPOINT){
  return json({token_type:"Bearer",expires_in:300,
   id_token:signToken({iss:issuer,sub:"staff-identity-001",aud:"synqoria-staff",
    iat:seconds-5,exp:seconds+300,auth_time:seconds-20,
    amr:["pwd","otp"],nonce:currentNonce}),
   access_token:signToken({iss:issuer,sub:"staff-identity-001",aud:"synqoria-clinical-core",
    client_id:"synqoria-staff",iat:seconds-5,exp:seconds+300,
    scope:"openid facility:view patient:view"})
  });
 }
 if(url===env.SYNQORIA_OIDC_INTROSPECTION_ENDPOINT){
  return json({active,iss:issuer,sub:"staff-identity-001",aud:"synqoria-clinical-core",
   client_id:"synqoria-staff",exp:seconds+300,token_type:"Bearer"});
 }
 if(url===workforce+"/internal/v1/staff/grants/active"){
  return json({actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,
   role:"nurse",status:"ACTIVE",issuer,subject:"staff-identity-001",
   revision:17,shiftStartsAt:iso(at-3600000),shiftEndsAt:iso(at+10800000),
   capabilities:["patient:view","facility:view"]});
 }
 throw Error("unexpected identity HTTP endpoint");
}
let authenticatedCookie="",callback="",flowCookie="",auditsAtLogin=0;
test("B0090 valid server-only bootstrap attests three roles, TLS, policy and IdP discovery",async()=>{
 const state=await initializeProductionStaffIdentity({env,loadPg:()=>({Pool}),fetcher,now:()=>at});
 assert.deepEqual(state,{enabled:true,ready:true});
 assert.equal(connections.length,3);
 assert.equal(new Set(connections.map(c=>c.role)).size,3);
 assert.ok(connections.every(c=>c.options.ssl.rejectUnauthorized===true));
 assert.ok(events.filter(x=>x.startsWith("connect:")).length>=4);
 assert.deepEqual(network.slice(0,2).map(x=>x.method),["GET","GET"]);
 assert.equal(sessions.size,0);assert.equal(audits.length,0);
});
test("B0091 dynamic Next dispatch completes one-use real-signature OIDC flow and audited ticket",async()=>{
 const start=await dispatchVerifiedStaffAuthRoute("start",
  new Request(host+"/api/auth/oidc/start"));
 assert.equal(start.status,303);
 assert.equal(start.headers.get("cache-control"),"no-store, private");
 const authUrl=new URL(start.headers.get("location"));
 currentNonce=authUrl.searchParams.get("nonce");
 flowCookie=start.headers.getSetCookie()[0].split(";")[0];
 assert.ok(flowCookie.startsWith("__Host-synqoria-oidc-flow="));
 callback=host+"/api/auth/oidc/callback?state="+
   encodeURIComponent(authUrl.searchParams.get("state"))+
   "&code=OpaqueAuthCode123456789012345&iss="+encodeURIComponent(issuer);
 const done=await dispatchVerifiedStaffAuthRoute("callback",
  new Request(callback,{headers:{Cookie:flowCookie}}));
 assert.equal(done.status,303);
 assert.equal(done.headers.get("location"),"/workforce");
 const ticket=done.headers.getSetCookie().find(s=>s.startsWith("__Host-synqoria-session="));
 assert.ok(ticket);
 authenticatedCookie=ticket.split(";")[0];
 assert.doesNotMatch(authenticatedCookie,/Bearer |patient:view|staff-identity/);
 assert.equal(sessions.size,1);
 assert.equal(audits.length,1);
 assert.equal(audits[0][3],"LOGIN_GRANTED");
 auditsAtLogin=audits.length;
 const runtime=await initializeVerifiedStaffRoutes();
 const scope=await runtime.authorize(new Request(host+"/workforce",{
  headers:{Cookie:authenticatedCookie}
 }));
 assert.equal(scope.actorId,actor);assert.equal(scope.tenantId,tenant);
 assert.deepEqual(scope.capabilities,["patient:view","facility:view"]);
 assert.ok(network.some(e=>e.url===env.SYNQORIA_OIDC_INTROSPECTION_ENDPOINT));
 assert.ok(network.some(e=>e.url===workforce+"/internal/v1/staff/grants/active"));
});
test("B0092 callback replay, CSRF logout, revocation and post-revoke reauthorization deny",async()=>{
 const duplicate=await dispatchVerifiedStaffAuthRoute("callback",
  new Request(callback,{headers:{Cookie:flowCookie}}));
 assert.equal(duplicate.status,401);
 assert.equal(sessions.size,1);assert.equal(audits.length,auditsAtLogin);
 const wrongOrigin=await dispatchVerifiedStaffAuthRoute("logout",
  new Request(host+"/api/auth/logout",{method:"POST",
   headers:{Cookie:authenticatedCookie,Origin:"https://evil.org"}}));
 assert.equal(wrongOrigin.status,403);assert.equal(sessions.size,1);
 const cleared=await dispatchVerifiedStaffAuthRoute("logout",
  new Request(host+"/api/auth/logout",{method:"POST",
   headers:{Cookie:authenticatedCookie,Origin:host}}));
 assert.equal(cleared.status,204);
 assert.ok(cleared.headers.get("set-cookie")?.includes("Max-Age=0"));
 assert.equal(sessions.size,0);
 assert.equal(audits.at(-1)[3],"SESSION_REVOKED");
 const runtime=await initializeVerifiedStaffRoutes();
 await assert.rejects(()=>runtime.authorize(new Request(host+"/workforce",{
  headers:{Cookie:authenticatedCookie}
 })));
 active=false;
 const noSession=await dispatchVerifiedStaffAuthRoute("callback",
  new Request(callback,{headers:{Cookie:flowCookie}}));
 assert.equal(noSession.status,401);
 assert.equal(sessions.size,0);
});
