import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawnSync} from "node:child_process";
import {IDENTITY_ROLE_PROBE_SQL,verifyIdentitySqlClients,IdentityRoleVerificationError}
 from "../apps/web/lib/identity-db-role-verification.mjs";

if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")
 throw Error("Requires a disposable PostgreSQL 16 service");
const env={...process.env,PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",
 PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test",
 PGOPTIONS:"-c statement_timeout=10000"};
const cmd="psql",args=["-X","-A","-t","-v","ON_ERROR_STOP=1"];
function execSql(source,{fail=false}={}){
 const out=spawnSync(cmd,args,{input:source,encoding:"utf8",env,timeout:15000,maxBuffer:65536});
 if(fail){assert.notEqual(out.status,0);return out.stderr;}
 assert.equal(out.status,0,(out.stderr||"SQL failure")+"\n"+source);
 return out.stdout;
}
const roles=["synqoria_oidc_replay_runtime","synqoria_staff_session_runtime","synqoria_identity_audit_writer"];
const headers=["role_name","superuser","bypass_rls","create_role","create_db","row_security","cross_role_member"];
function client(role){
 return {query:async(sql,params)=>{
  assert.equal(sql,IDENTITY_ROLE_PROBE_SQL);assert.deepEqual(params,[]);
  const output=execSql("SET ROLE "+role+";\n"+sql+";");
  const row=output.trim().split("\n").find(s=>s.split("|").length===7);
  assert.ok(row,"expected canonical PostgreSQL role result");
  const values=row.split("|");
  return {rowCount:1,rows:[Object.fromEntries(headers.map((name,i)=>[
   name,i===0||i===5?values[i]:values[i]==="t"
  ]))]};
 }};
}
test("P03-B0053 real PostgreSQL role probe passes independent least-privilege identities",async()=>{
 for(const file of ["oidc_replay_state_candidate.sql","oidc_replay_roles_rls_v1.sql",
   "staff_sessions_v1.sql","staff_security_audit_v1.sql"]){
  execSql(readFileSync(new URL("../services/identity/db/"+file,import.meta.url),"utf8"));
 }
 const result=await verifyIdentitySqlClients({
  replaySql:client(roles[0]),sessionSql:client(roles[1]),auditSql:client(roles[2])
 });
 assert.equal(result.verified,true);
 assert.equal(result.roles.replay,roles[0]);
 assert.equal(result.roles.session,roles[1]);
 assert.equal(result.roles.audit,roles[2]);
});
test("P03-B0053 privileged DBA, wrong current_user and forced row_security=off fail closed",async()=>{
 const invalid={
  replaySql:client("postgres"),sessionSql:client(roles[1]),auditSql:client(roles[2])
 };
 await assert.rejects(()=>verifyIdentitySqlClients(invalid),IdentityRoleVerificationError);
 const disabled={query:async(sql)=>{
  const output=execSql("SET ROLE "+roles[0]+";\nSET row_security = off;\n"+sql+";");
  const row=output.trim().split("\n").find(s=>s.split("|").length===7);
  const values=row.split("|");
  return {rowCount:1,rows:[Object.fromEntries(headers.map((k,i)=>[
   k,i===0||i===5?values[i]:values[i]==="t"
  ]))]};
 }};
 await assert.rejects(()=>verifyIdentitySqlClients({
  replaySql:disabled,sessionSql:client(roles[1]),auditSql:client(roles[2])
 }),IdentityRoleVerificationError);
});
test("P03-B0053 forbidden identity role membership is detected from real catalog",async()=>{
 execSql("GRANT synqoria_staff_session_runtime TO synqoria_oidc_replay_runtime;");
 try{
  await assert.rejects(()=>verifyIdentitySqlClients({
   replaySql:client(roles[0]),sessionSql:client(roles[1]),auditSql:client(roles[2])
  }),IdentityRoleVerificationError);
 }finally{
  execSql("REVOKE synqoria_staff_session_runtime FROM synqoria_oidc_replay_runtime;");
 }
});
