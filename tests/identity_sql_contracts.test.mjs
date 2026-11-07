import test from "node:test";
import assert from "node:assert/strict";
import {
 assertIdentitySqlContract,identitySqlContractIds,
 IdentitySqlContractError,IDENTITY_SQL_HEALTH,IDENTITY_SQL_CONTEXT
} from "../apps/web/lib/identity-sql-contracts.mjs";
import {createConnectionAttestedSqlClient,IdentityAttestedQueryError}
 from "../apps/web/lib/identity-attested-db-query.mjs";
import {IDENTITY_ROLE_PROBE_SQL} from "../apps/web/lib/identity-db-role-verification.mjs";
import {IDENTITY_SCHEMA_POLICY_SQL} from "../apps/web/lib/identity-db-schema-policies.mjs";
import {OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL}
 from "../apps/web/lib/oidc-replay-postgres.mjs";
import {SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL}
 from "../apps/web/lib/postgres-staff-session-store.mjs";
import {IDENTITY_AUDIT_INSERT_SQL}
 from "../apps/web/lib/identity-security-audit.mjs";

const R="synqoria_oidc_replay_runtime";
const S="synqoria_staff_session_runtime";
const A="synqoria_identity_audit_writer";
const state="synqoria:oidc:state:v1:"+"a".repeat(64);
const digest="a".repeat(64);
const uuid="11111111-1111-4111-8111-111111111111";
const iv=Buffer.alloc(12,1).toString("base64url");
const tag=Buffer.alloc(16,2).toString("base64url");
const ctext=Buffer.from("encrypted-data").toString("base64url");
const sealed="v1."+iv+"."+ctext+"."+tag;
const epoch=Date.now();
const goodAudit=()=>[uuid,digest,digest,"LOGIN_DENIED","DENIED",uuid,epoch];
const pass=(role,sql,args=[])=>assert.doesNotThrow(()=>assertIdentitySqlContract(role,sql,args));
const no=(role,sql,args=[])=>assert.throws(()=>assertIdentitySqlContract(role,sql,args),IdentitySqlContractError);

