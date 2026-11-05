import test from "node:test";
import assert from "node:assert/strict";
import {verifyIdentitySqlClients,IdentityRoleVerificationError,
 IDENTITY_ROLE_PROBE_SQL} from "../apps/web/lib/identity-db-role-verification.mjs";

const expected=["synqoria_oidc_replay_runtime","synqoria_staff_session_runtime","synqoria_identity_audit_writer"];
const good=role_name=>({
 role_name,superuser:false,bypass_rls:false,create_role:false,create_db:false,
 row_security:"on",cross_role_member:false
});
function make(){
 const calls=[];
 const clients=expected.map(role=>({query:async(sql,args)=>{
  calls.push([role,sql,args]);return {rowCount:1,rows:[good(role)]};
 }}));
 return {replaySql:clients[0],sessionSql:clients[1],auditSql:clients[2],clients,calls};
}
const rejected=x=>assert.rejects(x,IdentityRoleVerificationError);
test("P03-B0053 exact independent DB roles and active RLS checked before identity startup",async()=>{
 const set=make(),result=await verifyIdentitySqlClients(set);
 assert.deepEqual(result,{verified:true,roles:{
  replay:expected[0],session:expected[1],audit:expected[2]
 }});
 assert.equal(Object.isFrozen(result),true);
 assert.equal(Object.isFrozen(result.roles),true);
 assert.equal(set.calls.length,3);
 assert.ok(set.calls.every(x=>x[1]===IDENTITY_ROLE_PROBE_SQL&&x[2].length===0));
 assert.match(IDENTITY_ROLE_PROBE_SQL,/current_user/);
 assert.match(IDENTITY_ROLE_PROBE_SQL,/pg_auth_members/);
});
test("P03-B0053 reject a fake three-client arrangement using one PostgreSQL principal",async()=>{
 const clients=make();
 clients.sessionSql={query:async()=>({rowCount:1,rows:[good(expected[0])]})};
 await rejected(()=>verifyIdentitySqlClients(clients));
});
test("P03-B0053 reject superuser, BYPASSRLS, inherited role, and disabled RLS independently",async()=>{
 for(const change of [
  {superuser:true},{bypass_rls:true},{cross_role_member:true},{row_security:"off"},
  {create_db:true},{create_role:true},{role_name:"postgres"}
 ]){
  const clients=make();
  clients.replaySql={query:async()=>({rowCount:1,rows:[{...good(expected[0]),...change}]})};
  await rejected(()=>verifyIdentitySqlClients(clients));
 }
});
test("P03-B0053 unknown fields, malformed query outputs, and unavailable databases never pass",async()=>{
 for(const value of [
  {rowCount:0,rows:[]},{rowCount:1,rows:[]},{rowCount:1,rows:[{...good(expected[0]),token:"x"}]},
  {rowCount:1,rows:[{...good(expected[0]),superuser:"false"}]},
  {rowCount:1,rows:[{...good(expected[0]),row_security:null}]}
 ]){
  const clients=make();
  clients.replaySql={query:async()=>value};
  await rejected(()=>verifyIdentitySqlClients(clients));
 }
 const offline=make();
 offline.replaySql={query:async()=>{throw Error("db connection lost");}};
 await rejected(()=>verifyIdentitySqlClients(offline));
});
test("P03-B0053 missing or shared SQL client instances fail before any permission query",async()=>{
 const clients=make();
 await rejected(()=>verifyIdentitySqlClients({...clients,sessionSql:clients.replaySql}));
 await rejected(()=>verifyIdentitySqlClients({...clients,auditSql:null}));
});
