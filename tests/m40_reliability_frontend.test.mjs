// M40 frontend verification: dictionaries, proxy allow-list, client behaviour, honesty rules and static UX rules.
// Run: node --test tests/m40_reliability_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/reliability/i18n.ts");
const policy = await imp("lib/reliability/proxy-policy.ts");
const client = await imp("lib/reliability/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";
const javaDir = join(root, "services/reliability-core/src/main/java/org/synqoria/reliability");
const compDir = join(web, "components/reliability");
const tsx = () => readdirSync(compDir).filter(x => x.endsWith(".tsx"));

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.recovery"], i18n.dictionaries.en["nav.recovery"]);
});

test("every translation key used by a component exists, including the dynamic families", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 for (const f of tsx()) {
  const src = readFileSync(join(compDir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
 }
 const families = {
  health: ["HEALTHY", "DEGRADED", "DOWN", "UNKNOWN"], tier: ["CRITICAL", "IMPORTANT", "SUPPORT"], sev: ["SEV1", "SEV2", "SEV3", "SEV4"],
  inc: ["OPEN", "ACKNOWLEDGED", "MITIGATING", "MITIGATED", "RESOLVED"], bk: ["RECORDED", "VERIFIED", "VERIFICATION_FAILED"], kind: ["FULL", "INCREMENTAL", "WAL_ARCHIVE"],
  check: ["INVENTORY", "INTEGRITY", "ENCRYPTION_KEY", "COMPLETENESS", "RESTORE_READINESS"], result: ["PASS", "FAIL"],
  rec: ["PLANNED", "AUTHORIZED", "FENCED", "DB_RESTORED", "OBJECTS_RECONCILED", "AUDIT_RECOVERED", "EFFECTS_RECONCILED", "VALIDATING", "REOPENED", "ABORTED"],
  step: ["FENCE", "RESTORE_DB", "RECONCILE_OBJECTS", "RECOVER_AUDIT", "RECONCILE_EFFECTS", "BEGIN_VALIDATION"],
  scenario: ["DATABASE_FAILURE", "APPLICATION_FAILURE", "INFRASTRUCTURE_LOSS", "CONNECTIVITY_INTERRUPTION", "ISOLATED_CELL_FAILURE"],
  item: ["SMOKE_TESTS", "IDENTITY_CONTINUITY", "CLINICAL_RECONCILIATION", "FINANCIAL_RECONCILIATION", "AUDIT_CONTINUITY", "EGRESS_STATE"],
  level: ["DEBUG", "INFO", "WARN", "ERROR"], source: ["EXERCISE", "REAL"]
 };
 for (const [g, vals] of Object.entries(families)) for (const v of vals) assert.ok(keys.has(`enum.${g}.${v}`), `${g}.${v}`);
});

test("server error codes users can hit have translated messages", () => {
 const codes = new Set();
 for (const f of ["ObservabilityService", "IncidentService", "BackupService", "RecoveryService", "Platform", "domain/Domain"])
  for (const m of readFileSync(join(javaDir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)*)"/g)) codes.add(m[1]);
 const must = ["permission_denied", "approver_must_be_independent", "verifier_must_be_independent", "reopener_must_be_independent", "stale_version", "invalid_transition", "duplicate_record", "reason_required",
  "evidence_required", "backup_not_verified", "wal_gap", "target_out_of_range", "fencing_not_confirmed", "restore_target_mismatch", "objects_unreconciled", "effects_unreconciled", "validation_incomplete",
  "invalid_verification", "invalid_recovery", "invalid_incident", "invalid_query", "invalid_batch", "invalid_pagination"];
 for (const c of must) { assert.ok(codes.has(c), `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.safetyLabel("en", "INTEGRITY"), "Integrity (digest)");
 assert.equal(i18n.safetyLabel("en", "SMOKE_TESTS"), "Smoke tests");
});

test("proxy allow-list admits only documented browser routes and never machine-to-machine ingestion", () => {
 for (const p of ["health-board", "incidents", `incidents/${U}`, "backups", `backups/${U}`, "wal-continuity", "recoveries", `recoveries/${U}`, "recovery-measurements", "telemetry/events", "telemetry/access-log"]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of ["incidents", `incidents/${U}/acknowledgement`, `incidents/${U}/assignment`, `incidents/${U}/notes`, `incidents/${U}/escalation`, `incidents/${U}/status`, `incidents/${U}/resolution`,
  `backups/${U}/verification`, "recoveries", `recoveries/${U}/authorization`, `recoveries/${U}/steps`, `recoveries/${U}/validation`, `recoveries/${U}/reopening`, `recoveries/${U}/abort`]) assert.ok(policy.isAllowed("POST", p), p);
 for (const p of ["services", `services/${U}/signals`, "telemetry/events", "backups", "../x", "actuator/health", `recoveries/${U}/delete`, "recoveries/not-a-uuid/steps"]) assert.ok(!policy.isAllowed("POST", p), p);
 assert.ok(!policy.isAllowed("GET", `recoveries/${U}/steps`), "mutations are not readable");
 assert.ok(!policy.isAllowed("PUT", "backups") && !policy.isAllowed("DELETE", `recoveries/${U}`));
 assert.ok(!policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]) && !policy.isSafeSegments(["a", "b", "c", "d"]));
 assert.ok(policy.safeQuery(new URLSearchParams("correlationId=corr-0001-abcd&level=ERROR&page=0&size=20&from=2026-11-07T06:00:00.000Z")));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=x")), null);
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "health-board")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "wal_gap", details: ["300", "350"]}), {status: 409}); };
  const r = await client.call("POST", `recoveries/${U}/authorization`, {body: {}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "wal_gap"); assert.deepEqual(r.details, ["300", "350"]);
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/reliability\/proxy\/recoveries\//);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "health-board")).kind, "unavailable");
  assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the six canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["health", "Health"], ["incidents", "Incidents"], ["backups", "Backups"], ["recovery", "Recovery"], ["validation", "Validation"], ["telemetry", "Telemetry"]]) {
  const page = `app/workforce/reliability/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import ${comp} from`));
  assert.ok(existsSync(join(web, `components/reliability/${comp}.tsx`)));
 }
 assert.ok(existsSync(join(web, "app/workforce/reliability/page.tsx")));
 const proxy = read("app/api/reliability/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_RELIABILITY_API_ORIGIN", "/api/v1/reliability/"]) assert.ok(proxy.includes(needle), needle);
});

test("honesty rules: unknown health, unverified backups, fencing, independent reopening and no guarantees are wired into the screens", () => {
 const health = read("components/reliability/Health.tsx");
 assert.ok(/"UNKNOWN"/.test(health) && /hl\.freshness/.test(health));
 const bk = read("components/reliability/Backups.tsx");
 assert.ok(/bk\.noChecks/.test(bk) && /observedDigest/.test(bk) && /HEX\.test\(observed\)/.test(bk));
 const rc = read("components/reliability/Recovery.tsx");
 assert.ok(/egressDisabled:egress/.test(rc) && /writersStopped:writers/.test(rc) && /err\.fencing_not_confirmed/.test(rc) && /auditChainDigest/.test(rc));
 const va = read("components/reliability/Validation.tsx");
 assert.ok(/va\.noGuarantee/.test(va) && /reopening/.test(va) && /va\.missing/.test(va) && /enum\.source|"source"/.test(va));
 const all = tsx().map(f => readFileSync(join(compDir, f), "utf8")).join("\n");
 assert.ok(!/POST","services|POST","backups"|POST","telemetry/.test(all), "browser code must not register services, record backups or ingest telemetry");
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/reliability/reliability.module.css");
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
 const ui = read("components/reliability/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
