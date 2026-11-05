import test from "node:test";
import assert from "node:assert/strict";
import {IDENTITY_SCHEMA_POLICY_SQL,verifyIdentitySchemaPolicies,IdentitySchemaPolicyError}
 from "../apps/web/lib/identity-db-schema-policies.mjs";
const policies={
 oidc_replay_state:[
 ["oidc_replay_runtime_read_live","r","synqoria_oidc_replay_runtime"],
 ["oidc_replay_runtime_create_live","a","synqoria_oidc_replay_runtime"],
 ["oidc_replay_runtime_consume_live","d","synqoria_oidc_replay_runtime"],
 ["oidc_replay_janitor_read_expired","r","synqoria_oidc_replay_janitor"],
 ["oidc_replay_janitor_delete_expired","d","synqoria_oidc_replay_janitor"]
 ],
 staff_sessions:[
 ["staff_session_active_read","r","synqoria_staff_session_runtime"],
 ["staff_session_new_secure","a","synqoria_staff_session_runtime"],
 ["staff_session_revoke_once","d","synqoria_staff_session_runtime"],
 ["staff_session_janitor_read_expired","r","synqoria_staff_session_janitor"],
 ["staff_session_janitor_delete_expired","d","synqoria_staff_session_janitor"]
 ],
 staff_security_audit:[
 ["identity_security_audit_append_only","a","synqoria_identity_audit_writer"]
 ]
};
const all=()=>Object.entries(policies).map(([table_name,rows])=>({
 table_name,row_security:true,force_row_security:true,public_grant:false,
 policies:rows.map(([name,command,role])=>({name,command,permissive:true,roles:[role]}))
}));
const db=rows=>({query:async (sql,args)=>{
 assert.equal(sql,IDENTITY_SCHEMA_POLICY_SQL);assert.deepEqual(args,[]);
 return {rowCount:rows.length,rows};
}});
const denied=async rows=>assert.rejects(()=>verifyIdentitySchemaPolicies(db(rows)),IdentitySchemaPolicyError);
test("B0055 expected three isolated identity tables and 11 exact policy bindings",async()=>{
 const v=await verifyIdentitySchemaPolicies(db(all()));
 assert.deepEqual(v,{verified:true,tableCount:3,policyCount:11});
 assert.equal(Object.isFrozen(v),true);
 assert.match(IDENTITY_SCHEMA_POLICY_SQL,/relforcerowsecurity/);
 assert.match(IDENTITY_SCHEMA_POLICY_SQL,/aclexplode/);
});
test("B0055 missing table, disabled FORCE RLS or PUBLIC privilege denies activation",async()=>{
 const rows=all();
 await denied(rows.slice(0,2));
 await denied(rows.map((r,i)=>i===0?{...r,force_row_security:false}:r));
 await denied(rows.map((r,i)=>i===1?{...r,row_security:false}:r));
 await denied(rows.map((r,i)=>i===2?{...r,public_grant:true}:r));
});
test("B0055 extra policy, revoked policy or broad FOR ALL grants deny activation",async()=>{
 const rows=all();
 await denied(rows.map((r,i)=>i===0?{...r,policies:[...r.policies,{name:"allow_all",command:"*",permissive:true,roles:["public"]}]}:r));
 await denied(rows.map((r,i)=>i===1?{...r,policies:r.policies.slice(0,-1)}:r));
 await denied(rows.map((r,i)=>i===2?{...r,policies:r.policies.map(p=>({...p,command:"*"}))}:r));
});
test("B0055 forged policy role, permissiveness or malformed data fail closed",async()=>{
 const rows=all();
 await denied(rows.map((r,i)=>i===0?{...r,policies:r.policies.map(p=>({...p,roles:["postgres"]}))}:r));
 await denied(rows.map((r,i)=>i===2?{...r,policies:r.policies.map(p=>({...p,permissive:false}))}:r));
 await denied(rows.map((r,i)=>i===0?{...r,policies:r.policies.map(p=>({...p,roles:["public",...p.roles]}))}:r));
 await denied(rows.map(r=>({...r,policies:null})));
 await assert.rejects(()=>verifyIdentitySchemaPolicies({query:async()=>{throw Error("postgres down");}}),IdentitySchemaPolicyError);
});
