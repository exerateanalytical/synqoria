import test from "node:test";
import assert from "node:assert/strict";
import {createRestrictedIdentityPools,IdentityProductionSqlError}
 from "../apps/web/lib/identity-production-sql.mjs";
const PEM="-----BEGIN CERTIFICATE-----\n"+("QUJD".repeat(35))+"\n-----END CERTIFICATE-----\n";
const ROLES=["synqoria_oidc_replay_runtime","synqoria_staff_session_runtime","synqoria_identity_audit_writer"];
const env=()=>({
 SYNQORIA_IDENTITY_DB_CA_PEM:PEM,
 SYNQORIA_IDENTITY_REPLAY_DSN:"postgresql://"+ROLES[0]+":"+"x".repeat(44)+"@db.hospital.internal:5432/synqoria_identity",
 SYNQORIA_IDENTITY_SESSION_DSN:"postgresql://"+ROLES[1]+":"+"y".repeat(44)+"@db.hospital.internal:5432/synqoria_identity",
 SYNQORIA_IDENTITY_AUDIT_DSN:"postgresql://"+ROLES[2]+":"+"z".repeat(44)+"@db.hospital.internal:5432/synqoria_identity"
});
test("B0069 three independently restricted TLS pools use CA verification, never shared or admin role",async()=>{
 const options=[],instances=[];
 class Pool{
  constructor(config){options.push(config);instances.push(this);this.ended=0;}
  async query(){return {rowCount:0,rows:[]};}
  async end(){this.ended++;}
 }
 const pools=await createRestrictedIdentityPools({env:env(),Pool});
 assert.equal(options.length,3);
 assert.notEqual(pools.replaySql,pools.sessionSql);
 assert.notEqual(pools.auditSql,pools.sessionSql);
 assert.deepEqual(options.map(x=>x.max),[1,1,1]);
 for(const item of options){
  assert.equal(item.ssl.ca,PEM);
  assert.equal(item.ssl.rejectUnauthorized,true);
  assert.equal(item.ssl.servername,"db.hospital.internal");
  assert.equal(item.connectionTimeoutMillis,5000);
  assert.equal(item.connectionString.includes("sslmode=disable"),false);
 }
 await pools.close();await pools.close();
 assert.deepEqual(instances.map(x=>x.ended),[1,1,1]);
});
test("B0069 reject wrong role, loopback host, cleartext policy, and malformed CA",async()=>{
 const bad=[
  {...env(),SYNQORIA_IDENTITY_REPLAY_DSN:env().SYNQORIA_IDENTITY_SESSION_DSN},
  {...env(),SYNQORIA_IDENTITY_AUDIT_DSN:env().SYNQORIA_IDENTITY_AUDIT_DSN.replace("db.hospital.internal","localhost")},
  {...env(),SYNQORIA_IDENTITY_SESSION_DSN:env().SYNQORIA_IDENTITY_SESSION_DSN+"?sslmode=disable"},
  {...env(),SYNQORIA_IDENTITY_DB_CA_PEM:"untrusted"}
 ];
 for(const e of bad){
  let created=0;
  class Pool{constructor(){created++;}query(){}end(){}}
  await assert.rejects(()=>createRestrictedIdentityPools({env:e,Pool}),IdentityProductionSqlError);
  assert.equal(created,0);
 }
});
test("B0069 partial connection construction errors close already created pools",async()=>{
 let ended=0,created=0;
 class Pool{
  constructor(){created++;if(created===2)throw Error("pool error");}
  async query(){return {};}
  async end(){ended++;}
 }
 await assert.rejects(()=>createRestrictedIdentityPools({env:env(),Pool}),IdentityProductionSqlError);
 assert.equal(created,2);assert.equal(ended,1);
});
