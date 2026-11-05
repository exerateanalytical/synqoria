import test from "node:test";
import assert from "node:assert/strict";
import {createStaffAuthHttpHandlers} from "../apps/web/lib/staff-auth-http-handlers.mjs";
const issuer="https://identity.synqoria.org/realms/hospital";
const fail=new Error("one-use state already consumed");
const svc={
 begin:async()=>{throw fail;},
 complete:async()=>{throw fail;},
 logout:async()=>{throw fail;}
};
test("B0057 failed callback returns 401 and clears sealed one-use flow cookie",async()=>{
 const handlers=createStaffAuthHttpHandlers(svc,{issuer});
 const response=await handlers.callback(new Request("https://staff.synqoria.org/api/auth/oidc/callback"));
 assert.equal(response.status,401);
 assert.match(response.headers.get("set-cookie"),
  /^__Host-synqoria-oidc-flow=; Path=\/; Max-Age=0; Secure; HttpOnly; SameSite=Strict$/);
 assert.equal(response.headers.get("cache-control"),"no-store, private");
 assert.equal(response.headers.get("location"),null);
 assert.ok(!JSON.stringify([...response.headers]).includes("consumed"));
});
test("B0057 repeated errors do not issue a browser staff session or upstream bearer",async()=>{
 const handlers=createStaffAuthHttpHandlers(svc,{issuer});
 for(let n=0;n<3;n++){
  const r=await handlers.callback(new Request("https://staff.synqoria.org/api/auth/oidc/callback?code=forged"));
  assert.equal(r.status,401);
  assert.ok(!r.headers.get("set-cookie").includes("__Host-synqoria-session"));
  assert.equal(r.body,null);
 }
});
