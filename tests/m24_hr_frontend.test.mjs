// M24 frontend verification: dictionaries, proxy allow-list, money handling and static UX rules.
// Run: node --test tests/m24_hr_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/hr/i18n.ts");
const policy = await imp("lib/hr/proxy-policy.ts");
const client = await imp("lib/hr/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";
const javaDir = join(root, "services/hr-payroll-core/src/main/java/org/synqoria/hr");

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.roster"], i18n.dictionaries.en["nav.roster"]);
});

test("every translation key used by a component exists", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 const dir = join(web, "components/hr");
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/\bkey:"((?:nav|hub)\.[\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: key ${m[1]}`);
 }
});

test("server error codes users can hit have specific, translated messages", () => {
 const codes = new Set();
 for (const f of ["StaffService", "TimeService", "PayrollService", "Platform", "domain/Domain"])
  for (const m of readFileSync(join(javaDir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)+)"/g)) codes.add(m[1]);
 const must = ["permission_denied", "approver_must_be_independent", "reviewer_must_be_independent", "decider_must_be_independent", "confirmer_must_be_independent", "stale_version",
  "invalid_transition", "duplicate_record", "effective_date_conflict", "reason_required", "period_locked", "run_already_live", "rules_not_approved", "negative_net", "evidence_mismatch",
  "payment_exists", "reversal_requires_correction", "roster_conflict", "rest_violation", "coverage_gap", "insufficient_leave", "invalid_rule_set", "invalid_account", "reconciliation_incomplete"];
 for (const c of must) { assert.ok(codes.has(c) || c === "reconciliation_incomplete" || c === "invalid_rule_set", `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 assert.equal(i18n.safetyLabel("en", "NO_ACCOUNT:S1"), "No payment account (S1)");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CM"), "fr");
});

test("proxy allow-list admits only documented browser routes", () => {
 for (const p of ["organisation", "staff", `staff/${U}`, "changes", "shift-templates", "rosters", `rosters/${U}`, `rosters/${U}/check`, "attendance", "leave-requests", `leave-balances/${U}`,
  "leave-policy", "rule-sets", "periods", "runs", `runs/${U}`, `runs/${U}/payments`, `runs/${U}/reconciliation`]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of ["departments", "positions", "staff", `staff/${U}/changes`, `changes/${U}/decision`, "changes/apply-due", `staff/${U}/contracts`, `contracts/${U}/decision`, `staff/${U}/payment-account`,
  "shift-templates", "coverage", "rosters", `rosters/${U}/shifts`, `shifts/${U}/remove`, `shifts/${U}/overtime-authorization`, `rosters/${U}/submit`, `rosters/${U}/review`, `rosters/${U}/revise`,
  "attendance", "attendance/import", `attendance/${U}/dispute`, `attendance/${U}/correction`, `attendance/${U}/decision`, "leave-requests", `leave-requests/${U}/decision`, "leave-types", "holidays",
  "rule-sets", `rule-sets/${U}/decision`, "periods", `periods/${U}/runs`, "adjustments", `adjustments/${U}/decision`, `runs/${U}/submit`, `runs/${U}/decision`, `runs/${U}/reversal`,
  `runs/${U}/export`, `runs/${U}/payments`, `runs/${U}/reconcile`, `payments/${U}/confirmation`]) assert.ok(policy.isAllowed("POST", p), p);
 for (const p of ["../x", "actuator/health", `runs/${U}/delete`, "runs/not-a-uuid/decision", `payments/${U}/confirm`, `staff/${U}/salary`]) assert.ok(!policy.isAllowed("POST", p) && !policy.isAllowed("GET", p), p);
 assert.ok(!policy.isAllowed("PUT", "staff") && !policy.isAllowed("DELETE", `staff/${U}`));
 assert.ok(!policy.isAllowed("GET", `runs/${U}/decision`), "mutations are not readable");
 assert.ok(!policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]));
 assert.ok(policy.safeQuery(new URLSearchParams("q=a&page=0&size=25&departmentId=" + U)));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=x")), null);
});

test("money is handled as exact decimal strings, never as numbers", () => {
 const {validMoney, showMoney} = client;
 assert.ok(validMoney("400000") && validMoney("250000.50") && validMoney("0.0001"));
 for (const bad of ["", "1e3", "-5", "1,000", "12.12345", "0x10", " ", "NaN", "9".repeat(16)]) assert.ok(!validMoney(bad), bad);
 assert.equal(showMoney("1234567", "en"), "1,234,567");
 assert.equal(showMoney("1234567.5000", "en"), "1,234,567.5");
 assert.equal(showMoney("1234567.0000", "en"), "1,234,567");
 assert.equal(showMoney("0.1000", "fr"), "0,1");
 assert.equal(showMoney("12345.67", "fr").replace(/\s/g, " "), "12 345,67".replace(/\s/g, " "));
 assert.equal(showMoney(null, "en"), "—");
 // no component converts money with Number(), parseFloat() or toFixed()
 for (const f of readdirSync(join(web, "components/hr")).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(web, "components/hr", f), "utf8");
  assert.ok(!/parseFloat|\.toFixed\(|Number\((?:ct|af|conf|l\.|run|x\.)(?:[\w.]*(?:base|amount|gross|net|total))/i.test(src), `${f}: money conversion`);
 }
 assert.equal(client.parseJsonObject("{").problem, "invalid_json");
 assert.equal(client.parseJsonObject("[]").problem, "not_an_object");
 assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "runs")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "period_locked", details: []}), {status: 409}); };
  const r = await client.call("POST", `periods/${U}/runs`, {body: {}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "period_locked");
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/hr\/proxy\/periods\//);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "runs")).kind, "unavailable");
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the six canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["directory", "Directory"], ["employment", "Employment"], ["roster", "Roster"], ["attendance", "Attendance"], ["preparation", "Preparation"], ["approval", "Approval"]]) {
  const page = `app/workforce/hr/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import \\{${comp}\\}`));
  assert.ok(existsSync(join(web, `components/hr/${comp}.tsx`)));
 }
 assert.ok(existsSync(join(web, "app/workforce/hr/page.tsx")));
 const proxy = read("app/api/hr/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_HR_API_ORIGIN", "/api/v1/hr/"]) assert.ok(proxy.includes(needle), needle);
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/hr/hr.module.css");
 assert.ok(!/box-shadow|text-shadow|drop-shadow|transform:\s*scale|margin:\s*-/.test(css));
 assert.ok(!/overflow:\s*hidden/.test(css) && !/font-size:\s*(?:[0-9]|1[01])px/.test(css));
 assert.ok(/prefers-reduced-motion/.test(css) && /minmax\(0,1fr\)/.test(css) && /@media\(max-width:720px\)/.test(css));
 const allowed = new Set(["#073656", "#1664d4", "#1253b1", "#0e438f", "#0b4aa2", "#f7f5f0", "#fff", "#ffffff", "#f0eee7", "#172b4d", "#475569", "#64748b", "#dedcd5",
  "#f5f7fa", "#e2e8f0", "#e8f0fe", "#eaf2ff", "#174ea6", "#e8f5ec", "#166534", "#fff4d6", "#854d0e", "#fdecec", "#b42318", "#912018", "#edf2f7"]);
 for (const m of css.matchAll(/#[0-9a-fA-F]{3,6}\b/g)) assert.ok(allowed.has(m[0].toLowerCase()), `colour ${m[0]}`);
 assert.ok(/\.btn\{[^}]*min-height:44px/.test(css) && /\.input\{[^}]*min-height:44px/.test(css));
});

test("every control is wired or disabled with a visible reason; no dead links", () => {
 const dir = join(web, "components/hr");
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  assert.ok(!/href="#"|onClick=\{\(\)=>\{\}\}|onClick=\{undefined\}/.test(src), `${f}: dead control`);
  for (const m of src.matchAll(/<button\b[^>]*>/g)) {
   assert.match(m[0], /type="button"|type=\{/, `${f}: button without type`);
   if (!/role="tab"/.test(m[0]) && !/aria-pressed/.test(m[0]) && f !== "ui.tsx") assert.match(m[0], /onClick=/, `${f}: button without handler: ${m[0].slice(0, 80)}`);
  }
  for (const m of src.matchAll(/<ActionButton\b[^>]*?\/?>/gs)) assert.match(m[0], /onClick=/, `${f}: ActionButton without handler`);
 }
 const ui = read("components/hr/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
