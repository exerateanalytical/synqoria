/**
 * P03-B0102..B0105 actual PostgreSQL 16 TCP/TLS integration.
 * Disposable CI identity database with CA-signed server, three restricted
 * LOGIN principals and three NOLOGIN FORCE-RLS runtime roles.
 * Not a deployed hospital; no real staff or patient data.
 */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {postgresOidcReplayStore} from "../apps/web/lib/oidc-replay-postgres.mjs";
import {createRequire} from "node:module";
import {execFileSync} from "node:child_process";
import {createRestrictedIdentityPools,IdentityProductionSqlError}
 from "../apps/web/lib/identity-production-sql.mjs";
import {verifyIdentitySqlClients} from "../apps/web/lib/identity-db-role-verification.mjs";
import {verifyIdentitySchemaPolicies} from "../apps/web/lib/identity-db-schema-policies.mjs";
import {IdentityAttestedQueryError,createConnectionAttestedSqlClient} from "../apps/web/lib/identity-attested-db-query.mjs";
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
test("B0106 role elevation is transaction-local and resets before the same backend returns to its pool",async()=>{
 const rawPool=new Pool({connectionString:dsn(0),ssl:{ca,rejectUnauthorized:true,servername:host},max:1});
 try{
  const scoped=createConnectionAttestedSqlClient(rawPool,
   "synqoria_oidc_replay_runtime",login[0]);
  const actor=(await scoped.query("SELECT current_user AS active_role,session_user AS login_role")).rows[0].active_role;
  assert.equal(actor,"synqoria_oidc_replay_runtime");
  const raw=await rawPool.connect();
  try{
   assert.equal((await raw.query("SELECT current_user AS login_role")).rows[0].login_role,login[0]);
   await assert.rejects(()=>raw.query("SELECT state_key FROM synqoria_identity.oidc_replay_state"));
  }finally{raw.release();}
 }finally{await rawPool.end();}
});
test("B0107 a wrong pinned CA never becomes an authorized identity TLS session",async()=>{
 const wrongCa="-----BEGIN CERTIFICATE-----\nQUJD\n-----END CERTIFICATE-----\n";
 const client=new Client({connectionString:dsn(0),
  ssl:{ca:wrongCa,rejectUnauthorized:true,servername:host},
  connectionTimeoutMillis:1500});
 await assert.rejects(()=>client.connect());
 try{await client.end();}catch{/* connection never established */}
});
test("B0108 PostgreSQL might accept cleartext, but identity runtime refuses an unauthenticated socket",async()=>{
 const rawPool=new Pool({connectionString:dsn(0),ssl:false,max:1,connectionTimeoutMillis:2000});
 try{
  const scoped=createConnectionAttestedSqlClient(rawPool,
   "synqoria_oidc_replay_runtime",login[0]);
  await assert.rejects(()=>scoped.query("SELECT 1 AS ok"),IdentityAttestedQueryError);
 }finally{await rawPool.end();}
});
test("B0109 swapped PostgreSQL login principal with another role's secret fails authentication",async()=>{
 const swapped=dsn(0).replace(login[0],login[1]);
 const client=new Client({connectionString:swapped,ssl:{ca,rejectUnauthorized:true,servername:host},
  connectionTimeoutMillis:2000});
 await assert.rejects(()=>client.connect());
 try{await client.end();}catch{/* denied authentication */}
});

test("B0105 privilege drift on LOGIN principal is detected on subsequent physical SQL connection",async()=>{
 admin("GRANT synqoria_staff_session_runtime TO synqoria_oidc_replay_login");
 try{
  await assert.rejects(()=>pools.replaySql.query("SELECT 1 AS ok"),IdentityAttestedQueryError);
 }finally{admin("REVOKE synqoria_staff_session_runtime FROM synqoria_oidc_replay_login");}
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
 admin("ALTER ROLE synqoria_oidc_replay_login BYPASSRLS");
 try{
  await assert.rejects(()=>pools.replaySql.query("SELECT 1 AS ok"),IdentityAttestedQueryError);
 }finally{admin("ALTER ROLE synqoria_oidc_replay_login NOBYPASSRLS");}
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
});

