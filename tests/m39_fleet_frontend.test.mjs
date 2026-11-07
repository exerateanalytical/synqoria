// M39 frontend verification: dictionaries, proxy allow-list, client behaviour, management-plane safety rules and static UX rules.
// Run: node --test tests/m39_fleet_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/fleet/i18n.ts");
const policy = await imp("lib/fleet/proxy-policy.ts");
const client = await imp("lib/fleet/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";
const javaDir = join(root, "services/cell-lifecycle-core/src/main/java/org/synqoria/fleet");
const compDir = join(web, "components/fleet");
const tsx = () => readdirSync(compDir).filter(x => x.endsWith(".tsx"));

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.migration"], i18n.dictionaries.en["nav.migration"]);
});

test("every translation key used by a component exists, including the dynamic families", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 for (const f of tsx()) {
  const src = readFileSync(join(compDir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
 }
 for (const s of ["PROVISIONING", "QUALIFIED", "ACTIVE", "DRAINING", "FENCED", "MIGRATING", "RETIRED", "FAILED"]) assert.ok(keys.has(`enum.cell.${s}`), s);
 for (const s of ["PREPARED", "COPYING", "CAUGHT_UP", "FROZEN", "FENCED", "VERIFIED", "SWITCHED", "OBSERVING", "RETIRED", "ROLLED_BACK", "FAILED"]) assert.ok(keys.has(`enum.mig.${s}`), s);
 for (const s of ["COPY", "CATCH_UP", "FREEZE", "FENCE", "VERIFY", "SWITCH", "OBSERVE", "RETIRE"]) assert.ok(keys.has(`enum.step.${s}`), s);
 for (const s of ["DATABASE", "NETWORK", "IDENTITY", "STORAGE", "CAPACITY", "MONITORING", "BACKUP", "MIGRATION_COMPAT"]) assert.ok(keys.has(`enum.check.${s}`), s);
 for (const s of ["DEDICATED_DATABASE", "DEDICATED_CLUSTER", "IN_REGION_ONLY", "IN_COUNTRY_ONLY", "SAME_CONTINENT", "SMALL", "STANDARD", "LARGE"]) assert.ok(keys.has(`pr.${s}`), s);
});

test("server error codes users can hit have translated messages", () => {
 const codes = new Set();
 for (const f of ["FleetService", "RoutingService", "ReleaseService", "MigrationService", "Platform", "domain/Domain"])
  for (const m of readFileSync(join(javaDir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)*)"/g)) codes.add(m[1]);
 const must = ["permission_denied", "tenant_scope_denied", "approver_must_be_independent", "completer_must_be_independent", "stale_version", "invalid_transition", "duplicate_record", "reason_required",
  "not_qualified", "qualification_incomplete", "stale_placement_epoch", "incompatible_release", "evidence_mismatch", "verification_failed", "irreversible", "rollout_stopped", "stage_not_open",
  "stage_incomplete", "health_gate_failed", "migration_in_progress", "residency_violation", "route_unavailable", "route_in_migration", "lag_too_high", "evidence_required", "invalid_provisioning", "invalid_pagination"];
 for (const c of must) { assert.ok(codes.has(c), `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CM"), "fr");
 assert.equal(i18n.safetyLabel("en", "BACKUP"), "Backup");
});

test("proxy allow-list admits only documented browser routes and never health reports or routing lookups", () => {
 for (const p of ["tenants", "cells", `cells/${U}`, `cells/${U}/qualifications`, "provisioning-requests", "routes", "releases", "rollouts", `rollouts/${U}`, "migrations", `migrations/${U}`]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of ["provisioning-requests", `provisioning-requests/${U}/decision`, `provisioning-requests/${U}/application`, `provisioning-requests/${U}/rollback`, `cells/${U}/transition`, `cells/${U}/qualifications`,
  `qualifications/${U}/checks`, `qualifications/${U}/completion`, "releases", `releases/${U}/decision`, "rollouts", `rollouts/${U}/advance`, `rollouts/${U}/stop`, `rollouts/${U}/cells/${U}/evidence`,
  "routes", `routes/${U}/switch`, "migrations", `migrations/${U}/steps`, `migrations/${U}/rollback`]) assert.ok(policy.isAllowed("POST", p), p);
 for (const p of [`cells/${U}/health`, "../x", "actuator/health", `migrations/${U}/delete`, "migrations/not-a-uuid/steps", `rollouts/${U}/cells/not-a-uuid/evidence`]) assert.ok(!policy.isAllowed("POST", p), p);
 for (const p of ["routing/acme.prod", `cells/${U}/health`]) assert.ok(!policy.isAllowed("GET", p) && !policy.isAllowed("POST", p), p);
 assert.ok(!policy.isAllowed("GET", `migrations/${U}/steps`), "mutations are not readable");
 assert.ok(!policy.isAllowed("PUT", "routes") && !policy.isAllowed("DELETE", `cells/${U}`));
 assert.ok(policy.isSafeSegments(["rollouts", U, "cells", U, "evidence"].slice(0, 4)) && !policy.isSafeSegments(["a", "b", "c", "d", "e"]) && !policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments([]));
 assert.ok(policy.safeQuery(new URLSearchParams("state=ACTIVE&page=0&size=15&tenantId=" + U)));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("tenants=*")), null);
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "cells")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "stale_placement_epoch", details: []}), {status: 409}); };
  const r = await client.call("POST", `migrations/${U}/steps`, {body: {}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "stale_placement_epoch");
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/fleet\/proxy\/migrations\//);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "cells")).kind, "unavailable");
  assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the five canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["fleet", "Fleet"], ["provisioning", "Provisioning"], ["qualification", "Qualification"], ["releases", "Releases"], ["migration", "Migration"]]) {
  const page = `app/workforce/fleet/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import ${comp} from`));
  assert.ok(existsSync(join(web, `components/fleet/${comp}.tsx`)));
 }
 assert.ok(existsSync(join(web, "app/workforce/fleet/page.tsx")));
 const proxy = read("app/api/fleet/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_FLEET_API_ORIGIN", "/api/v1/fleet/"]) assert.ok(proxy.includes(needle), needle);
});

test("safety rules: evidence, current epoch, independent approval and honest limits are wired into the screens", () => {
 const mig = read("components/fleet/Migration.tsx");
 assert.ok(/expectedEpoch/.test(mig) && /mg\.switchNote/.test(mig) && /mg\.irreversible/.test(mig) && /sourceDigest/.test(mig));
 const rel = read("components/fleet/Releases.tsx");
 assert.ok(/artifactDigest:evDigest/.test(rel) && /\/evidence/.test(rel) && /advance/.test(rel));
 const q = read("components/fleet/Qualification.tsx");
 assert.ok(/ql\.missing/.test(q) && /completion/.test(q));
 const prov = read("components/fleet/Provisioning.tsx");
 assert.ok(/executorResult/.test(prov) && /DEDICATED_DATABASE/.test(prov) && !/SHARED/.test(prov));
 const all = tsx().map(f => readFileSync(join(compDir, f), "utf8")).join("\n");
 assert.ok(!/\/health|routing\//.test(all.replace(/fl\.health|enum\.health|\.health\b|health\b/g, "")), "browser code must not report health or fetch signed routing decisions");
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/fleet/fleet.module.css");
 assert.ok(!/box-shadow|text-shadow|drop-shadow|transform:\s*scale|margin:\s*-/.test(css));
 assert.ok(!/overflow:\s*hidden/.test(css) && !/font-size:\s*(?:[0-9]|1[01])px/.test(css));
 assert.ok(/prefers-reduced-motion/.test(css) && /minmax\(0,1fr\)/.test(css) && /@media\(max-width:720px\)/.test(css));
 const allowed = new Set(["#073656", "#1664d4", "#1253b1", "#0e438f", "#0b4aa2", "#f7f5f0", "#fff", "#ffffff", "#f0eee7", "#172b4d", "#475569", "#64748b", "#dedcd5",
  "#f5f7fa", "#e2e8f0", "#e8f0fe", "#eaf2ff", "#174ea6", "#e8f5ec", "#166534", "#fff4d6", "#854d0e", "#fdecec", "#b42318", "#912018", "#edf2f7"]);
 for (const m of css.matchAll(/#[0-9a-fA-F]{3,6}\b/g)) assert.ok(allowed.has(m[0].toLowerCase()), `colour ${m[0]}`);
 assert.ok(/\.btn\{[^}]*min-height:44px/.test(css) && /\.input\{[^}]*min-height:44px/.test(css));
});

test("every control is wired or disabled with a visible reason; no dead links", () => {
 for (const f of tsx()) {
  const src = readFileSync(join(compDir, f), "utf8");
  assert.ok(!/href="#"|onClick=\{\(\)=>\{\}\}|onClick=\{undefined\}/.test(src), `${f}: dead control`);
  for (const m of src.matchAll(/<button\b[^>]*>/g)) {
   assert.match(m[0], /type="button"|type=\{/, `${f}: button without type`);
   if (!/aria-pressed/.test(m[0])) assert.match(m[0], /onClick=/, `${f}: button without handler: ${m[0].slice(0, 80)}`);
  }
  for (const m of src.matchAll(/<ActionButton\b[^>]*?\/?>/gs)) assert.match(m[0], /onClick=/, `${f}: ActionButton without handler`);
 }
 const ui = read("components/fleet/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
