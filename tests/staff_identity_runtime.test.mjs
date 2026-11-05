import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {createStaffIdentityRuntime,StaffIdentityRuntimeError} from "../apps/web/lib/staff-identity-runtime.mjs";
import {OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL} from "../apps/web/lib/oidc-replay-postgres.mjs";
import {SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL} from "../apps/web/lib/postgres-staff-session-store.mjs";
import {IDENTITY_AUDIT_INSERT_SQL} from "../apps/web/lib/identity-security-audit.mjs";

const at=Date.now(),seconds=Math.floor(at/1000);
const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const issuer="https://identity.synqoria.org/realms/hospital",publicOrigin="https://staff.synqoria.org/";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",shift="44444444-4444-4444-8444-444444444444";
const env={
 SYNQORIA_OIDC_ISSUER:issuer,
 SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT:issuer+"/protocol/openid-connect/auth",
 SYNQORIA_OIDC_TOKEN_ENDPOINT:issuer+"/protocol/openid-connect/token",
 SYNQORIA_OIDC_INTROSPECTION_ENDPOINT:issuer+"/protocol/openid-connect/token/introspect",
 SYNQORIA_PUBLIC_ORIGIN:publicOrigin,SYNQORIA_WORKFORCE_ORIGIN:"https://workforce.synqoria-clinical.org/",
 SYNQORIA_OIDC_CLIENT_ID:"synqoria-staff",
 SYNQORIA_OIDC_REDIRECT_URI:"https://staff.synqoria.org/api/auth/oidc/callback",
 SYNQORIA_OIDC_CLIENT_SECRET:"VaultSecret"+ "x".repeat(50),
 SYNQORIA_OIDC_FLOW_KEY:"a".repeat(64),
 SYNQORIA_STAFF_TOKEN_WRAP_KEY:"b".repeat(64),
 SYNQORIA_IDENTITY_AUDIT_PEPPER:"c".repeat(64),
 SYNQORIA_OIDC_API_AUDIENCE:"synqoria-clinical-core",
 SYNQORIA_EXPECTED_TENANT_ID:tenant,SYNQORIA_EXPECTED_FACILITY_ID:facility,
 SYNQORIA_OIDC_JWKS_JSON:JSON.stringify({keys:[
  {...publicKey.export({format:"jwk"}),kid:"signing-key",alg:"RS256",use:"sig"}
 ]})
};
const bearer=(claims)=>{
 const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const data=b64({alg:"RS256",kid:"signing-key",typ:"JWT"})+"."+b64(claims);
 return data+"."+sign("sha256",Buffer.from(data),privateKey).toString("base64url");
};
const fmt=x=>new Date(x).toISOString().replace(".000Z","Z");
const json=x=>new Response(JSON.stringify(x),{status:200,headers:{
 "Content-Type":"application/json","Cache-Control":"no-store"
}});
const host="https://staff.synqoria.org";
function harness({active=true,workforceFacility=facility,missingAudit=false}={}){
 const replay=new Map(),sessions=new Map(),auditRows=[],network=[];
 let nonce="";
 const replaySql={query:async(sql,args)=>{
  const key=args[0];
  if(sql===OIDC_REPLAY_RESERVE_SQL){
   if(replay.has(key))return {rowCount:0,rows:[]};
   replay.set(key,at+args[1]);return {rowCount:1,rows:[{state_key:key}]};
  }
  if(sql===OIDC_REPLAY_CONSUME_SQL){
   if(!replay.has(key)||replay.get(key)<=at)return {rowCount:0,rows:[]};
   replay.delete(key);return {rowCount:1,rows:[{state_key:key}]};
  }
  throw Error("untrusted replay SQL");
 }};
 const sessionSql={query:async(sql,args)=>{
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
   const deleted=sessions.delete(args[0]);return {rowCount:Number(deleted),rows:[]};
  }
  throw Error("untrusted session SQL");
 }};
 const auditSql={query:async(sql,args)=>{
  assert.equal(sql,IDENTITY_AUDIT_INSERT_SQL);
  if(missingAudit)throw Error("audit DB offline");
  auditRows.push(args);return {rowCount:1,rows:[]};
 }};
 const fetcher=async(url,init)=>{
  network.push(url);
  if(url===env.SYNQORIA_OIDC_TOKEN_ENDPOINT){
   return json({token_type:"Bearer",expires_in:300,
    id_token:bearer({iss:issuer,sub:"staff-identity-001",aud:"synqoria-staff",
     iat:seconds-5,exp:seconds+300,auth_time:seconds-20,amr:["pwd","otp"],nonce}),
    access_token:bearer({iss:issuer,sub:"staff-identity-001",aud:"synqoria-clinical-core",
     client_id:"synqoria-staff",iat:seconds-5,exp:seconds+300,
     scope:"openid facility:view patient:view"})
   });
  }
  if(url===env.SYNQORIA_OIDC_INTROSPECTION_ENDPOINT){
   return json({active,iss:issuer,sub:"staff-identity-001",aud:"synqoria-clinical-core",
    client_id:"synqoria-staff",exp:seconds+300,token_type:"Bearer"});
  }
  if(url==="https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active"){
   return json({actorId:actor,tenantId:tenant,facilityId:workforceFacility,
    shiftId:shift,role:"nurse",status:"ACTIVE",issuer,subject:"staff-identity-001",
    revision:17,shiftStartsAt:fmt(at-3600000),shiftEndsAt:fmt(at+10800000),
    capabilities:["patient:view","facility:view"]});
  }
  throw Error("unexpected network endpoint");
 };
 const runtime=createStaffIdentityRuntime({env,replaySql,sessionSql,auditSql,fetcher,now:()=>at});
 async function begin(){
  const res=await runtime.http.start(new Request(host+"/api/auth/oidc/start"));
  assert.equal(res.status,303);
  const u=new URL(res.headers.get("location"));nonce=u.searchParams.get("nonce");
  const flow=res.headers.getSetCookie()[0].split(";")[0];
  const callback=host+"/api/auth/oidc/callback?state="+
   encodeURIComponent(u.searchParams.get("state"))+"&code=OpaqueAuthCode123456789012345"+
   "&iss="+encodeURIComponent(issuer);
  return {flow,callback};
 }
 return {runtime,replay,sessions,auditRows,network,begin};
}
test("P03-B0052 trusted runtime composes replay, signed IdP, roster, encrypted session, audit and logout",async()=>{
 const h=harness(),{flow,callback}=await h.begin();
 const done=await h.runtime.http.callback(new Request(callback,{headers:{Cookie:flow}}));
 assert.equal(done.status,303);assert.equal(done.headers.get("location"),"/workforce");
 assert.equal(h.sessions.size,1);
 assert.equal(h.auditRows.length,1);
 assert.equal(h.auditRows[0][3],"LOGIN_GRANTED");
 assert.equal(h.auditRows[0][4],"PERMITTED");
 const s=done.headers.getSetCookie().find(x=>x.startsWith("__Host-synqoria-session="));
 assert.ok(s);
 assert.ok(!s.includes("Bearer "));
 assert.ok(!s.includes(tenant));
 const session=s.split(";")[0];
 const auth=await h.runtime.authorize(new Request(host+"/workforce",{headers:{cookie:session}}));
 assert.equal(auth.actorId,actor);assert.equal(auth.tenantId,tenant);
 assert.deepEqual(auth.capabilities,["patient:view","facility:view"]);
 const end=await h.runtime.http.logout(new Request(host+"/api/auth/logout",{
  method:"POST",headers:{cookie:session,Origin:host}
 }));
 assert.equal(end.status,204);assert.equal(h.sessions.size,0);
 assert.equal(h.auditRows.length,2);
 assert.equal(h.auditRows[1][3],"SESSION_REVOKED");
 assert.equal(h.auditRows[1][4],"PERMITTED");
 assert.equal(h.runtime.configuration.publicOrigin,publicOrigin);
 assert.equal("oidc" in h.runtime.configuration,false);
 assert.equal("clientSecret" in h.runtime.configuration,false);
});
test("P03-B0052 revoked IdP token cannot create staff cookie or audit success",async()=>{
 const h=harness({active:false}),{flow,callback}=await h.begin();
 const denied=await h.runtime.http.callback(new Request(callback,{headers:{cookie:flow}}));
 assert.equal(denied.status,401);assert.equal(h.sessions.size,0);assert.equal(h.auditRows.length,0);
});
test("P03-B0052 missing audit storage rolls back new session without returning browser credentials",async()=>{
 const h=harness({missingAudit:true}),{flow,callback}=await h.begin();
 const denied=await h.runtime.http.callback(new Request(callback,{headers:{cookie:flow}}));
 assert.equal(denied.status,401);assert.equal(h.sessions.size,0);
});
test("P03-B0052 cross-tenant facility mismatch denies login before storage",async()=>{
 const h=harness({workforceFacility:actor}),{flow,callback}=await h.begin();
 const denied=await h.runtime.http.callback(new Request(callback,{headers:{cookie:flow}}));
 assert.equal(denied.status,401);assert.equal(h.sessions.size,0);
});
test("P03-B0052 privilege separation requires three independent SQL clients",()=>{
 const db={query:async()=>({rowCount:0,rows:[]})};
 assert.throws(()=>createStaffIdentityRuntime({env,replaySql:db,sessionSql:db,auditSql:db}),StaffIdentityRuntimeError);
 assert.throws(()=>createStaffIdentityRuntime({env,replaySql:db,sessionSql:{},auditSql:{query:async()=>{}}}),StaffIdentityRuntimeError);
});
