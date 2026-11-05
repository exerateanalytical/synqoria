import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {spawnSync} from "node:child_process";
import {IDENTITY_SCHEMA_POLICY_SQL,verifyIdentitySchemaPolicies,IdentitySchemaPolicyError}
 from "../apps/web/lib/identity-db-schema-policies.mjs";

if(process.env.SYNQORIA_POSTGRES_INTEGRATION!=="1")
 throw Error("Requires disposable PostgreSQL 16 integration service");
const env={...process.env,PGHOST:"127.0.0.1",PGPORT:"5432",PGUSER:"postgres",
 PGPASSWORD:"test-only-never-use-production",PGDATABASE:"synqoria_oidc_test",
 PGOPTIONS:"-c statement_timeout=10000"};
function sql(statement,{fail=false}={}){
 const p=spawnSync("psql",["-X","-A","-t","-v","ON_ERROR_STOP=1"],{
  input:statement,encoding:"utf8",env,timeout:15000,maxBuffer:131072
 });
 if(fail){assert.notEqual(p.status,0);return p.stderr;}
 assert.equal(p.status,0,(p.stderr||"Postgres failure")+"\n"+statement);
 return p.stdout;
}
const client={query:async(statement,args)=>{
 assert.equal(statement,IDENTITY_SCHEMA_POLICY_SQL);assert.deepEqual(args,[]);
 const text=sql("SELECT row_to_json(t) FROM ("+statement+") t;");
 const rows=text.split("\n").map(x=>x.trim()).filter(x=>x.startsWith("{")).map(JSON.parse);
 return {rowCount:rows.length,rows};
}};
const denied=()=>assert.rejects(()=>verifyIdentitySchemaPolicies(client),IdentitySchemaPolicyError);
test("B0055 actual PG16 migrations produce exactly three FORCE-RLS policy tables",async()=>{
 const files=["oidc_replay_state_candidate.sql","oidc_replay_roles_rls_v1.sql",
  "staff_sessions_v1.sql","staff_session_janitor_v1.sql","staff_security_audit_v1.sql"];
 for(const path of files)sql(readFileSync(new URL("../services/identity/db/"+path,import.meta.url),"utf8"));
 const verified=await verifyIdentitySchemaPolicies(client);
 assert.deepEqual(verified,{verified:true,tableCount:3,policyCount:11});
});
test("B0055 public table grants and disabled forced-RLS are rejected against live catalog",async()=>{
 sql("GRANT SELECT ON synqoria_identity.staff_sessions TO PUBLIC;");
 try{await denied();}finally{sql("REVOKE SELECT ON synqoria_identity.staff_sessions FROM PUBLIC;");}
 sql("ALTER TABLE synqoria_identity.staff_sessions NO FORCE ROW LEVEL SECURITY;");
 try{await denied();}finally{sql("ALTER TABLE synqoria_identity.staff_sessions FORCE ROW LEVEL SECURITY;");}
 assert.equal((await verifyIdentitySchemaPolicies(client)).verified,true);
});
test("B0055 injection of one permissive additional policy cannot silently grant access",async()=>{
 sql("CREATE POLICY dangerous_for_all ON synqoria_identity.oidc_replay_state FOR SELECT TO PUBLIC USING (true);");
 try{await denied();}finally{sql("DROP POLICY dangerous_for_all ON synqoria_identity.oidc_replay_state;");}
 assert.equal((await verifyIdentitySchemaPolicies(client)).verified,true);
});

test("B0061 a correctly named but permissive USING(true) policy is denied by predicate attestation",async()=>{
 sql("ALTER POLICY staff_session_active_read ON synqoria_identity.staff_sessions USING (true);");
 try{await denied();}
 finally{sql("ALTER POLICY staff_session_active_read ON synqoria_identity.staff_sessions USING (expires_at > clock_timestamp());");}
 assert.equal((await verifyIdentitySchemaPolicies(client)).verified,true);
});
test("B0061 an INSERT policy without its bounded TTL must fail before identity boot",async()=>{
 sql("ALTER POLICY staff_session_new_secure ON synqoria_identity.staff_sessions WITH CHECK (expires_at > clock_timestamp());");
 try{await denied();}
 finally{sql("ALTER POLICY staff_session_new_secure ON synqoria_identity.staff_sessions WITH CHECK (expires_at > clock_timestamp() AND expires_at <= clock_timestamp()+INTERVAL '10 minutes');");}
 assert.equal((await verifyIdentitySchemaPolicies(client)).verified,true);
});
