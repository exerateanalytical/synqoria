import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
test("B0073 startup is wired to attested identity roles and policy checks, not demo sessions",()=>{
 const read=path=>readFileSync(new URL("../"+path,import.meta.url),"utf8");
 const startup=read("apps/web/lib/staff-identity-production-startup.mjs");
 const bootstrap=read("apps/web/lib/staff-route-bootstrap.mjs");
 const verified=read("apps/web/lib/staff-identity-runtime.mjs");
 assert.match(startup,/requireClinicalIdentitySafety\(env\)/);
 assert.match(startup,/createRestrictedIdentityPools\(/);
 assert.match(startup,/createRestrictedIdentityTransport\(/);
 assert.match(startup,/initializeVerifiedStaffRoutes\(/);
 assert.match(bootstrap,/bindVerifiedStaffRouteRuntime\(runtime\)/);
 assert.match(verified,/await verifyIdentitySqlClients\(options\)/);
 assert.match(verified,/await verifyIdentitySchemaPolicies\(options.replaySql\)/);
 assert.doesNotMatch(startup,/ALLOW_DEMO|ALLOW_LEGACY|mockIdentity|fakeSession/i);
});
test("B0073 no operational screen acceptance or clinical release is asserted by this patch",()=>{
 const source=readFileSync(new URL("../apps/web/lib/staff-identity-production-startup.mjs",import.meta.url),"utf8");
 assert.doesNotMatch(source,/clinicalAccepted:\s*true|productionDeployed:\s*true|release:\s*"APPROVED"/);
});
