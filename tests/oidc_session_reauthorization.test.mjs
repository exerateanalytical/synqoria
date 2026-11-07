import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {reauthorizeClinicalStaffRequest,revokeStaffRequestSession,readOpaqueTicketCookie,
 clearStaffTicketCookieOptions,StaffReauthorizationError} from "../apps/web/lib/oidc-session-reauthorization.mjs";
import {STAFF_TICKET_COOKIE} from "../apps/web/lib/opaque-staff-session.mjs";
import {createPostgresStaffSessionStore,SESSION_INSERT_SQL,SESSION_READ_SQL,
 SESSION_REVOKE_SQL} from "../apps/web/lib/postgres-staff-session-store.mjs";
const env={SYNQORIA_STAFF_TOKEN_WRAP_KEY:randomBytes(32).toString("hex")};
const now=Date.parse("2026-11-05T21:50:00Z"),nowSec=Math.floor(now/1000);
const issuer="https://auth.synqoria.org/realms/hospital",clientId="synqoria-staff";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",shift="44444444-4444-4444-8444-444444444444";
const record={
 actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,
 role:"nurse",subject:"staff-identity-001",revision:17,
 capabilities:["facility:view","patient:view"],bearer:"aaa.bbb.ccc",
 issuedAt:now,expiresAt:now+300000
};
const introspectionConfig={issuer,clientId,
 clientSecret:"TestVaultSecret-"+ "Z".repeat(40),apiAudience:"synqoria-clinical-core",
 introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect"};
const workforceConfig={issuer,workforceOrigin:"https://workforce.synqoria-clinical.org/",
 expectedTenantId:tenant,expectedFacilityId:facility};
const dto={actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,role:"nurse",
 subject:record.subject,issuer,status:"ACTIVE",revision:17,
 shiftStartsAt:"2026-11-05T18:00:00Z",shiftEndsAt:"2026-11-06T02:00:00Z",
 capabilities:record.capabilities};
const json=data=>new Response(JSON.stringify(data),{status:200,headers:{
 "Content-Type":"application/json","Cache-Control":"no-store"}});
function backend({active=true,grant={}}={}){
 const calls=[];
 const fetcher=async url=>{
  calls.push(url);
  if(url===introspectionConfig.introspectionEndpoint)return json({
   active,sub:record.subject,client_id:clientId,iss:issuer,
   exp:nowSec+300,aud:introspectionConfig.apiAudience,token_type:"Bearer"
  });
  if(url==="https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active")
   return json({...dto,...grant});
  throw Error("Unexpected source");
 };
 return {calls,fetcher};
}
function sessionDb(){
 const rows=new Map();
 const client={query:async(sql,args)=>{
  if(sql===SESSION_INSERT_SQL){
   rows.set(args[0],{sealed_record:args[1],expires_at:new Date(args[2])});
   return {rowCount:1,rows:[]};
  }
  if(sql===SESSION_READ_SQL){
   const row=rows.get(args[0]);
   return row?{rowCount:1,rows:[row]}:{rowCount:0,rows:[]};
  }
  if(sql===SESSION_REVOKE_SQL){
   const existed=rows.delete(args[0]);return {rowCount:Number(existed),rows:[]};
  }
  throw Error("unapproved DB command");
 }};
 return createPostgresStaffSessionStore(client,{env,now:()=>now});
}
const req=ref=>new Request("https://staff.synqoria.org/workforce/clinical/G-S03",
 {headers:{Cookie:STAFF_TICKET_COOKIE+"="+ref}});
const call=(ref,store,fx)=>reauthorizeClinicalStaffRequest(req(ref),{
 sessionStore:store,nowMs:now,introspectionConfig,workforceConfig,fetcher:fx
});
const reject=fn=>assert.rejects(fn,StaffReauthorizationError);

test("P03-B0038 decrypts opaque ticket and requires fresh IdP+shift checks for every request",async()=>{
 const store=sessionDb(),ref=await store.issue(record),source=backend();
 const a=await call(ref,store,source.fetcher),b=await call(ref,store,source.fetcher);
 assert.deepEqual(a,{actorId:actor,tenantId:tenant,facilityId:facility,
  capabilities:["facility:view","patient:view"],bearer:record.bearer,
  sessionExpiresAt:record.expiresAt});
 assert.deepEqual(a,b);
 assert.equal(Object.isFrozen(a),true);
 assert.equal(source.calls.length,4);
 assert.deepEqual(source.calls.slice(0,2),[introspectionConfig.introspectionEndpoint,
  "https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active"]);
 assert.equal("patientId" in a,false);
});
test("P03-B0038 source revocation, roster revision and privilege drift block access",async()=>{
 for(const source of [backend({active:false}),
   backend({grant:{revision:18}}),backend({grant:{role:"physician"}}),
   backend({grant:{facilityId:actor}}),backend({grant:{capabilities:["patient:view"]}}),
   backend({grant:{status:"SUSPENDED"}})]){
  const store=sessionDb(),ref=await store.issue(record);
  await reject(()=>call(ref,store,source.fetcher));
 }
});
test("P03-B0038 duplicate cookies and forged ticket cannot reach IdP or workforce",async()=>{
 const store=sessionDb(),valid=await store.issue(record),source=backend();
 assert.equal(readOpaqueTicketCookie(STAFF_TICKET_COOKIE+"="+valid),valid);
 assert.equal(readOpaqueTicketCookie(STAFF_TICKET_COOKIE+"="+valid+"; "+STAFF_TICKET_COOKIE+"="+valid),null);
 await reject(()=>call("forged",store,source.fetcher));
 await reject(()=>reauthorizeClinicalStaffRequest(
  new Request("https://staff.synqoria.org/",{headers:{Cookie:
   STAFF_TICKET_COOKIE+"="+valid+"; "+STAFF_TICKET_COOKIE+"="+valid}}),{
   sessionStore:store,introspectionConfig,workforceConfig,fetcher:source.fetcher,nowMs:now
  }));
 assert.equal(source.calls.length,0);
});
test("P03-B0038 logout atomically revokes and enforces clearing the secure cookie",async()=>{
 const store=sessionDb(),ref=await store.issue(record),source=backend();
 assert.equal((await revokeStaffRequestSession(req(ref),{sessionStore:store})).revoked,true);
 assert.equal((await revokeStaffRequestSession(req(ref),{sessionStore:store})).revoked,false);
 await reject(()=>call(ref,store,source.fetcher));
 assert.equal(source.calls.length,0);
 assert.deepEqual(clearStaffTicketCookieOptions(),{httpOnly:true,secure:true,
  sameSite:"strict",path:"/",maxAge:0,priority:"high"});
});
test("P03-B0038 database outage must not silently grant stale access",async()=>{
 const ref="B".repeat(43),source=backend();
 const bad={read:async()=>{throw Error("db offline");},revoke:async()=>{throw Error("db offline");}};
 await reject(()=>call(ref,bad,source.fetcher));
 await reject(()=>revokeStaffRequestSession(req(ref),{sessionStore:bad}));
 assert.equal(source.calls.length,0);
});
