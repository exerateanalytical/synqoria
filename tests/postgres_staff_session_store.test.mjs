import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {createPostgresStaffSessionStore,StaffSessionStoreError,
 SESSION_INSERT_SQL,SESSION_READ_SQL,SESSION_REVOKE_SQL} from "../apps/web/lib/postgres-staff-session-store.mjs";
const env={SYNQORIA_STAFF_TOKEN_WRAP_KEY:randomBytes(32).toString("hex")};
const now=Date.parse("2026-11-05T21:50:00Z");
const record={
 actorId:"11111111-1111-4111-8111-111111111111",
 tenantId:"22222222-2222-4222-8222-222222222222",
 facilityId:"33333333-3333-4333-8333-333333333333",
 shiftId:"44444444-4444-4444-8444-444444444444",
 subject:"staff-identity-001",role:"nurse",revision:17,
 capabilities:["facility:view","patient:view"],bearer:"aaa.bbb.ccc",
 issuedAt:now,expiresAt:now+300000
};
function fakeDb(){
 const rows=new Map(),calls=[];
 return {calls,rows,query:async(sql,args)=>{
  calls.push({sql,args});
  if(sql===SESSION_INSERT_SQL){
   if(rows.has(args[0]))return {rowCount:0,rows:[]};
   rows.set(args[0],{sealed_record:args[1],expires_at:new Date(args[2]),revoked_at:null});
   return {rowCount:1,rows:[]};
  }
  if(sql===SESSION_READ_SQL){
   const v=rows.get(args[0]);
   return v&&!v.revoked_at?{rowCount:1,rows:[{sealed_record:v.sealed_record,expires_at:v.expires_at}]}:{rowCount:0,rows:[]};
  }
  if(sql===SESSION_REVOKE_SQL){
   const v=rows.get(args[0]);
   if(!v||v.revoked_at)return {rowCount:0,rows:[]};
   v.revoked_at=new Date(now);return {rowCount:1,rows:[]};
  }
  throw Error("unexpected SQL");
 }};
}
const rejected=async fn=>assert.rejects(fn,StaffSessionStoreError);
test("P03-B0037 inserts encrypted bearer, reads only by digest and revokes server side",async()=>{
 const db=fakeDb(),store=createPostgresStaffSessionStore(db,{env,now:()=>now});
 const ticket=await store.issue(record);
 assert.match(ticket,/^[A-Za-z0-9_-]{43}$/);
 assert.equal(db.calls[0].sql,SESSION_INSERT_SQL);
 assert.match(db.calls[0].args[0],/^[0-9a-f]{64}$/);
 assert.equal(db.calls[0].args[2],record.expiresAt);
 assert.ok(!db.calls[0].args[1].includes(record.bearer));
 assert.ok(!db.calls[0].args[1].includes(record.facilityId));
 assert.ok(!db.calls.some(x=>x.args.includes(record.bearer)));
 assert.deepEqual(await store.read(ticket),record);
 assert.equal(db.calls[1].sql,SESSION_READ_SQL);
 assert.deepEqual(db.calls[1].args,[db.calls[0].args[0]]);
 assert.equal(await store.revoke(ticket),true);
 assert.equal(await store.revoke(ticket),false);
 assert.equal(await store.read(ticket),null);
 assert.equal(db.calls[2].sql,SESSION_REVOKE_SQL);
});
test("P03-B0037 malformed tickets, failed inserts and unavailable SQL never issue a session",async()=>{
 const bad=createPostgresStaffSessionStore(fakeDb(),{env,now:()=>now});
 await rejected(()=>bad.read("invalid"));
 await rejected(()=>bad.revoke("invalid"));
 const fail=createPostgresStaffSessionStore({query:async()=>{throw Error("postgres unavailable");}},{env,now:()=>now});
 await rejected(()=>fail.issue(record));
 await rejected(()=>fail.read("A".repeat(43))); // wrong ticket encoding still fails closed
 await rejected(()=>fail.revoke("A".repeat(43)));
 const conflict=createPostgresStaffSessionStore({query:async()=>({rowCount:0,rows:[]})},{env,now:()=>now});
 await rejected(()=>conflict.issue(record));
});
test("P03-B0037 DB ciphertext substitution, expiry mismatch and bogus result shape refuse access",async()=>{
 const db=fakeDb(),store=createPostgresStaffSessionStore(db,{env,now:()=>now});
 const ticket=await store.issue(record),digest=db.calls[0].args[0];
 db.rows.get(digest).expires_at=new Date(record.expiresAt-1000);
 await rejected(()=>store.read(ticket));
 db.rows.get(digest).expires_at=new Date(record.expiresAt);
 db.rows.get(digest).sealed_record="invalid";
 await rejected(()=>store.read(ticket));
 const corrupt=createPostgresStaffSessionStore({query:async()=>({rowCount:2,rows:[{},{}]})},{env,now:()=>now});
 await rejected(()=>corrupt.issue(record));
});
test("P03-B0037 input record never bypasses encryption validation or time cap",async()=>{
 const db=fakeDb(),store=createPostgresStaffSessionStore(db,{env,now:()=>now});
 await rejected(()=>store.issue({...record,capabilities:["patient:view","patient:view"]}));
 await rejected(()=>store.issue({...record,expiresAt:now+1200000}));
 await rejected(()=>store.issue({...record,patientId:"leak"}));
 assert.equal(db.calls.length,0);
});
