import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dispatchVerifiedStaffAuthRoute} from "../apps/web/lib/verified-staff-auth-route.mjs";
const path=new URL("../apps/web/app/api/auth/oidc/callback/route.ts",import.meta.url);
test("B0066 Next callback delegates to cryptographic/audited server-only runtime",()=>{
 const file=readFileSync(path,"utf8");
 assert.match(file,/export const runtime="nodejs"/);
 assert.match(file,/export const dynamic="force-dynamic"/);
 assert.match(file,/dispatchVerifiedStaffAuthRoute\("callback",request\)/);
 assert.doesNotMatch(file,/readSessionCookie|sealStaffSession|localStorage|JSON\.stringify\(.*bearer/);
});
test("B0066 absent trusted identity bootstrap returns a private 503, no callback cookie",async()=>{
 const r=await dispatchVerifiedStaffAuthRoute("callback",new Request(
  "https://staff.synqoria.org/api/auth/oidc/callback?code=forged&state=forged"));
 assert.equal(r.status,503);
 assert.equal(r.headers.get("set-cookie"),null);
 assert.equal(r.headers.get("location"),null);
});
