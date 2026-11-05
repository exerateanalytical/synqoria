/** P03-B0043: validate identity security audit against real disposable Postgres. */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawnSync} from "node:child_process";
import {randomUUID} from "node:crypto";
import {IDENTITY_AUDIT_INSERT_SQL} from "../apps/web/lib/identity-security-audit.mjs";
if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")throw Error("Requires disposable PostgreSQL integration DB");
const env={...process.env,PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",
 PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test",
 PGOPTIONS:"-c statement_timeout=10000"};
let cmd="psql",args=["-X","-A","-t","-v","ON_ERROR_STOP=1"];
if(spawnSync("psql",["--version"],{encoding:"utf8"}).status!==0){
 const d=spawnSync("docker",["ps","--filter","ancestor=postgres:16","--format","{{.ID}}"],{encoding:"utf8"});
 const container=d.status===0?d.stdout.trim().split("\n")[0]:null;
 if(!container)throw Error("No disposable Postgres 16 service");
 cmd="docker";args=["exec","-i","-e","PGPASSWORD="+env.PGPASSWORD,container,
  "psql","-X","-A","-t","-v","ON_ERROR_STOP=1","-U","postgres","-d",env.PGDATABASE];
}
const run=(sql,{fail=false}={})=>{
 const out=spawnSync(cmd,args,{input:sql,encoding:"utf8",env,timeout:16000,maxBuffer:65536});
 if(fail){assert.notEqual(out.status,0,"Must deny SQL: "+sql);return out.stderr;}
 assert.equal(out.status,0,(out.stderr||out.error?.message||"DB error")+"\n"+sql);
 return out.stdout;
};
const esc=s=>s.replaceAll("'","''");
const staffRole="SET ROLE synqoria_identity_audit_writer;\n";
const sqlArgs=(eventId,kind="LOGIN_GRANTED",outcome="PERMITTED",timestamp=Date.now())=>[
 "'"+eventId+"'","'"+ "a".repeat(64)+"'","'"+ "b".repeat(64)+"'",
 "'"+esc(kind)+"'","'"+esc(outcome)+"'","'"+randomUUID()+"'",String(timestamp)
].join(",");
const append=(eventId,kind,outcome,at)=>staffRole+
 "PREPARE identity_audit(uuid,text,text,text,text,uuid,bigint) AS "+IDENTITY_AUDIT_INSERT_SQL+";\n"+
 "EXECUTE identity_audit("+sqlArgs(eventId,kind,outcome,at)+");";

test("P03-B0043 real audit table has insert-only NOLOGIN role and forced RLS",()=>{
 run("CREATE SCHEMA IF NOT EXISTS synqoria_identity;");
 const schema=readFileSync(new URL("../services/identity/db/staff_security_audit_v1.sql",import.meta.url),"utf8");
 run(schema);
 assert.match(run("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='synqoria_identity.staff_security_audit'::regclass;"),/t\|t/);
 assert.match(run("SELECT rolcanlogin,rolbypassrls FROM pg_roles WHERE rolname='synqoria_identity_audit_writer';"),/f\|f/);
 assert.equal(run("SELECT count(*) FROM pg_policies WHERE schemaname='synqoria_identity' AND tablename='staff_security_audit';").trim(),"1");
});
test("P03-B0043 real Postgres append accepts only enum metadata and pseudonymous digests",()=>{
 const id=randomUUID();
 assert.match(run(append(id)),/INSERT 0 1/);
 const stored=run("SELECT kind,outcome FROM synqoria_identity.staff_security_audit WHERE event_id='"+id+"';");
 assert.match(stored,/LOGIN_GRANTED\|PERMITTED/);
 assert.match(run(append(randomUUID(),"LOGIN_DENIED","DENIED")) ,/INSERT 0 1/);
});
test("P03-B0043 writer cannot SELECT, DELETE or UPDATE historical metadata",()=>{
 for(const sql of [
  staffRole+"SELECT event_id FROM synqoria_identity.staff_security_audit;",
  staffRole+"DELETE FROM synqoria_identity.staff_security_audit;",
  staffRole+"UPDATE synqoria_identity.staff_security_audit SET outcome='DENIED';"
 ])assert.match(run(sql,{fail:true}),/permission denied/i);
});
test("P03-B0043 illegal event and stale timestamp reject without bypass",()=>{
 assert.match(run(append(randomUUID(),"CLINICAL_CONTENT_EXPORTED","PERMITTED"),{fail:true}),/check constraint|value too long/i);
 assert.match(run(append(randomUUID(),"LOGIN_GRANTED","PERMITTED",Date.now()-1200000),{fail:true}),/row-level security/i);
});
