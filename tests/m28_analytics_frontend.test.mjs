// M28 frontend verification: dictionaries, proxy allow-list, number formatting, honesty rules and static UX rules.
// Run: node --test tests/m28_analytics_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/analytics/i18n.ts");
const policy = await imp("lib/analytics/proxy-policy.ts");
const client = await imp("lib/analytics/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";
const javaDir = join(root, "services/analytics-core/src/main/java/org/synqoria/analytics");
const compDir = join(web, "components/analytics");
const tsx = () => readdirSync(compDir).filter(x => x.endsWith(".tsx"));

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.dashboard"], i18n.dictionaries.en["nav.dashboard"]);
});

test("every translation key used by a component exists", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 for (const f of tsx()) {
  const src = readFileSync(join(compDir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/\bkey:"((?:nav|hub)\.[\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: key ${m[1]}`);
 }
 for (const m of read("components/analytics/Analytics.tsx").matchAll(/"(an\.\w+Title|an\.\w+Intro)"/g)) assert.ok(keys.has(m[1]), m[1]);
});

test("server error codes users can hit have specific, translated messages", () => {
 const codes = new Set();
 for (const f of ["IngestService", "MetricService", "ReportService", "Platform", "domain/Domain"])
  for (const m of readFileSync(join(javaDir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)+)"/g)) codes.add(m[1]);
 const must = ["permission_denied", "approver_must_be_independent", "stale_version", "invalid_transition", "report_not_approved", "export_not_ready", "export_expired",
  "unsupported_format", "invalid_period", "invalid_export", "invalid_query", "evidence_mismatch", "invalid_pagination"];
 for (const c of must) { assert.ok(codes.has(c), `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CM"), "fr");
 assert.equal(i18n.safetyLabel("en", "SUPPRESSED"), "Hidden: group too small");
 for (const s of ["OK", "INCOMPLETE", "SUPPRESSED", "NO_DATA"]) assert.notEqual(i18n.enumLabel("en", "state", s), s);
});

test("proxy allow-list admits only documented browser routes and never ingestion or administration", () => {
 for (const p of ["freshness", "metrics", "metric-values", "dashboard", "cell-aggregates", "reports", "runs", `runs/${U}`, "exports"]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of [`reports/${U}/runs`, `runs/${U}/exports`, `exports/${U}/decision`, `exports/${U}/download`, "runs/process"]) assert.ok(policy.isAllowed("POST", p), p);
 for (const p of ["sources", `sources/${U}/events`, `sources/${U}/rebuild`, `sources/${U}/verification`, "quarantine", `quarantine/${U}/discard`, "metrics", `metrics/${U}/values`,
  `metrics/${U}/decision`, "reports", `reports/${U}/decision`, `reports/${U}/schedule`, "schedules/run-due", "exports/expire-due", "../x", "actuator/health", "runs/not-a-uuid/exports"])
  assert.ok(!policy.isAllowed("POST", p), p);
 for (const p of ["sources", "quarantine", `sources/${U}`]) assert.ok(!policy.isAllowed("GET", p), p);
 assert.ok(!policy.isAllowed("PUT", "reports") && !policy.isAllowed("DELETE", `runs/${U}`));
 assert.ok(!policy.isAllowed("GET", `exports/${U}/download`), "mutations are not readable");
 assert.ok(!policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]));
 assert.ok(policy.safeQuery(new URLSearchParams("domain=CLINICAL&metricKey=a.b&from=2026-01-01&to=2026-02-01&page=0&size=25")));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=x")), null);
});

test("numbers are formatted with integer arithmetic and unknown is never shown as zero", () => {
 assert.equal(client.percentText("0.700000", "en"), "70.0%");
 assert.equal(client.percentText("0.7", "fr").replace(/[\s  ]/g, " "), "70,0 %");
 assert.equal(client.percentText("0.1235", "en"), "12.4%");
 assert.equal(client.percentText(null, "en"), "—");
 assert.equal(client.percentText("abc", "en"), "—");
 assert.equal(client.plainText("1234567", "en"), "1,234,567");
 assert.equal(client.plainText("1234567.5000", "en"), "1,234,567.5");
 assert.equal(client.plainText("1234567.0000", "en"), "1,234,567");
 assert.equal(client.plainText(null, "en"), "—");
 const base = {unit: "COUNT", value: "42", numerator: "42"};
 assert.equal(client.showValue({...base, state: "OK"}, "en"), "42");
 assert.equal(client.showValue({...base, state: "SUPPRESSED"}, "en"), "—");
 assert.equal(client.showValue({...base, state: "NO_DATA"}, "en"), "—");
 assert.equal(client.showValue({unit: "COUNT", value: null, numerator: null, state: "OK"}, "en"), "—");
 const rows = [
  {dimensionKey: "ALL", currency: null, periodStart: "2026-01-01", state: "OK", value: "3", numerator: "3"},
  {dimensionKey: "ALL", currency: null, periodStart: "2026-02-01", state: "NO_DATA", value: null, numerator: null},
  {dimensionKey: "ALL", currency: null, periodStart: "2026-03-01", state: "SUPPRESSED", value: "9", numerator: "9"},
  {dimensionKey: "WARD", currency: null, periodStart: "2026-03-01", state: "OK", value: "5", numerator: "5"}];
 const pts = client.seriesPoints(rows, null);
 assert.deepEqual(pts.map(p => p.y), [3, null, null], "unknown and hidden points are gaps, never zero or the hidden number");
 const cur = client.seriesPoints([{...rows[0], currency: "XAF "}, {...rows[0], currency: "EUR", periodStart: "2026-02-01", value: "9", numerator: "9"}], "XAF");
 assert.equal(cur.length, 1, "currencies are never mixed");
 assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
 const src = ["Analytics", "Dashboard", "Reports"].map(f => readFileSync(join(compDir, f + ".tsx"), "utf8")).join("\n");
 assert.ok(!/parseFloat|\.toFixed\(/.test(src), "no float formatting in components");
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "dashboard")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "approver_must_be_independent", details: []}), {status: 409}); };
  const r = await client.call("POST", `exports/${U}/decision`, {body: {}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "approver_must_be_independent");
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/analytics\/proxy\/exports\//);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "dashboard")).kind, "unavailable");
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the five canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["dashboard", "Dashboard"], ["clinical", "Analytics"], ["financial", "Analytics"], ["reports", "Reports"], ["exports", "Exports"]]) {
  const page = `app/workforce/analytics/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import ${comp} from`));
  assert.ok(existsSync(join(web, `components/analytics/${comp}.tsx`)));
 }
 assert.match(read("app/workforce/analytics/clinical/page.tsx"), /domain="CLINICAL"/);
 assert.match(read("app/workforce/analytics/financial/page.tsx"), /domain="FINANCIAL"/);
 assert.ok(existsSync(join(web, "app/workforce/analytics/page.tsx")));
 const proxy = read("app/api/analytics/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_ANALYTICS_API_ORIGIN", "/api/v1/analytics/"]) assert.ok(proxy.includes(needle), needle);
});

test("honesty rules: CSV only, no ingestion from the browser, hidden states explained", () => {
 const ex = read("components/analytics/Exports.tsx");
 assert.ok(/format:"CSV"/.test(ex));
 const all = tsx().map(f => readFileSync(join(compDir, f), "utf8")).join("\n");
 assert.ok(!/["`/]sources\/|["`]quarantine|rebuild/.test(all), "browser code must not call ingestion or administration");
 const parts = read("components/analytics/parts.tsx");
 assert.ok(/explain\.SUPPRESSED/.test(parts) && /explain\.NO_DATA/.test(parts) && /explain\.INCOMPLETE/.test(parts) && /explain\.stale/.test(parts));
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/analytics/analytics.module.css");
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
 const ui = read("components/analytics/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
