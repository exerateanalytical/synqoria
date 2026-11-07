// M26 frontend verification: dictionaries, proxy allow-list, client logic and static UX rules.
// Run: node --test tests/m26_nutrition_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const i18n = await import(join(web, "lib/nutrition/i18n.ts").replaceAll("\\", "/").replace(/^/, "file:///"));
const policy = await import(join(web, "lib/nutrition/proxy-policy.ts").replaceAll("\\", "/").replace(/^/, "file:///"));
const client = await import(join(web, "lib/nutrition/client.ts").replaceAll("\\", "/").replace(/^/, "file:///"));
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim().length > 0, `empty en ${k}`);
  assert.ok(i18n.dictionaries.fr[k].trim().length > 0, `empty fr ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.worklist"], i18n.dictionaries.en["nav.worklist"]);
});

test("every translation key used by a component exists", () => {
 const dir = join(web, "components/nutrition");
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
 }
 for (const m of read("components/nutrition/ui.tsx").matchAll(/key:"([\w.]+)"/g)) assert.ok(keys.has(m[1]), `ui: ${m[1]}`);
});

test("every server error code and safety code has a translation", () => {
 const platform = readFileSync(join(root, "services/nutrition-core/src/main/java/org/synqoria/nutrition/Platform.java"), "utf8");
 const codes = new Set();
 for (const f of ["OrderService", "ProductionService", "DeliveryService", "WorklistService", "domain/Domain"])
  for (const m of readFileSync(join(root, `services/nutrition-core/src/main/java/org/synqoria/nutrition/${f}.java`), "utf8").matchAll(/(?:Violation|CommittedRejection)\("([a-z_]+)"/g)) codes.add(m[1]);
 for (const m of platform.matchAll(/"([a-z_]+)"->HttpStatus/g)) codes.add(m[1]);
 // codes that users can actually act on must have a specific message (generic fallback is for the rest)
 const mustHave = ["permission_denied", "approver_must_be_independent", "dispatch_must_be_independent", "ack_must_be_independent",
  "stale_version", "diet_contradictory", "diet_safety_block", "identity_mismatch", "tray_already_issued", "batch_already_planned",
  "allergen_not_excluded", "allergy_reconciliation_required", "cutoff_passed", "substitutions_pending", "invalid_transition"];
 for (const c of mustHave) { assert.ok(codes.has(c) || c === "permission_denied", `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("something_new"), "err.generic");
 const dom = readFileSync(join(root, "services/nutrition-core/src/main/java/org/synqoria/nutrition/domain/Domain.java"), "utf8");
 for (const m of dom.matchAll(/"((?:DIET|ALLERGY|ALLERGEN|FASTING|MEAL)_[A-Z_]+)"/g))
  assert.notEqual(i18n.safetyLabel("en", m[1]), m[1], `no label for ${m[1]}`);
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CA"), "fr");
 assert.equal(i18n.detectLang("en", "fr-CA"), "en");
 assert.equal(i18n.detectLang("xx", "de"), "en");
});

test("proxy allow-list admits only the documented browser routes and never the feed", () => {
 for (const p of ["worklist", `worklist/${U}`, "orders", `orders/${U}`, `orders/${U}/history`, "batches", `batches/${U}`, "trays", "trays/awaiting", `trays/${U}`, "incidents"])
  assert.ok(policy.isAllowed("GET", p), p);
 for (const p of ["orders", `orders/${U}/approve`, `orders/${U}/amend`, `orders/${U}/cancel`, `orders/${U}/revoke`, "batches", `batches/${U}/release`,
  `batches/${U}/prepared`, `batches/${U}/dispatch-approval`, `batches/${U}/cancel`, "substitutions", `substitutions/${U}/decision`, "trays",
  `trays/${U}/dispatch`, `trays/${U}/deliver`, `trays/${U}/acknowledge`, `trays/${U}/return`, `trays/${U}/exceptions`, `incidents/${U}/escalate`, `incidents/${U}/resolve`])
  assert.ok(policy.isAllowed("POST", p), p);
 for (const p of [`feed/patients/${U}/context`, `feed/patients/${U}/allergies`, `feed/fasting/${U}`, "orders/not-a-uuid/approve", `orders/${U}/delete`, "../orders", "actuator/health"])
  assert.ok(!policy.isAllowed("POST", p) && !policy.isAllowed("GET", p), p);
 assert.ok(!policy.isAllowed("PUT", "orders") && !policy.isAllowed("DELETE", `orders/${U}`));
 assert.ok(!policy.isAllowed("GET", `orders/${U}/approve`), "mutations are not readable");
 assert.ok(policy.isSafeSegments(["orders", U]));
 assert.ok(!policy.isSafeSegments(["orders", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]) && !policy.isSafeSegments("x"));
 assert.ok(policy.safeQuery(new URLSearchParams("ward=W1&page=0&size=25")));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=other")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=other")), null);
});

