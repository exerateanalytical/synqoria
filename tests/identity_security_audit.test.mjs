import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {createIdentitySecurityAuditWriter,IdentitySecurityAuditError,
 IDENTITY_AUDIT_INSERT_SQL} from "../apps/web/lib/identity-security-audit.mjs";
const pepper=randomBytes(32).toString("hex"),now=Date.now();
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 alternate="33333333-3333-4333-8333-333333333333",correlationId="44444444-4444-4444-8444-444444444444";
const good={actorId:actor,tenantId:tenant,kind:"LOGIN_GRANTED",outcome:"PERMITTED",correlationId};
const error=fn=>assert.rejects(fn,IdentitySecurityAuditError);
test("P03-B0043 store only HMAC-pseudonymous security metadata, never PHI, actor ID or tokens",async()=>{
 const calls=[],db={query:async(sql,args)=>{calls.push([sql,args]);return {rowCount:1,rows:[]};}};
 const writer=createIdentitySecurityAuditWriter(db,{pepper,now:()=>now});
 const result=await writer.append(good);
 assert.match(result.eventId,/^[0-9a-f-]{36}$/i);
 assert.equal(result.createdAt,now);
 const [sql,args]=calls[0];
 assert.equal(sql,IDENTITY_AUDIT_INSERT_SQL);
 assert.equal(args[3],"LOGIN_GRANTED");assert.equal(args[4],"PERMITTED");
 assert.equal(args[5],correlationId);assert.equal(args[6],now);
 assert.match(args[1],/^[0-9a-f]{64}$/);assert.match(args[2],/^[0-9a-f]{64}$/);
 assert.notEqual(args[1],actor);assert.notEqual(args[2],tenant);
 assert.ok(!JSON.stringify(args).includes(actor));
 assert.ok(!JSON.stringify(args).includes(tenant));
 assert.ok(!JSON.stringify(args).includes(pepper));
 assert.equal("actorId" in result,false);assert.equal("tenantId" in result,false);
});
test("P03-B0043 staff pseudonym scoped to tenant, stable within tenant and unlinkable across tenants",async()=>{
 const calls=[],writer=createIdentitySecurityAuditWriter({
  query:async(_,args)=>{calls.push(args);return {rowCount:1,rows:[]};}
 },{pepper,now:()=>now});
 await writer.append(good);
 await writer.append({...good,kind:"LOGIN_DENIED",outcome:"DENIED"});
 await writer.append({...good,tenantId:alternate});
 assert.equal(calls[0][1],calls[1][1]);
 assert.equal(calls[0][2],calls[1][2]);
 assert.notEqual(calls[0][1],calls[2][1]);
 assert.notEqual(calls[0][2],calls[2][2]);
});
test("P03-B0043 refuses arbitrary fields and forged audit decisions before DB writes",async()=>{
 const calls=[],writer=createIdentitySecurityAuditWriter({
  query:async(...a)=>{calls.push(a);return {rowCount:1,rows:[]};}
 },{pepper,now:()=>now});
 for(const bad of [
  {...good,patientName:"Should be rejected"},
  {...good,bearer:"privateToken"},
  {...good,kind:"PATIENT_DIAGNOSIS"},
  {...good,kind:"LOGIN_GRANTED",outcome:"DENIED"},
  {...good,kind:"PATIENT_DISCLOSURE_DENIED",outcome:"PERMITTED"},
  {...good,actorId:"not-a-uuid"},
  {...good,correlationId:"not-a-uuid"}
 ])await error(()=>writer.append(bad));
 assert.equal(calls.length,0);
});
test("P03-B0043 DB error, missing pepper and suspicious rowCount fail closed",async()=>{
 assert.throws(()=>createIdentitySecurityAuditWriter({query:async()=>{}},{pepper:"x"}),IdentitySecurityAuditError);
 const offline=createIdentitySecurityAuditWriter({query:async()=>{throw Error("DB down");}},{pepper,now:()=>now});
 await error(()=>offline.append(good));
 const zero=createIdentitySecurityAuditWriter({query:async()=>({rowCount:0,rows:[]})},{pepper,now:()=>now});
 await error(()=>zero.append(good));
 const unexpected=createIdentitySecurityAuditWriter({query:async()=>({rowCount:1,rows:[{}]})},{pepper,now:()=>now});
 await error(()=>unexpected.append(good));
});
