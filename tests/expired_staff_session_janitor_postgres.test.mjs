/** SYNQORIA P03-B0046: test the expired-session janitor against PostgreSQL 16. */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawnSync,spawn} from "node:child_process";
import {STAFF_SESSION_SWEEP_SQL} from "../apps/web/lib/expired-staff-session-janitor.mjs";

if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")throw Error("Requires disposable test PostgreSQL");
const env={...process.env,PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",
 PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test",
 PGOPTIONS:"-c statement_timeout=10000"};
let cmd="psql",args=["-X","-A","-t","-v","ON_ERROR_STOP=1"];
if(spawnSync("psql",["--version"],{encoding:"utf8"}).status!==0){
 const d=spawnSync("docker",["ps","--filter","ancestor=postgres:16","--format","{{.ID}}"],{encoding:"utf8"});
 const cid=d.status===0?d.stdout.trim().split("\n")[0]:null;
 if(!cid)throw Error("No PostgreSQL 16 test container available");
 cmd="docker";args=["exec","-i","-e","PGPASSWORD="+env.PGPASSWORD,cid,
  "psql","-X","-A","-t","-v","ON_ERROR_STOP=1","-U","postgres","-d",env.PGDATABASE];
}
function sql(statement,{fail=false}={}){
 const p=spawnSync(cmd,args,{input:statement,encoding:"utf8",env,timeout:16000,maxBuffer:65536});
 if(fail){assert.notEqual(p.status,0,"Expected database denial");return p.stderr;}
 assert.equal(p.status,0,(p.stderr||p.error?.message||"Database error")+"\n"+statement);
 return p.stdout;
}
const asyncSql=statement=>new Promise((resolve,reject)=>{
 const p=spawn(cmd,args,{env,stdio:["pipe","pipe","pipe"]});
 let out="",err="";
 p.stdout.on("data",x=>out+=x);p.stderr.on("data",x=>err+=x);
 p.on("error",reject);p.on("close",code=>code===0?resolve(out):reject(Error(err)));
 p.stdin.end(statement);
});
const role=statement=>"SET ROLE synqoria_staff_session_janitor;\n"+statement;
const sweep=limit=>role("PREPARE sweep(int) AS "+STAFF_SESSION_SWEEP_SQL+";\nEXECUTE sweep("+limit+");");

test("real PostgreSQL creates expiry-only NOLOGIN, NO-BYPASSRLS janitor",()=>{
 for(const file of ["oidc_replay_state_candidate.sql","oidc_replay_roles_rls_v1.sql",
  "staff_sessions_v1.sql","staff_session_janitor_v1.sql"]){
  sql(readFileSync(new URL("../services/identity/db/"+file,import.meta.url),"utf8"));
 }
 assert.match(sql("SELECT rolcanlogin,rolbypassrls FROM pg_roles WHERE rolname='synqoria_staff_session_janitor';"),/f\|f/);
 assert.match(sql("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='synqoria_identity.staff_sessions'::regclass;"),/t\|t/);
 const policy=sql("SELECT count(*) FROM pg_policies WHERE schemaname='synqoria_identity' AND tablename='staff_sessions' AND policyname LIKE 'staff_session_janitor_%';");
 assert.equal(policy.trim(),"2");
});
test("janitor cannot SELECT active tickets or encrypted payload, or UPDATE, INSERT, or destroy active state",()=>{
 sql("INSERT INTO synqoria_identity.staff_sessions(session_digest,sealed_record,created_at,expires_at) VALUES "+
  "('"+ "a".repeat(64)+"','v1.aaa.bbb.ccc',clock_timestamp(),clock_timestamp()+INTERVAL '5 minutes'),"+
  "('"+ "b".repeat(64)+"','v1.aaa.bbb.ccc',clock_timestamp()-INTERVAL '10 minutes',clock_timestamp()-INTERVAL '9 minutes'),"+
  "('"+ "c".repeat(64)+"','v1.aaa.bbb.ccc',clock_timestamp()-INTERVAL '10 minutes',clock_timestamp()-INTERVAL '8 minutes');");
 assert.equal(sql(role("SELECT count(*) FROM synqoria_identity.staff_sessions;")).trim().endsWith("2"),true);
 assert.match(sql(role("SELECT sealed_record FROM synqoria_identity.staff_sessions;"),{fail:true}),/permission denied/i);
 assert.match(sql(role("UPDATE synqoria_identity.staff_sessions SET sealed_record='v1.aaa.bbb.ccc';"),{fail:true}),/permission denied/i);
 assert.match(sql(role("INSERT INTO synqoria_identity.staff_sessions(session_digest,sealed_record,expires_at) VALUES ('"+ "d".repeat(64)+"','v1.aaa.bbb.ccc',clock_timestamp()+INTERVAL '2 minutes');"),{fail:true}),/permission denied/i);
 assert.equal(sql(role("DELETE FROM synqoria_identity.staff_sessions WHERE session_digest='"+ "a".repeat(64)+"';")).includes("DELETE 0"),true);
 assert.match(sql(sweep(1)),new RegExp("[bc]".repeat(0)+"[bc]{64}"));
});
test("one bounded sweep removes remaining expired only; active session persists",()=>{
 const result=sql(sweep(100));
 assert.match(result,/^[\s\S]*[bc]{64}[\s\S]*DELETE 1/);
 assert.match(sql(sweep(100)),/DELETE 0/);
 assert.equal(sql("SELECT count(*) FROM synqoria_identity.staff_sessions WHERE session_digest='"+ "a".repeat(64)+"';").trim(),"1");
});
test("separate concurrent janitor connections may not consume one expired row twice",async()=>{
 sql("INSERT INTO synqoria_identity.staff_sessions(session_digest,sealed_record,created_at,expires_at) VALUES "+
  "('"+ "e".repeat(64)+"','v1.aaa.bbb.ccc',clock_timestamp()-INTERVAL '10 minutes',clock_timestamp()-INTERVAL '9 minutes');");
 const runs=await Promise.all(Array.from({length:6},()=>asyncSql(sweep(1))));
 assert.equal(runs.filter(x=>x.includes("DELETE 1")).length,1);
 assert.equal(runs.filter(x=>x.includes("DELETE 0")).length,5);
 assert.equal(sql("SELECT count(*) FROM synqoria_identity.staff_sessions WHERE session_digest='"+ "e".repeat(64)+"';").trim(),"0");
});