test("client mirrors server diet rules and classifies failures", () => {
 const {dietProblem, parseCodes, validCodes, classify, buildPath} = client;
 assert.equal(dietProblem("REGULAR", "NORMAL", ["LUNCH"], []), null);
 assert.equal(dietProblem("NIL_BY_MOUTH", "NONE", [], []), null);
 assert.equal(dietProblem("NIL_BY_MOUTH", "NORMAL", [], []), "diet_contradictory");
 assert.equal(dietProblem("NIL_BY_MOUTH", "NONE", ["LUNCH"], []), "diet_contradictory");
 assert.equal(dietProblem("REGULAR", "NONE", ["LUNCH"], []), "diet_contradictory");
 assert.equal(dietProblem("CLEAR_LIQUID", "NORMAL", ["LUNCH"], []), "diet_contradictory");
 assert.equal(dietProblem("PUREED", "SOFT_BITE_SIZED", ["LUNCH"], []), "diet_contradictory");
 assert.equal(dietProblem("REGULAR", "NORMAL", [], []), "meal_timing_required");
 assert.equal(dietProblem("RENAL", "NORMAL", ["LUNCH"], ["FLUID_RESTRICTED", "HIGH_FLUID"]), "diet_contradictory");
 assert.deepEqual(parseCodes("peanut, EGG;peanut  nuts"), ["PEANUT", "EGG", "NUTS"]);
 assert.ok(validCodes(["PEANUT"], 5) && !validCodes(["p"], 5) && !validCodes(["A_B", "C"], 5));
 assert.deepEqual([401, 403, 404, 409, 422, 500, 503].map(classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable", "unavailable"]);
 assert.equal(buildPath("worklist", {ward: "W1", q: "", page: 0, x: undefined}), "/api/nutrition/proxy/worklist?ward=W1&page=0");
});

test("client call() never throws: offline, HTTP failures, and success are classified", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "orders")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "stale_version", details: ["X"]}), {status: 409}); };
  const r = await client.call("POST", `orders/${U}/approve`, {body: {expectedVersion: 1}, key: U});
  assert.equal(r.ok, false); assert.equal(r.kind, "conflict"); assert.equal(r.code, "stale_version"); assert.deepEqual(r.details, ["X"]);
  assert.equal(seen.init.headers["Idempotency-Key"], U);
  assert.equal(seen.init.credentials, "same-origin");
  globalThis.fetch = async () => new Response(JSON.stringify({items: [], page: 0, size: 25, total: 0}), {status: 200});
  const ok = await client.call("GET", "worklist"); assert.equal(ok.ok, true); assert.equal(ok.data.total, 0);
  globalThis.fetch = async () => { throw new TypeError("network"); };
  assert.equal((await client.call("GET", "worklist")).kind, "unavailable");
  globalThis.fetch = async () => new Response("<html>", {status: 502});
  assert.equal((await client.call("GET", "worklist")).kind, "unavailable");
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the four canonical routes and the guarded proxy exist", () => {
 for (const [dir, comp] of [["worklist", "Worklist"], ["orders", "Orders"], ["production", "Production"], ["delivery", "Delivery"]]) {
  const page = `app/workforce/nutrition/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import \\{${comp}\\}`));
  assert.ok(existsSync(join(web, `components/nutrition/${comp}.tsx`)));
 }
 const proxy = read("app/api/nutrition/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", "redirect:\"error\"", "no-store"]) assert.ok(proxy.includes(needle), needle);
 assert.ok(!/["'`/]feed/.test(proxy), "browser proxy must not expose the integration-only feed");
});

test("static UX rules from UI/UX Master v1.1 (no glow, no scaling, no clipping, approved palette)", () => {
 const css = read("components/nutrition/nutrition.module.css");
 assert.ok(!/box-shadow|text-shadow|filter:\s*drop-shadow|transform:\s*scale|negative|margin:\s*-/.test(css), "glow/scale/negative margin");
 assert.ok(!/overflow:\s*hidden/.test(css), "blanket overflow hidden");
 assert.ok(!/font-size:\s*(?:[0-9]|1[01])px/.test(css), "text below 12px");
 assert.ok(/prefers-reduced-motion/.test(css));
 assert.ok(/minmax\(0,1fr\)/.test(css) && /@media\(max-width:720px\)/.test(css));
 const allowed = new Set(["#073656", "#1664d4", "#1253b1", "#0e438f", "#0b4aa2", "#f7f5f0", "#fff", "#ffffff", "#f0eee7", "#172b4d", "#475569", "#64748b", "#dedcd5",
  "#f5f7fa", "#e2e8f0", "#e8f0fe", "#eaf2ff", "#174ea6", "#e8f5ec", "#166534", "#fff4d6", "#854d0e", "#fdecec", "#b42318", "#912018", "#edf2f7"]);
 for (const m of css.matchAll(/#[0-9a-fA-F]{3,6}\b/g)) assert.ok(allowed.has(m[0].toLowerCase()), `colour ${m[0]} is not an approved token`);
 // minimum 44px touch targets on controls
 assert.ok(/\.btn\{[^}]*min-height:44px/.test(css) && /\.input\{[^}]*min-height:44px/.test(css));
});

test("every control is wired or disabled with a visible reason; no dead links or empty handlers", () => {
 const dir = join(web, "components/nutrition");
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  assert.ok(!/href="#"|onClick=\{\(\)=>\{\}\}|onClick=\{undefined\}/.test(src), `${f}: dead control`);
  for (const m of src.matchAll(/<button\b[^>]*>/g)) {
   assert.match(m[0], /type="button"|type=\{|type="submit"/, `${f}: button without type: ${m[0].slice(0, 70)}`);
   if (!/role="tab"/.test(m[0]) && !/aria-pressed/.test(m[0]) && f !== "ui.tsx")
    assert.match(m[0], /onClick=/, `${f}: button without handler: ${m[0].slice(0, 70)}`);
  }
  for (const m of src.matchAll(/<ActionButton\b[^>]*?(\/>|>)/gs)) assert.match(m[0], /onClick=/, `${f}: ActionButton without handler`);
 }
 const ui = read("components/nutrition/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui), "disabled ActionButton must explain why");
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /aria-live/.test(ui));
 assert.ok(/htmlFor=\{id\}/.test(ui), "form labels are bound to inputs");
});
