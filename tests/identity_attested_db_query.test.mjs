import test from "node:test";
import assert from "node:assert/strict";
import {createConnectionAttestedSqlClient,IDENTITY_CONNECTION_ATTEST_SQL,
 IdentityAttestedQueryError} from "../apps/web/lib/identity-attested-db-query.mjs";
const role="synqoria_staff_session_runtime",login="synqoria_staff_session_login";
const good=(changes={})=>({
 role_name:role,login_name:login,row_security:"on",
 superuser:false,bypass_rls:false,create_role:false,create_db:false,cross_role_member:false,
 login_can_login:true,login_inherit:false,login_superuser:false,login_bypass_rls:false,
 login_create_role:false,login_create_db:false,
 login_has_expected_membership:true,login_has_extra_membership:false,...changes
});
const verifiedStream=(change={})=>({
 encrypted:true,authorized:true,
 getProtocol:()=>"TLSv1.3",
 getCipher:()=>({name:"TLS_AES_256_GCM_SHA384"}),
 getPeerCertificate:()=>({raw:Buffer.alloc(512)}),...change
});
function mock(probe=good(),{failAt=null,stream=verifiedStream()}={}){
 const events=[];
 return {events,pool:{
  async connect(){
   events.push("connect");
   if(failAt==="connect")throw Error("postgres password");
   return {
    connection:{stream},
    async query(sql,args){
     const type=sql==="BEGIN"?"begin":sql.startsWith("SET LOCAL ROLE ")?"set":
      sql===IDENTITY_CONNECTION_ATTEST_SQL?"attest":
      sql==="COMMIT"?"commit":sql==="ROLLBACK"?"rollback":"operation";
     events.push(type);
     if(type==="set")assert.equal(sql,"SET LOCAL ROLE "+role);
     if(type==="attest")assert.deepEqual(args,[]);
     if(type===failAt)throw Error("postgres credential or privacy failure");
     if(type==="attest")return {rowCount:1,rows:[probe]};
     return {rowCount:1,rows:[{ok:true}]};
    },
    release(){events.push("release");}
   };
  }
 }};
}
const use=h=>createConnectionAttestedSqlClient(h.pool,role,login);
test("B0098 each SQL operation verifies live Node TLS socket then begins local role transaction",async()=>{
 const h=mock(),c=use(h);
 assert.deepEqual((await c.query("SELECT 1")).rows,[{ok:true}]);
 assert.deepEqual((await c.query("SELECT 2")).rows,[{ok:true}]);
 assert.deepEqual(h.events,[
  "connect","begin","set","attest","operation","commit","release",
  "connect","begin","set","attest","operation","commit","release"]);
 assert.doesNotMatch(IDENTITY_CONNECTION_ATTEST_SQL,/pg_stat_ssl/);
});
test("B0099 reject unauthenticated socket, broken peer certificate, TLS downgrade, wrong role",async()=>{
 const insecure=[
  verifiedStream({encrypted:false}),verifiedStream({authorized:false}),
  verifiedStream({getProtocol:()=>"TLSv1.1"}),
  verifiedStream({getCipher:()=>({name:"RC4-MD5"})}),
  verifiedStream({getPeerCertificate:()=>({raw:Buffer.alloc(10)})}),
  verifiedStream({getPeerCertificate:()=>({})}),{}
 ];
 for(const stream of insecure){
  const h=mock(good(),{stream});
  await assert.rejects(()=>use(h).query("SELECT 1"),IdentityAttestedQueryError);
  assert.deepEqual(h.events,["connect","release"]);
 }
 for(const changes of [
  {role_name:"postgres"},{login_name:"postgres"},{row_security:"off"},
  {cross_role_member:true},{superuser:true},{bypass_rls:true}
 ]){
  const h=mock(good(changes));
  await assert.rejects(()=>use(h).query("SELECT 1"),IdentityAttestedQueryError);
  assert.deepEqual(h.events,["connect","begin","set","attest","rollback","release"]);
 }
});
test("B0100 refuse privileged or inherited LOGIN principals and extra role memberships",async()=>{
 for(const changes of [
  {login_can_login:false},{login_inherit:true},{login_superuser:true},
  {login_bypass_rls:true},{login_create_role:true},{login_create_db:true},
  {login_has_expected_membership:false},{login_has_extra_membership:true}
 ]){
  const h=mock(good(changes));
  await assert.rejects(()=>use(h).query("SELECT 1"),IdentityAttestedQueryError);
  assert.equal(h.events.includes("operation"),false);
  assert.equal(h.events.at(-2),"rollback");
 }
});
test("B0101 connection, SQL and commit errors redact messages and rollback before release",async()=>{
 for(const failAt of ["connect","begin","set","attest","operation","commit"]){
  const h=mock(good(),{failAt});
  await assert.rejects(()=>use(h).query("SELECT protected"),e=>{
   assert.equal(e.message,"identity_attested_query_denied");
   assert.doesNotMatch(e.message,/credential|privacy|postgres/);return true;
  });
  if(failAt!=="connect"){
   assert.equal(h.events.at(-1),"release");
   if(failAt!=="begin")assert.ok(h.events.includes("rollback"));
  }
 }
});
test("B0101 forged role changes or SQL multi-statements denied before acquiring a connection",async()=>{
 const h=mock(),c=use(h);
 assert.throws(()=>createConnectionAttestedSqlClient(h.pool,role,"postgres"),IdentityAttestedQueryError);
 for(const statement of ["","CREATE ROLE postgres","SET ROLE postgres",
  "SELECT 1; SET ROLE postgres","DELETE FROM x; SELECT 1","COMMIT"]){
  await assert.rejects(()=>c.query(statement,[]),IdentityAttestedQueryError);
 }
 await assert.rejects(()=>c.query("SELECT 1",{}),IdentityAttestedQueryError);
 assert.deepEqual(h.events,[]);
});

test("B0110 SQL pool discards denied, poisoned or unverifiable connection on release",async()=>{
 for(const opt of [{failAt:"operation"},{failAt:"commit"},{stream:verifiedStream({authorized:false})}]){
  const h=mock(good(),opt),orig=h.pool.connect.bind(h.pool),flags=[];
  h.pool.connect=async()=>{
   const c=await orig(),oldRelease=c.release;
   c.release=(discard)=>{flags.push(discard);oldRelease();};
   return c;
  };
  await assert.rejects(()=>use(h).query("SELECT sensitive"),IdentityAttestedQueryError);
  assert.deepEqual(flags,[true]);
 }
 const h=mock(),orig=h.pool.connect.bind(h.pool),flags=[];
 h.pool.connect=async()=>{
  const c=await orig(),oldRelease=c.release;
  c.release=(discard)=>{flags.push(discard);oldRelease();};
  return c;
 };
 await use(h).query("SELECT 1");
 assert.deepEqual(flags,[false]);
});
