import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dispatchVerifiedStaffAuthRoute} from "../apps/web/lib/verified-staff-auth-route.mjs";
const path=new URL("../apps/web/app/api/auth/oidc/start/route.ts",import.meta.url);
test("B0065 Next OIDC start route is dynamic Node and uses server trusted dispatcher",()=>{
 const source=readFileSync(path,"utf8");
 assert.match(source,/export const runtime="nodejs"/);
 assert.match(source,/export const dynamic="force-dynamic"/);
 assert.match(source,/export const revalidate=0/);
 assert.match(source,/import \{dispatchVerifiedStaffAuthRoute\} from "\.\.\/\.\.\/\.\.\/\.\.\/\.\.\/lib\/verified-staff-auth-route\.mjs"/);
 assert.match(source,/dispatchVerifiedStaffAuthRoute\("start",request\)/);
 assert.doesNotMatch(source,/SYNQORIA_STAFF_COOKIE_KEY|localStorage|process\.env\.|sealStaffSession\(/i);
});
test("B0065 auth start has no auto-login if verified runtime is missing",async()=>{
 const result=await dispatchVerifiedStaffAuthRoute("start",new Request(
  "https://staff.synqoria.org/api/auth/oidc/start"));
 assert.equal(result.status,503);
 assert.equal(result.headers.get("set-cookie"),null);
});
