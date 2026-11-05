/** Real PostgreSQL 16 CI integration: P03-B0037 staff-session RLS and revocation. */
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawn,spawnSync} from "node:child_process";
import {randomBytes} from "node:crypto";
import {sealOpaqueStaffTicket} from "../apps/web/lib/opaque-staff-session.mjs";
import {SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL} from "../apps/web/lib/postgres-staff-session-store.mjs";

if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")throw Error("Requires disposable PostgreSQL identity test DB");
const cfg={PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test"};
const env={...process.env,...cfg,PGOPTIONS:"-c statement_timeout=10000"};
let cmd="psql",args=["-X","-A","-t","-v","ON_ERROR_STOP=1"];
if(spawnSync("psql",["--version"],{encoding:"utf8"}).status!==0){
 const d=spawnSync("docker",["ps","--filter","ancestor=postgres:16","--format","{{.ID}}"],{encoding:"utf8"});
 const container=d.status===0?d.stdout.trim().split("\n")[0]:null;
 if(!container)throw Error("Test PostgreSQL cannot be reached");
 cmd="docker";args=["exec","-i","-e","PGPASSWORD="+cfg.PGPASSWORD,container,
  "psql","-X","-A","-t","-v","ON_ERROR_STOP=1","-U","postgres","-d",cfg.PGDATABASE];
}
const run=(text,{fail=false}={})=>{
 const p=spawnSync(cmd,args,{input:text,encoding:"utf8",env,timeout:16000,maxBuffer:65536});
 if(fail){assert.notEqual(p.status,0,"Must have denied: "+text);return p.stderr;}
 assert.equal(p.status,0,(p.stderr||p.error?.message||"PostgreSQL failed")+"\n"+text);
 return p.stdout;
};
const parallel=text=>new Promise((resolve,reject)=>{
 const p=spawn(cmd,args,{env,stdio:["pipe","pipe","pipe"]});let out="",err="";
 p.stdout.on("data",x=>out+=x);p.stderr.on("data",x=>err+=x);
 p.on("error",reject);p.on("close",code=>code===0?resolve(out):reject(Error(err)));
 p.stdin.end(text);
});
const esc=s=>s.replaceAll("'","''");
const runtime=sql=>"SET ROLE synqoria_staff_session_runtime;\n"+sql;
const prepared=(name,sql,types,values)=>"PREPARE "+name+"("+types+") AS "+sql+";\nEXECUTE "+name+"("+values+");";
const insert=(s)=>runtime(prepared("create_staff",SESSION_INSERT_SQL,
 "text,text,bigint",["'"+s.digest+"'","'"+esc(s.ciphertext)+"'",String(s.expiresAt)].join(",")));
const read=(s)=>runtime(prepared("read_staff",SESSION_READ_SQL,"text","'"+s.digest+"'"));
const revoke=(s)=>runtime(prepared("revoke_staff",SESSION_REVOKE_SQL,"text","'"+s.digest+"'"));
const wrapKey=randomBytes(32).toString("hex");
const now=Date.now();
const makeRecord=()=>({
 actorId:"11111111-1111-4111-8111-111111111111",
 tenantId:"22222222-2222-4222-8222-222222222222",
 facilityId:"33333333-3333-4333-8333-333333333333",
 shiftId:"44444444-4444-4444-8444-444444444444",
 subject:"verified-staff",role:"nurse",revision:15,
 capabilities:["patient:view"],bearer:"aaa.bbb.ccc",issuedAt:now,expiresAt:now+240000
});
const newSeal=()=>sealOpaqueStaffTicket(makeRecord(),{env:{SYNQORIA_STAFF_TOKEN_WRAP_KEY:wrapKey},now});

test("P03-B0037 real identity schema installs FORCE RLS without LOGIN/BYPASSRLS role",()=>{
 const dir="../services/identity/db/";
 for(const file of ["oidc_replay_state_candidate.sql","oidc_replay_roles_rls_v1.sql","staff_sessions_v1.sql"]){
  const sql=readFileSync(new URL(dir+file,import.meta.url),"utf8");
  run(sql);
 }
 const role=run("SELECT rolcanlogin,rolbypassrls FROM pg_roles WHERE rolname='synqoria_staff_session_runtime';");
 assert.match(role,/f\|f/);
 const rls=run("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='synqoria_identity.staff_sessions'::regclass;");
 assert.match(rls,/t\|t/);
 const policies=run("SELECT count(*) FROM pg_policies WHERE schemaname='synqoria_identity' AND tablename='staff_sessions';");
 assert.equal(policies.trim(),"3");
});
test("P03-B0037 real PostgreSQL insertion, lookup, revocation and replay denial",()=>{
 const s=newSeal();
 const inserted=run(insert(s));
 assert.match(inserted,/INSERT 0 1/);
 const looked=run(read(s));assert.ok(looked.includes(s.ciphertext));
 assert.match(run(revoke(s)),/DELETE 1/);
 assert.match(run(revoke(s)),/DELETE 0/);
 assert.doesNotMatch(run(read(s)),new RegExp(s.ciphertext.replaceAll(".","[.]")));
});
test("P03-B0037 unauthorized roles cannot browse sessions or overwrite ciphertext",()=>{
 run("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_staff_session_untrusted') THEN CREATE ROLE synqoria_staff_session_untrusted NOLOGIN NOBYPASSRLS; END IF; END $$;");
 const denied=run("SET ROLE synqoria_staff_session_untrusted;\nSELECT session_digest FROM synqoria_identity.staff_sessions;",{fail:true});
 assert.match(denied,/permission denied/i);
 const s=newSeal();
 run(insert(s));
 const deniedUpdate=run(runtime("UPDATE synqoria_identity.staff_sessions SET sealed_record='forged' WHERE session_digest='"+s.digest+"';"),{fail:true});
 assert.match(deniedUpdate,/permission denied/i);
});
test("P03-B0037 expired or over-ten-minute sessions cannot be inserted",()=>{
 const s=newSeal();
 const tooLate=runtime(prepared("long_staff",SESSION_INSERT_SQL,"text,text,bigint",
  "'"+s.digest+"','"+esc(s.ciphertext)+"',"+String(now+900000)));
 assert.match(run(tooLate),/INSERT 0 0/);
 const bad=runtime("INSERT INTO synqoria_identity.staff_sessions(session_digest,sealed_record,expires_at) VALUES ('"+s.digest+"','"+esc(s.ciphertext)+"',clock_timestamp()+INTERVAL '12 minutes');");
 assert.match(run(bad,{fail:true}),/row-level security|check constraint/i);
});
test("P03-B0037 eight separate PostgreSQL connections cannot duplicate one ticket",async()=>{
 const s=newSeal();
 const result=await Promise.all(Array.from({length:8},()=>parallel(insert(s))));
 assert.equal(result.filter(x=>x.includes("INSERT 0 1")).length,1);
 assert.equal(result.filter(x=>x.includes("INSERT 0 0")).length,7);
 assert.match(run(revoke(s)),/DELETE 1/);
});