test("B0117 PostgreSQL16 changing INHERIT or SET grant options cannot bypass identity admission",async()=>{
 for(const mode of ["INHERIT TRUE, SET TRUE","INHERIT FALSE, SET FALSE"]){
  admin("REVOKE synqoria_oidc_replay_runtime FROM synqoria_oidc_replay_login");
  admin("GRANT synqoria_oidc_replay_runtime TO synqoria_oidc_replay_login WITH ADMIN FALSE, "+mode);
  try{await assert.rejects(()=>pools.replaySql.query("SELECT 1 AS ok"),IdentityAttestedQueryError);}
  finally{
   admin("REVOKE synqoria_oidc_replay_runtime FROM synqoria_oidc_replay_login");
   admin("GRANT synqoria_oidc_replay_runtime TO synqoria_oidc_replay_login WITH ADMIN FALSE, INHERIT FALSE, SET TRUE");
  }
 }
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
});
test("B0118 PostgreSQL16 replication, hidden runtime memberships, or role privileges deny",async()=>{
 admin("ALTER ROLE synqoria_oidc_replay_login REPLICATION");
 try{await assert.rejects(()=>pools.replaySql.query("SELECT 1 AS ok"),IdentityAttestedQueryError);}
 finally{admin("ALTER ROLE synqoria_oidc_replay_login NOREPLICATION");}
 admin("GRANT pg_read_all_data TO synqoria_oidc_replay_runtime");
 try{await assert.rejects(()=>pools.replaySql.query("SELECT 1 AS ok"),IdentityAttestedQueryError);}
 finally{admin("REVOKE pg_read_all_data FROM synqoria_oidc_replay_runtime");}
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
});
test("B0120 SQL contracts deny arbitrary PostgreSQL sleep before executing it",async()=>{
 const started=Date.now();
 await assert.rejects(()=>pools.replaySql.query("SELECT pg_sleep(6)"),IdentityAttestedQueryError);
 assert.ok(Date.now()-started<1000,"unauthorized sleep must be denied before DB acquisition");
 assert.equal((await pools.replaySql.query("SELECT 1 AS ok")).rows[0].ok,1);
});
test("B0121 live RLS policy predicate drift denies preflight until corrected",async()=>{
 admin("ALTER POLICY oidc_replay_runtime_read_live ON synqoria_identity.oidc_replay_state USING (true)");
 try{await assert.rejects(()=>verifyIdentitySchemaPolicies(pools.replaySql));}
 finally{admin("ALTER POLICY oidc_replay_runtime_read_live ON synqoria_identity.oidc_replay_state USING (expires_at > clock_timestamp())");}
 assert.equal((await verifyIdentitySchemaPolicies(pools.replaySql)).verified,true);
});
test("B0122 disposable PG16 OIDC replay is atomic across eight concurrent clients",async()=>{
 const at=Date.now();
 const other=await createRestrictedIdentityPools({env,Pool});
 try{
  const stores=[postgresOidcReplayStore(pools.replaySql,{now:()=>at}),
   postgresOidcReplayStore(other.replaySql,{now:()=>at})];
  const state="synqoria:oidc:state:v1:"+"d".repeat(64);
  const attempts=await Promise.all(Array.from({length:8},(_,i)=>
   stores[i%2].reserve(state,at+120000)));
  assert.equal(attempts.filter(Boolean).length,1);
  const consumes=await Promise.all(Array.from({length:8},(_,i)=>
   stores[i%2].consume(state,at)));
  assert.equal(consumes.filter(Boolean).length,1);
  assert.equal(await stores[0].consume(state,at),false);
 }finally{await other.close();}
});

test("P03 disposable PostgreSQL role/TLS suite closes all restricted pools",async()=>{await pools.close();});
