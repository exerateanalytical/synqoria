import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {sealOpaqueStaffTicket,openOpaqueStaffTicket,staffTicketReferenceDigest,
 staffTicketCookieOptions,STAFF_TICKET_COOKIE,StaffTicketError} from "../apps/web/lib/opaque-staff-session.mjs";
const env={SYNQORIA_STAFF_TOKEN_WRAP_KEY:randomBytes(32).toString("hex")};
const now=Date.parse("2026-11-05T21:50:00Z");
const record={
 actorId:"11111111-1111-4111-8111-111111111111",
 tenantId:"22222222-2222-4222-8222-222222222222",
 facilityId:"33333333-3333-4333-8333-333333333333",
 shiftId:"44444444-4444-4444-8444-444444444444",
 role:"nurse",subject:"staff-identity-001",revision:17,
 capabilities:["facility:view","patient:view"],
 bearer:"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0."+ "Z".repeat(256),
 issuedAt:now,expiresAt:now+300000
};
const opts={env,now};
const denied=fn=>assert.throws(fn,StaffTicketError);
test("P03-B0036 encrypts server-only records and exposes an unguessable opaque ticket",()=>{
 const one=sealOpaqueStaffTicket(record,opts),two=sealOpaqueStaffTicket(record,opts);
 assert.match(one.reference,/^[A-Za-z0-9_-]{43}$/);
 assert.match(one.digest,/^[0-9a-f]{64}$/);
 assert.equal(one.digest,staffTicketReferenceDigest(one.reference));
 assert.notEqual(one.reference,two.reference);
 assert.notEqual(one.digest,two.digest);
 assert.notEqual(one.ciphertext,two.ciphertext);
 assert.ok(!one.reference.includes(record.bearer));
 assert.ok(!one.ciphertext.includes(record.bearer));
 assert.ok(!one.ciphertext.includes(record.tenantId));
 assert.deepEqual(openOpaqueStaffTicket(one.reference,one.ciphertext,opts),record);
 assert.deepEqual(openOpaqueStaffTicket(one.reference,one.ciphertext,{env,now:now+120000}),record);
 assert.equal(Object.isFrozen(openOpaqueStaffTicket(one.reference,one.ciphertext,opts)),true);
 assert.deepEqual(staffTicketCookieOptions(),{httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:600,priority:"high"});
 assert.equal(STAFF_TICKET_COOKIE,"__Host-synqoria-session");
});
test("P03-B0036 ticket/digest substitution, ciphertext tampering and wrong envelope key deny",()=>{
 const one=sealOpaqueStaffTicket(record,opts),two=sealOpaqueStaffTicket(record,opts);
 denied(()=>openOpaqueStaffTicket(two.reference,one.ciphertext,opts));
 denied(()=>openOpaqueStaffTicket(one.reference,one.ciphertext.replace(/.$/,x=>x==="a"?"b":"a"),opts));
 denied(()=>openOpaqueStaffTicket(one.reference,one.ciphertext,{env:{SYNQORIA_STAFF_TOKEN_WRAP_KEY:randomBytes(32).toString("hex")},now}));
 denied(()=>openOpaqueStaffTicket("attacker",one.ciphertext,opts));
 denied(()=>staffTicketReferenceDigest("invalid"));
});
test("P03-B0036 timeout, oversized token, replay past expiry and invalid privilege DTO deny",()=>{
 const v=sealOpaqueStaffTicket(record,opts);
 denied(()=>openOpaqueStaffTicket(v.reference,v.ciphertext,{env,now:record.expiresAt}));
 denied(()=>sealOpaqueStaffTicket({...record,expiresAt:now+900000},opts));
 denied(()=>sealOpaqueStaffTicket({...record,issuedAt:now-3600000},opts));
 denied(()=>sealOpaqueStaffTicket({...record,capabilities:["patient:view","patient:view"]},opts));
 denied(()=>sealOpaqueStaffTicket({...record,capabilities:["patient:view","admin:*"]},opts));
 denied(()=>sealOpaqueStaffTicket({...record,actorId:"not-a-uuid"},opts));
 denied(()=>sealOpaqueStaffTicket({...record,patientId:"sensitive"},opts));
 denied(()=>sealOpaqueStaffTicket({...record,bearer:"a.b."+"x".repeat(9000)},opts));
 denied(()=>sealOpaqueStaffTicket(record,{env:{},now}));
});
test("P03-B0036 encoded tokens never appear in cookies or session references",()=>{
 const v=sealOpaqueStaffTicket(record,opts);
 const browserOnly={name:STAFF_TICKET_COOKIE,value:v.reference,options:staffTicketCookieOptions()};
 const s=JSON.stringify(browserOnly);
 assert.ok(!s.includes("bearer"));
 assert.ok(!s.includes("patient:view"));
 assert.ok(!s.includes(record.facilityId));
 assert.ok(!s.includes(record.bearer));
});