test("B0124 exactly ten distinct reviewed SQL source operations in immutable catalogue",()=>{
 assert.deepEqual([...identitySqlContractIds],[
  "role-probe","health","context","policy-probe","replay-reserve",
  "replay-consume","session-insert","session-read","session-revoke","audit-append"]);
 assert.equal(new Set(identitySqlContractIds).size,10);
});
test("B0125 role catalog probe accepts zero arguments for each isolated runtime",()=>{
 for(const role of [R,S,A])pass(role,IDENTITY_ROLE_PROBE_SQL);
 for(const role of [R,S,A])no(role,IDENTITY_ROLE_PROBE_SQL,["unexpected"]);
});
test("B0126 exact harmless health SQL and no data-returning SELECT variants",()=>{
 pass(R,IDENTITY_SQL_HEALTH);pass(S,IDENTITY_SQL_HEALTH);pass(A,IDENTITY_SQL_HEALTH);
 no(R,"SELECT 1");no(R,"SELECT 2 AS ok");no(R,"SELECT 1 AS ok LIMIT 1");
});
test("B0127 exact current and session role SQL without caller-provided arguments",()=>{
 for(const role of [R,S,A])pass(role,IDENTITY_SQL_CONTEXT);
 no(R,IDENTITY_SQL_CONTEXT+" WHERE TRUE");
 no(R,IDENTITY_SQL_CONTEXT,[1]);
});
test("B0128 policy introspection is only callable from restricted replay runtime",()=>{
 pass(R,IDENTITY_SCHEMA_POLICY_SQL);
 no(S,IDENTITY_SCHEMA_POLICY_SQL);no(A,IDENTITY_SCHEMA_POLICY_SQL);
 no(R,IDENTITY_SCHEMA_POLICY_SQL,["synqoria_identity"]);
});
test("B0129 OIDC replay reservations reject foreign state namespaces and malformed state digest",()=>{
 pass(R,OIDC_REPLAY_RESERVE_SQL,[state,30000]);
 for(const bad of ["synqoria:oidc:state:v2:"+"a".repeat(64),
  "synqoria:oidc:state:v1:"+"A".repeat(64),state+"x",
  "synqoria:oidc:state:v1:"+"x".repeat(64),"",null])
  no(R,OIDC_REPLAY_RESERVE_SQL,[bad,30000]);
});
test("B0130 replay expiry is positive bounded integer and never arbitrary SQL interval",()=>{
 for(const ttl of [1,300000,30000])pass(R,OIDC_REPLAY_RESERVE_SQL,[state,ttl]);
 for(const ttl of [0,-1,300001,1.1,"1000",null,Infinity,NaN])
  no(R,OIDC_REPLAY_RESERVE_SQL,[state,ttl]);
});
test("B0131 OIDC state consume requires one canonical single-use state digest",()=>{
 pass(R,OIDC_REPLAY_CONSUME_SQL,[state]);
 for(const args of [[],[state,state],["user_supplied"],[null]])
  no(R,OIDC_REPLAY_CONSUME_SQL,args);
});
test("B0132 staff session inserts accept SHA-256 lowercase digest, never raw ticket",()=>{
 pass(S,SESSION_INSERT_SQL,[digest,sealed,epoch]);
 for(const d of ["x".repeat(43),digest.toUpperCase(),"f".repeat(63),null])
  no(S,SESSION_INSERT_SQL,[d,sealed,epoch]);
});
test("B0133 session ciphertext has canonical AEAD v1 IV, encrypted body and tag",()=>{
 pass(S,SESSION_INSERT_SQL,[digest,sealed,epoch]);
 for(const bad of ["plain-bearer","v2."+iv+"."+ctext+"."+tag,
  "v1."+iv+"."+ctext+"."+"a",
  "v1."+Buffer.alloc(8).toString("base64url")+"."+ctext+"."+tag,
  sealed+"=",sealed+".extra"])
  no(S,SESSION_INSERT_SQL,[digest,bad,epoch]);
});
test("B0134 reject unbounded ciphertext and noncanonical base64 encodings",()=>{
 const huge=Buffer.alloc(17000,1).toString("base64url");
 no(S,SESSION_INSERT_SQL,[digest,"v1."+iv+"."+huge+"."+tag,epoch]);
 no(S,SESSION_INSERT_SQL,[digest,"v1."+iv+"."+ctext+"==."+tag,epoch]);
 no(S,SESSION_INSERT_SQL,[digest,"v1."+iv+"."+"=".repeat(20)+"."+tag,epoch]);
});
test("B0135 staff session expiry must be finite absolute millisecond epoch integer",()=>{
 for(const bad of [0,-1,1.5,epoch+0.5,""+epoch,Infinity,null,4102444800001])
  no(S,SESSION_INSERT_SQL,[digest,sealed,bad]);
 pass(S,SESSION_INSERT_SQL,[digest,sealed,epoch]);
});
test("B0136 session read accepts precisely one server-derived SHA256 ticket digest",()=>{
 pass(S,SESSION_READ_SQL,[digest]);
 for(const bad of [[],[digest,digest],[digest.toUpperCase()],[null],[uuid]])
  no(S,SESSION_READ_SQL,bad);
});
test("B0137 session revoke has same digest-only input, never browser ticket",()=>{
 pass(S,SESSION_REVOKE_SQL,[digest]);
 for(const bad of [[],[digest,uuid],[uuid],["ticket"+digest]])
  no(S,SESSION_REVOKE_SQL,bad);
});
test("B0138 audit event ID and correlation ID require syntactically valid UUIDs",()=>{
 pass(A,IDENTITY_AUDIT_INSERT_SQL,goodAudit());
 for(const index of [0,5]){
  for(const invalid of ["not-a-uuid",state,digest,"",null]){
   const x=goodAudit();x[index]=invalid;no(A,IDENTITY_AUDIT_INSERT_SQL,x);
  }
 }
});
test("B0139 audit actor and tenant pseudonyms are SHA-256 digests, never raw identity",()=>{
 for(const index of [1,2]){
  for(const invalid of [uuid,"abc","f".repeat(65),"F".repeat(64),null]){
   const x=goodAudit();x[index]=invalid;no(A,IDENTITY_AUDIT_INSERT_SQL,x);
  }
 }
 pass(A,IDENTITY_AUDIT_INSERT_SQL,goodAudit());
});
test("B0140 audit event kind must be from closed clinical identity security dictionary",()=>{
 for(const kind of ["LOGIN_GRANTED","LOGIN_DENIED","SESSION_REVOKED",
  "SESSION_EXPIRED","WORKFORCE_GRANT_DENIED","PATIENT_DISCLOSURE_DENIED",
  "ENCOUNTER_DISCLOSURE_DENIED"]){
  const x=goodAudit();x[3]=kind;
  if(kind==="LOGIN_GRANTED")x[4]="PERMITTED";
  pass(A,IDENTITY_AUDIT_INSERT_SQL,x);
 }
 for(const kind of ["PATIENT_ADDRESS","TOKEN_LEAK","ACCESS_GRANTED","",null]){
  const x=goodAudit();x[3]=kind;no(A,IDENTITY_AUDIT_INSERT_SQL,x);
 }
});
test("B0141 audit outcome cannot represent successful access for denied events",()=>{
 const x=goodAudit();x[3]="LOGIN_GRANTED";x[4]="DENIED";no(A,IDENTITY_AUDIT_INSERT_SQL,x);
 x[3]="WORKFORCE_GRANT_DENIED";x[4]="PERMITTED";no(A,IDENTITY_AUDIT_INSERT_SQL,x);
 x[3]="SESSION_REVOKED";x[4]="PERMITTED";pass(A,IDENTITY_AUDIT_INSERT_SQL,x);
});
test("B0142 audit occurred-at timestamp rejects string, float, negative or out-of-range",()=>{
 for(const bad of [NaN,Infinity,-1,0,1.5,""+epoch,4102444800001,1577836799999]){
  const x=goodAudit();x[6]=bad;no(A,IDENTITY_AUDIT_INSERT_SQL,x);
 }
});
test("B0143 ad hoc unreviewed SELECT on sensitive metadata is always denied",()=>{
 for(const raw of ["SELECT * FROM synqoria_identity.staff_sessions",
  "SELECT current_setting('password_encryption')","SELECT pg_roles.rolpassword FROM pg_authid",
  "SELECT audit_ledger FROM clinical.encounter"]){
  no(R,raw);no(S,raw);no(A,raw);
 }
});
test("B0144 common table expressions, arbitrary INSERT and DELETE cannot escape manifest",()=>{
 for(const raw of ["WITH s AS (SELECT 1) SELECT * FROM s",
  "INSERT INTO synqoria_identity.staff_sessions VALUES ('x')",
  "DELETE FROM synqoria_identity.staff_sessions WHERE true",
  "SELECT 1 AS ok UNION SELECT 2","TRUNCATE synqoria_identity.staff_sessions"])
  no(S,raw);
});
test("B0145 exact SQL byte identity denies comments, trailing semicolons and whitespace",()=>{
 for(const raw of [IDENTITY_SQL_HEALTH+";",IDENTITY_SQL_HEALTH+" -- anything",
  " "+IDENTITY_SQL_HEALTH,IDENTITY_SQL_HEALTH+" ",
  IDENTITY_ROLE_PROBE_SQL.replace("SELECT","select"),
  IDENTITY_ROLE_PROBE_SQL+"\n/* injected */"])no(R,raw);
});
test("B0146 cross-role valid SQL operations still cannot be dispatched",()=>{
 no(S,OIDC_REPLAY_RESERVE_SQL,[state,1000]);
 no(A,OIDC_REPLAY_CONSUME_SQL,[state]);
 no(R,SESSION_READ_SQL,[digest]);
 no(R,SESSION_INSERT_SQL,[digest,sealed,epoch]);
 no(A,SESSION_REVOKE_SQL,[digest]);
 no(S,IDENTITY_AUDIT_INSERT_SQL,goodAudit());
});
test("B0147 an invalid array/prototype or oversized parameter bundle never opens DB",async()=>{
 for(const args of [{},new Array(8).fill(1),Object.assign(Object.create(null),{length:0})]){
  no(R,IDENTITY_SQL_HEALTH,args);
 }
 const malicious=[];Object.setPrototypeOf(malicious,null);
 no(R,IDENTITY_SQL_HEALTH,malicious);
 let connects=0;
 const pool={async connect(){connects++;throw Error("should never connect");}};
 const client=createConnectionAttestedSqlClient(pool,R,"synqoria_oidc_replay_login");
 for(const [sql,args] of [["SELECT current_database()",[]],
   [SESSION_READ_SQL,[digest]],[OIDC_REPLAY_RESERVE_SQL,[state,"300"]]])
  await assert.rejects(()=>client.query(sql,args),IdentityAttestedQueryError);
 assert.equal(connects,0);
});
