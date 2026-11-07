/**
 * SYNQORIA P03-B0124–B0153 / identity SQL operation boundary.
 *
 * Runtime selectors are NOT a general-purpose SQL transport. Each transaction
 * may execute one exact canonical statement with an exact parameter schema.
 * SQL text, table/column identifiers, role and argument shape cannot be
 * provided by browser input or a third-party plugin. Keep RLS and TLS checks.
 */
import {IDENTITY_ROLE_PROBE_SQL} from "./identity-db-role-verification.mjs";
import {IDENTITY_SCHEMA_POLICY_SQL} from "./identity-db-schema-policies.mjs";
import {OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL}
 from "./oidc-replay-postgres.mjs";
import {SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL}
 from "./postgres-staff-session-store.mjs";
import {IDENTITY_AUDIT_INSERT_SQL} from "./identity-security-audit.mjs";

export class IdentitySqlContractError extends Error{
 constructor(){super("identity_sql_contract_denied");this.name="IdentitySqlContractError";}
}
const deny=()=>{throw new IdentitySqlContractError();};
const replay="synqoria_oidc_replay_runtime",
 session="synqoria_staff_session_runtime",audit="synqoria_identity_audit_writer";
const SHA=/^[0-9a-f]{64}$/;
const STATE=/^synqoria:oidc:state:v1:[0-9a-f]{64}$/;
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const REF=/^[A-Za-z0-9_-]+$/;
const EPOCH_MIN=1577836800000,EPOCH_MAX=4102444800000;
const MAX_SEALED_RECORD=20000;
const KINDS=new Set(["LOGIN_GRANTED","LOGIN_DENIED","SESSION_REVOKED",
 "SESSION_EXPIRED","WORKFORCE_GRANT_DENIED","PATIENT_DISCLOSURE_DENIED",
 "ENCOUNTER_DISCLOSURE_DENIED"]);
const OUTCOMES=new Set(["DENIED","PERMITTED"]);
const exactArray=x=>Array.isArray(x)&&Object.getPrototypeOf(x)===Array.prototype;
const string=(x,re)=>typeof x==="string"&&re.test(x);
const millisecond=x=>Number.isSafeInteger(x)&&x>=EPOCH_MIN&&x<=EPOCH_MAX;
const noParams=x=>x.length===0;
function canonicalBase64Url(value,max){
 if(typeof value!=="string"||value.length<1||value.length>max||!REF.test(value))return false;
 const out=Buffer.from(value,"base64url");
 return out.length>0&&out.toString("base64url")===value;
}
function sealed(v){
 if(typeof v!=="string"||v.length<40||v.length>MAX_SEALED_RECORD)return false;
 const sections=v.split(".");
 if(sections.length!==4||sections[0]!=="v1")return false;
 if(!canonicalBase64Url(sections[1],16)||
   Buffer.from(sections[1],"base64url").length!==12||
   !canonicalBase64Url(sections[2],16000)||
   !canonicalBase64Url(sections[3],22)||
   Buffer.from(sections[3],"base64url").length!==16)return false;
 return true;
}
function auditParams(p){
 if(p.length!==7||!string(p[0],UUID)||!string(p[1],SHA)||
    !string(p[2],SHA)||!KINDS.has(p[3])||!OUTCOMES.has(p[4])||
    !string(p[5],UUID)||!millisecond(p[6]))return false;
 if(p[3]==="LOGIN_GRANTED"&&p[4]!=="PERMITTED")return false;
 if(p[4]==="PERMITTED"&&p[3]!=="LOGIN_GRANTED"&&
    p[3]!=="SESSION_REVOKED")return false;
 return true;
}
export const IDENTITY_SQL_HEALTH="SELECT 1 AS ok";
export const IDENTITY_SQL_CONTEXT="SELECT current_user AS active_role,session_user AS login_role";
const manifest=Object.freeze([
 Object.freeze({id:"role-probe",role:"*",sql:IDENTITY_ROLE_PROBE_SQL,check:noParams}),
 Object.freeze({id:"health",role:"*",sql:IDENTITY_SQL_HEALTH,check:noParams}),
 Object.freeze({id:"context",role:"*",sql:IDENTITY_SQL_CONTEXT,check:noParams}),
 Object.freeze({id:"policy-probe",role:replay,sql:IDENTITY_SCHEMA_POLICY_SQL,check:noParams}),
 Object.freeze({id:"replay-reserve",role:replay,sql:OIDC_REPLAY_RESERVE_SQL,
  check:p=>p.length===2&&string(p[0],STATE)&&
   Number.isSafeInteger(p[1])&&p[1]>=1&&p[1]<=300000}),
 Object.freeze({id:"replay-consume",role:replay,sql:OIDC_REPLAY_CONSUME_SQL,
  check:p=>p.length===1&&string(p[0],STATE)}),
 Object.freeze({id:"session-insert",role:session,sql:SESSION_INSERT_SQL,
  check:p=>p.length===3&&string(p[0],SHA)&&sealed(p[1])&&millisecond(p[2])}),
 Object.freeze({id:"session-read",role:session,sql:SESSION_READ_SQL,
  check:p=>p.length===1&&string(p[0],SHA)}),
 Object.freeze({id:"session-revoke",role:session,sql:SESSION_REVOKE_SQL,
  check:p=>p.length===1&&string(p[0],SHA)}),
 Object.freeze({id:"audit-append",role:audit,sql:IDENTITY_AUDIT_INSERT_SQL,
  check:auditParams})
]);
const contracts=new Map(manifest.map(x=>[x.sql,x]));
if(contracts.size!==manifest.length)throw new Error("duplicate canonical identity SQL");
export function assertIdentitySqlContract(role,statement,params){
 if(typeof statement!=="string"||statement.length>20000||
   !exactArray(params)||params.length>7)deny();
 const contract=contracts.get(statement);
 if(!contract||!(contract.role==="*"||contract.role===role))deny();
 // SQL guard is exercised before any acquisition of a privileged pool socket.
 if(!contract.check(params))deny();
 return contract.id;
}
export const identitySqlContractIds=Object.freeze(manifest.map(x=>x.id));
