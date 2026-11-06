import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const path=new URL("../services/identity/db/identity_runtime_login_principals_v1.sql",import.meta.url);
const sql=readFileSync(path,"utf8");
test("B0097 each NOLOGIN RLS runtime has distinct LOGIN NOINHERIT principal",()=>{
 for(const [login,role] of [
 ["synqoria_oidc_replay_login","synqoria_oidc_replay_runtime"],
 ["synqoria_staff_session_login","synqoria_staff_session_runtime"],
 ["synqoria_identity_audit_login","synqoria_identity_audit_writer"]]){
  assert.ok(sql.includes(login));
  assert.ok(sql.includes(role));
 }
 assert.match(sql,/PASSWORD NULL/);
 assert.match(sql,/rolcanlogin=false AND rolinherit=false AND rolreplication=false/);
 assert.match(sql,/rolcreaterole=false AND rolcreatedb=false/);
 assert.match(sql,/LOGIN NOINHERIT NOSUPERUSER/);
 assert.match(sql,/GRANT %I TO %I WITH ADMIN FALSE, INHERIT FALSE, SET TRUE/);
 assert.match(sql,/assigned.rolname<>rec.runtime_role/);
 assert.doesNotMatch(sql,/WITH INHERIT TRUE|PASSWORD '[^']+'/);
});
