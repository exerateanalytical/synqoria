/**
 * SYNQORIA P03 OIDC replay-state PostgreSQL INTEGRATION tests.
 * Runs against a disposable real PostgreSQL instance in dedicated CI.
 * No hospital, patient, production credentials or PHI data involved.
 * Uses the exact source SQL strings exported by the runtime adapter.
 */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawn,spawnSync} from "node:child_process";
import {OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL} from "../apps/web/lib/oidc-replay-postgres.mjs";

if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")throw Error("PostgreSQL integration requires SYNQORIA_POSTGRES_INTEGRATION=1");
const fixed={PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test"};
const env={...process.env,...fixed,PGOPTIONS:"-c statement_timeout=10000"};
const clientAvailable=spawnSync("psql",["--version"],{encoding:"utf8"}).status===0;
let cmd="psql",args=["-X","-A","-t","-v","ON_ERROR_STOP=1"];
if(!clientAvailable){
 const docker=spawnSync("docker",["ps","--filter","ancestor=postgres:16","--format","{{.ID}}"],{encoding:"utf8"});
 const cid=docker.status===0?docker.stdout.trim().split("\n")[0]:null;
 if(!cid)throw Error("Neither psql nor disposable postgres:16 Docker service is accessible");
 cmd="docker";args=["exec","-i","-e","PGPASSWORD="+fixed.PGPASSWORD,cid,"psql","-X","-A","-t","-v","ON_ERROR_STOP=1","-U",fixed.PGUSER,"-d",fixed.PGDATABASE];
}
const q=(s)=>s.replaceAll("'","''");
const validKey=(suffix)=>"synqoria:oidc:state:v1:"+suffix.repeat(64);
function statement(sql,role=null){
 return role?"SET ROLE "+role+";\n"+sql:sql;
}
function execSql(sql,{fail=false}={}){
 const result=spawnSync(cmd,args,{input:sql,encoding:"utf8",env,timeout:15000,maxBuffer:65536});
 if(fail){assert.notEqual(result.status,0,"SQL unexpectedly succeeded: "+sql);return result.stderr;}
 assert.equal(result.status,0,(result.stderr||result.error?.message||"SQL process failed")+"\n"+sql);
 return result.stdout;
}
function asyncSql(sql){
 return new Promise((resolve,reject)=>{
  const process=spawn(cmd,args,{env,stdio:["pipe","pipe","pipe"]});
  let stdout="",stderr="";
  process.stdout.on("data",chunk=>stdout+=chunk);
  process.stderr.on("data",chunk=>stderr+=chunk);
  process.on("error",reject);
  process.on("close",code=>code===0?resolve(stdout):reject(Error(stderr)));
  process.stdin.end(sql);
 });
}
const reserve=(key,ttl=300000)=>statement(
 "PREPARE reserve(text,bigint) AS "+OIDC_REPLAY_RESERVE_SQL+";\n"+
 "EXECUTE reserve('"+q(key)+"',"+ttl+");","synqoria_oidc_replay_runtime");
const consume=(key)=>statement(
 "PREPARE consume(text) AS "+OIDC_REPLAY_CONSUME_SQL+";\n"+
 "EXECUTE consume('"+q(key)+"');","synqoria_oidc_replay_runtime");
const janitorDelete=(key)=>statement(
 "DELETE FROM synqoria_identity.oidc_replay_state WHERE state_key='"+q(key)+"' RETURNING state_key;",
 "synqoria_oidc_replay_janitor");

test("real identity schema creates forced RLS and only dedicated no-login roles",()=>{
 const schema=readFileSync(new URL("../services/identity/db/oidc_replay_state_candidate.sql",import.meta.url),"utf8");
 const policy=readFileSync(new URL("../services/identity/db/oidc_replay_roles_rls_v1.sql",import.meta.url),"utf8");
 execSql(schema);
 execSql(policy);
 const roles=execSql("SELECT rolname, rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname LIKE 'synqoria_oidc_replay_%' ORDER BY rolname;");
 assert.match(roles,/synqoria_oidc_replay_runtime\|f\|f\|f/);
 assert.match(roles,/synqoria_oidc_replay_janitor\|f\|f\|f/);
 const rls=execSql("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='synqoria_identity.oidc_replay_state'::regclass;");
 assert.match(rls,/t\|t/);
 const count=execSql("SELECT count(*) FROM pg_policies WHERE schemaname='synqoria_identity' AND tablename='oidc_replay_state';");
 assert.equal(count.trim(),"5");
});
test("real SQL atomic reserve, duplicate denial, consume, replay denial",()=>{
 const key=validKey("a");
 const inserted=execSql(reserve(key));
 assert.match(inserted,new RegExp(key));
 assert.match(inserted,/INSERT 0 1/);
 const duplicate=execSql(reserve(key));
 assert.doesNotMatch(duplicate,new RegExp(key));
 assert.match(duplicate,/INSERT 0 0/);
 const consumed=execSql(consume(key));
 assert.match(consumed,new RegExp(key));
 assert.match(consumed,/DELETE 1/);
 const replay=execSql(consume(key));
 assert.doesNotMatch(replay,new RegExp(key));
 assert.match(replay,/DELETE 0/);
});
test("expired state is not consumable; janitor cannot delete live states",()=>{
 const stale=validKey("b"),live=validKey("c");
 execSql("INSERT INTO synqoria_identity.oidc_replay_state(state_key,expires_at) VALUES ('"+stale+"',clock_timestamp()-interval '5 seconds');");
 assert.match(execSql(consume(stale)),/DELETE 0/);
 assert.match(execSql(janitorDelete(stale)),/DELETE 1/);
 execSql(reserve(live));
 assert.match(execSql(janitorDelete(live)),/DELETE 0/);
 assert.match(execSql(consume(live)),/DELETE 1/);
});
test("least privilege denies untrusted role and janitor write privileges",()=>{
 execSql("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_oidc_replay_untrusted') THEN CREATE ROLE synqoria_oidc_replay_untrusted NOLOGIN NOBYPASSRLS; END IF; END $$;");
 assert.match(execSql(statement("SELECT state_key FROM synqoria_identity.oidc_replay_state;","synqoria_oidc_replay_untrusted"),{fail:true}),/permission denied/i);
 const key=validKey("d");
 assert.match(execSql(statement("INSERT INTO synqoria_identity.oidc_replay_state(state_key,expires_at) VALUES ('"+key+"',clock_timestamp()+interval '1 minute');","synqoria_oidc_replay_janitor"),{fail:true}),/permission denied/i);
});
test("out-of-policy TTL cannot insert into real database",()=>{
 const key=validKey("e");
 assert.match(execSql(reserve(key,300001)),/INSERT 0 0/);
 assert.match(execSql(reserve(key,-1)),/INSERT 0 0/);
 const illegal=statement("INSERT INTO synqoria_identity.oidc_replay_state(state_key,expires_at) VALUES ('"+key+"',clock_timestamp()+interval '10 minutes');","synqoria_oidc_replay_runtime");
 assert.match(execSql(illegal,{fail:true}),/row-level security/i);
});
test("12 parallel, independent PostgreSQL sessions reserve one state exactly once",async()=>{
 const key=validKey("f");
 const results=await Promise.all(Array.from({length:12},()=>asyncSql(reserve(key))));
 assert.equal(results.filter(x=>x.includes("INSERT 0 1")).length,1);
 assert.equal(results.filter(x=>x.includes("INSERT 0 0")).length,11);
 assert.match(execSql(consume(key)),/DELETE 1/);
});
