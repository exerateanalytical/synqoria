import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {dispatchVerifiedStaffAuthRoute} from "../apps/web/lib/verified-staff-auth-route.mjs";
const path=new URL("../apps/web/app/api/auth/logout/route.ts",import.meta.url);
test("B0067 Next logout route delegates POST only to audited verified server runtime",()=>{
 const file=readFileSync(path,"utf8");
 assert.match(file,/export const runtime="nodejs"/);
 assert.match(file,/export const dynamic="force-dynamic"/);
 assert.match(file,/export async function POST\(request:Request\)/);
 assert.match(file,/dispatchVerifiedStaffAuthRoute\("logout",request\)/);
 assert.doesNotMatch(file,/export async function GET|localStorage|process\.env|clearCookie\(/);
});
test("B0067 unprovisioned logout denies without misleading 204 or leaking tokens",async()=>{
 const r=await dispatchVerifiedStaffAuthRoute("logout",new Request(
  "https://staff.synqoria.org/api/auth/logout",{method:"POST",
  headers:{origin:"https://staff.synqoria.org"}}));
 assert.equal(r.status,503);
 assert.equal(r.headers.get("cache-control"),"no-store, private");
 assert.equal(r.headers.get("location"),null);
});
