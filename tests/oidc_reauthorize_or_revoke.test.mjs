import test from "node:test";
import assert from "node:assert/strict";
import {reauthorizeOrRevokeStaff,RevokedStaffAuthorizationError} from "../apps/web/lib/oidc-reauthorize-or-revoke.mjs";
const ref="B".repeat(43);
const request=new Request("https://staff.synqoria.org/clinical",{headers:{
 cookie:"__Host-synqoria-session="+ref
}});
const denied=fn=>assert.rejects(fn,RevokedStaffAuthorizationError);
test("B0056 identity-source failure revokes server ticket before rejecting request",async()=>{
 const calls=[];
 const store={
  read:async()=>{calls.push("read");return null;},
  revoke:async ticket=>{calls.push(ticket);return true;}
 };
 await denied(()=>reauthorizeOrRevokeStaff(request,{sessionStore:store}));
 assert.deepEqual(calls,["read",ref]);
});
test("B0056 revoked browser cookie or missing store is never authorized",async()=>{
 const events=[];
 const store={read:async()=>null,revoke:async()=>{events.push("revoke");return true;}};
 await denied(()=>reauthorizeOrRevokeStaff(new Request("https://staff.synqoria.org/"),{sessionStore:store}));
 assert.equal(events.length,0);
 await denied(()=>reauthorizeOrRevokeStaff(request,{sessionStore:{read:async()=>null}}));
});
test("B0056 storage revocation outage does not grant access",async()=>{
 const calls=[];
 const store={read:async()=>null,revoke:async()=>{calls.push("revoke");throw Error("offline");}};
 await denied(()=>reauthorizeOrRevokeStaff(request,{sessionStore:store}));
 assert.deepEqual(calls,["revoke"]);
});
