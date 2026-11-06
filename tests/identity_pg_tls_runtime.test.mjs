/**
 * P03-B0102..B0105 actual PostgreSQL 16 TCP/TLS integration.
 * Disposable CI identity database with CA-signed server, three restricted
 * LOGIN principals and three NOLOGIN FORCE-RLS runtime roles.
 * Not a deployed hospital; no real staff or patient data.
 */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {createRequire} from "node:module";
import {execFileSync} from "node:child_process";
import {createRestrictedIdentityPools,IdentityProductionSqlError}
 from "../apps/web/lib/identity-production-sql.mjs";
import {verifyIdentitySqlClients} from "../apps/web/lib/identity-db-role-verification.mjs";
import {verifyIdentitySchemaPolicies} from "../apps/web/lib/identity-db-schema-policies.mjs";
import {IdentityAttestedQueryError,IDENTITY_CONNECTION_ATTEST_SQL} from "../apps/web/lib/identity-attested-db-query.mjs";
if(process.env.SYNQORIA_POSTGRES_TLS_TEST!=="1")
 throw Error("Requires isolated TLS PostgreSQL 16 CI database");
const {Pool,Client}=createRequire(import.meta.url)("pg");
const host="pg-ci.synqoria.org";
const db="synqoria_ci";
const login=[
 "synqoria_oidc_replay_login","synqoria_staff_session_login",
 "synqoria_identity_audit_login"
];
const password=["x","y","z"].map(c=>c.repeat(44));
const ca=readFileSync(process.env.SYNQORIA_TEST_CA_PATH,"utf8");
const dsn=i=>"postgresql://"+login[i]+":"+password[i]+"@"+host+":5432/"+db;
const env={
 SYNQORIA_IDENTITY_DB_CA_PEM:ca,
 SYNQORIA_IDENTITY_REPLAY_DSN:dsn(0),
 SYNQORIA_IDENTITY_SESSION_DSN:dsn(1),
 SYNQORIA_IDENTITY_AUDIT_DSN:dsn(2)
};
const admin=sql=>{
 const output=execFileSync("docker",["exec","synqoria-p03-pg","psql",
 "-X","-A","-t","-v","ON_ERROR_STOP=1","-U","postgres","-d",db,"-c",sql],{
  encoding:"utf8",timeout:15000
 });
 return output.trim();
};
let pools;
test("B0102 actual TLS PostgreSQL 16 server and 3 LOGIN principals satisfy NOLOGIN role gate",async()=>{
 pools=await createRestrictedIdentityPools({env,Pool});
 const diagnostic=new Client({connectionString:dsn(0),ssl:{ca,rejectUnauthorized:true,servername:host}});
 await diagnostic.connect();
 try{
  await diagnostic.query("BEGIN");
  await diagnostic.query("SET LOCAL ROLE synqoria_oidc_replay_runtime");
  const raw=await diagnostic.query(IDENTITY_CONNECTION_ATTEST_SQL);
  console.log("CI disposable identity attestation fields:",raw.rows[0]||{rowCount:raw.rowCount});
  await diagnostic.query("ROLLBACK");
 }catch(error){
  console.log("CI disposable identity SQLSTATE:",error.code,error.message);
  throw error;
 }finally{await diagnostic.end();}
 const roles=await verifyIdentitySqlClients(pools);
 assert.equal(roles.verified,true);
 assert.equal(roles.roles.replay,"synqoria_oidc_replay_runtime");
 const policies=await verifyIdentitySchemaPolicies(pools.replaySql);
 assert.equal(policies.verified,true);
 const sql="SELECT current_user AS active_role,session_user AS login_role";
 for(const [i,client,expected] of [
  [0,pools.replaySql,"synqoria_oidc_replay_runtime"],
  [1,pools.sessionSql,"synqoria_staff_session_runtime"],
  [2,pools.auditSql,"synqoria_identity_audit_writer"]
 ]){
  const row=(await client.query(sql)).rows[0];
  assert.equal(row.active_role,expected);
  assert.equal(row.login_role,login[i]);
 }
});
test("B0103 login principal alone cannot directly inherit NOLOGIN role table privileges",async()=>{
 const raw=new Client({connectionString:dsn(0),ssl:{ca,rejectUnauthorized:true,servername:host}});
 await raw.connect();
 try{
  assert.equal((await raw.query("SELECT current_user")).rows[0].current_user,login[0]);
  await assert.rejects(()=>raw.query("SELECT state_key FROM synqoria_identity.oidc_replay_state"));
 }finally{await raw.end();}
});
test("B0104 runtime scopes deny read access to other identity tables, including audit data",async()=>{
 await assert.rejects(()=>pools.replaySql.query(
  "SELECT session_digest FROM synqoria_identity.staff_sessions"),IdentityAttestedQueryError);
 await assert.rejects(()=>pools.sessionSql.query(
  "SELECT state_key FROM synqoria_identity.oidc_replay_state"),IdentityAttestedQueryError);
 await assert.rejects(()=>pools.auditSql.query(
  "SELECT actor_digest FROM synqoria_identity.staff_security_audit"),IdentityAttestedQueryError);
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
});
test("B0105 privilege drift on LOGIN principal is detected on subsequent physical SQL connection",async()=>{
 admin("GRANT synqoria_staff_session_runtime TO synqoria_oidc_replay_login");
 try{
  await assert.rejects(()=>pools.replaySql.query("SELECT 1"),IdentityAttestedQueryError);
 }finally{admin("REVOKE synqoria_staff_session_runtime FROM synqoria_oidc_replay_login");}
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
 admin("ALTER ROLE synqoria_oidc_replay_login BYPASSRLS");
 try{
  await assert.rejects(()=>pools.replaySql.query("SELECT 1"),IdentityAttestedQueryError);
 }finally{admin("ALTER ROLE synqoria_oidc_replay_login NOBYPASSRLS");}
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
 await pools.close();
});
