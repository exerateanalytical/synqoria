import test from "node:test";
import assert from "node:assert/strict";
import {createConnectionAttestedSqlClient,IDENTITY_CONNECTION_ATTEST_SQL,
 IdentityAttestedQueryError} from "../apps/web/lib/identity-attested-db-query.mjs";
const role="synqoria_staff_session_runtime";
const evidence=(changes={})=>({
 role_name:role,row_security:"on",superuser:false,bypass_rls:false,
 create_role:false,create_db:false,cross_role_member:false,
 tls_active:true,tls_version:"TLSv1.3",tls_cipher:"TLS_AES_256_GCM_SHA384",...changes
});
function poolFor(probe=evidence(),{throwOnStatement=false,throwOnProbe=false,throwOnConnect=false}={}){
 const events=[];
 return {events,pool:{
  async connect(){
   events.push("connect");if(throwOnConnect)throw Error("postgres password");
   return {
    async query(sql,args){
     if(sql===IDENTITY_CONNECTION_ATTEST_SQL){
      events.push("probe");
      assert.deepEqual(args,[]);
      if(throwOnProbe)throw Error("postgres connection");
      return {rowCount:1,rows:[probe]};
     }
     events.push("operation");
     if(throwOnStatement)throw Error("private database outage");
     return {rowCount:1,rows:[{ok:true}]};
    },
    release(){events.push("release");}
   };
  }
 }};
}
test("B0074 exact same acquired connection attested before each SQL operation",async()=>{
 const h=poolFor(),client=createConnectionAttestedSqlClient(h.pool,role);
 assert.deepEqual((await client.query("SELECT 1",[])).rows,[{ok:true}]);
 assert.deepEqual((await client.query("SELECT 2",[3])).rows,[{ok:true}]);
 assert.deepEqual(h.events,["connect","probe","operation","release",
  "connect","probe","operation","release"]);
});
test("B0075 actual PostgreSQL non-TLS, SSL downgrade and obsolete ciphers denied",async()=>{
 for(const changed of [
  {tls_active:false},{tls_version:"TLSv1.1"},
  {tls_version:"SSLv3"},{tls_cipher:"RC4-MD5"},{tls_cipher:""},
  {tls_cipher:"DES-CBC3-SHA"}
 ]){
  const h=poolFor(evidence(changed));
  const client=createConnectionAttestedSqlClient(h.pool,role);
  await assert.rejects(()=>client.query("SELECT sensitive",[]),IdentityAttestedQueryError);
  assert.deepEqual(h.events,["connect","probe","release"]);
 }
});
test("B0076 elevated, bypass-RLS, cross-role and malformed attestation denied",async()=>{
 for(const changed of [
  {role_name:"postgres"},{superuser:true},{bypass_rls:true},
  {create_role:true},{create_db:true},{row_security:"off"},
  {cross_role_member:true},{tls_active:"true"}
 ]){
  const h=poolFor(evidence(changed));
  await assert.rejects(()=>createConnectionAttestedSqlClient(h.pool,role).query("SELECT 1"),
   IdentityAttestedQueryError);
  assert.deepEqual(h.events,["connect","probe","release"]);
 }
});
test("B0077 pool, probe and data-query errors redact secrets and release connections",async()=>{
 for(const opt of [{throwOnConnect:true},{throwOnProbe:true},{throwOnStatement:true}]){
  const h=poolFor(evidence(),opt),client=createConnectionAttestedSqlClient(h.pool,role);
  await assert.rejects(()=>client.query("SELECT private_credentials",[]),e=>{
   assert.equal(e.message,"identity_attested_query_denied");
   assert.doesNotMatch(e.message,/password|private|outage/);return true;
  });
  assert.equal(h.events.at(-1),opt.throwOnConnect?"connect":"release");
 }
});
test("B0077 forged role selector, invalid statement and missing client fail closed",async()=>{
 assert.throws(()=>createConnectionAttestedSqlClient({},role),IdentityAttestedQueryError);
 assert.throws(()=>createConnectionAttestedSqlClient(poolFor().pool,"postgres"),IdentityAttestedQueryError);
 const h=poolFor(),c=createConnectionAttestedSqlClient(h.pool,role);
 await assert.rejects(()=>c.query("",[]),IdentityAttestedQueryError);
 await assert.rejects(()=>c.query("SELECT 1",{}),IdentityAttestedQueryError);
 assert.equal(h.events.length,0);
});
