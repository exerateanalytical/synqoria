// M38 frontend verification: dictionaries, proxy allow-list, client behaviour, safety rules and static UX rules.
// Run: node --test tests/m38_offline_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/offline/i18n.ts");
const policy = await imp("lib/offline/proxy-policy.ts");
const client = await imp("lib/offline/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";
const javaDir = join(root, "services/offline-sync-core/src/main/java/org/synqoria/offline");
const compDir = join(web, "components/offline");
const tsx = () => readdirSync(compDir).filter(x => x.endsWith(".tsx"));

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.conflicts"], i18n.dictionaries.en["nav.conflicts"]);
});

test("every translation key used by a component exists", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 for (const f of tsx()) {
  const src = readFileSync(join(compDir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
 }
 for (const s of ["PROPOSED", "FROZEN", "FENCED", "APPROVED", "COMPLETED", "ABORTED"]) assert.ok(keys.has(`tr.step.${s}`), s);
 for (const s of ["APPLIED", "CONFLICT", "REJECTED", "EXPIRED", "RESOLVED"]) assert.ok(keys.has(`enum.cmd.${s}`), s);
});

test("server error and outcome codes users can hit have translated messages", () => {
 const codes = new Set();
 for (const f of ["SyncService", "TransferService", "Platform", "domain/Domain"])
  for (const m of readFileSync(join(javaDir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)+)"/g)) codes.add(m[1]);
 const must = ["permission_denied", "approver_must_be_independent", "resolver_must_be_independent", "stale_version", "invalid_transition", "record_frozen", "epoch_changed", "head_moved",
  "fence_version_mismatch", "evidence_required", "already_owner", "reason_required", "device_revoked", "invalid_pagination"];
 for (const c of must) { assert.ok(codes.has(c), `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 const outcomes = ["applied", "applied_merged", "stale_base", "incompatible_changes", "stale_writer_fenced", "command_expired", "queue_age_exceeded", "clock_skew", "no_offline_contract",
  "safety_gate_required", "operation_not_offline", "unknown_record", "invalid_epoch", "invalid_base"];
 for (const c of outcomes) { assert.ok(codes.has(c) || ["applied"].includes(c), `server no longer emits ${c}`); assert.notEqual(i18n.safetyLabel("en", c), c, c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CM"), "fr");
});

test("proxy allow-list admits only documented browser routes: the installed device may upload its own queue, nothing authors heads, contracts or devices", () => {
 for (const p of ["status", "devices", "heads", "contracts", "commands", `commands/${U}`, "conflicts", `conflicts/${U}`, "transfers", "reconciliations"]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of [`devices/${U}/revocation`, `conflicts/${U}/resolution`, "transfers", `transfers/${U}/freeze`, `transfers/${U}/fence`, `transfers/${U}/decision`, `transfers/${U}/completion`, `transfers/${U}/abort`])
  assert.ok(policy.isAllowed("POST", p), p);
 for (const p of ["heads", `heads/${U}/revisions`, "contracts", `contracts/${U}/decision`, "../x", "actuator/health", `conflicts/${U}/delete`, "conflicts/not-a-uuid/resolution"])
  assert.ok(!policy.isAllowed("POST", p), p);
 assert.ok(policy.isAllowed("POST", "devices"), "enrollment is forwarded; the service enforces m38:device_manage");
 assert.ok(policy.isAllowed("POST", "sync/batches") && policy.isAllowed("GET", `devices/${U}/cursor`), "the installed device uploads its own queue and reads its cursor");
 assert.ok(!policy.isAllowed("GET", "sync/batches") && !policy.isAllowed("GET", `transfers/${U}/freeze`), "mutations are not readable");
 assert.ok(read("app/api/offline/proxy/[...segments]/route.ts").includes("1048576"), "a full 50-command batch must fit the proxy body bound");
 assert.ok(!policy.isAllowed("PUT", "transfers") && !policy.isAllowed("DELETE", `commands/${U}`));
 assert.ok(!policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]));
 assert.ok(policy.safeQuery(new URLSearchParams("status=OPEN&page=0&size=10&deviceId=" + U)));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=x")), null);
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "status")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "stale_version", details: []}), {status: 409}); };
  const r = await client.call("POST", `conflicts/${U}/resolution`, {body: {}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "stale_version");
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/offline\/proxy\/conflicts\//);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "status")).kind, "unavailable");
  assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the five canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["status", "StatusBoard"], ["queue", "Queue"], ["conflicts", "Conflicts"], ["transfers", "Transfers"], ["history", "History"]]) {
  const page = `app/workforce/offline/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import ${comp} from`));
  assert.ok(existsSync(join(web, `components/offline/${comp}.tsx`)));
 }
 assert.ok(existsSync(join(web, "app/workforce/offline/page.tsx")));
 const proxy = read("app/api/offline/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_OFFLINE_API_ORIGIN", "/api/v1/offline/"]) assert.ok(proxy.includes(needle), needle);
});

test("safety rules: no silent overwrite, independent decisions, evidence required, browser cannot upload commands", () => {
 const all = tsx().map(f => readFileSync(join(compDir, f), "utf8")).join("\n");
 assert.ok(!/sync\/batches|heads\/|contracts\//.test(all), "browser code must not upload commands or publish heads");
 const conflicts = read("components/offline/Conflicts.tsx");
 assert.ok(/expectedHeadVersion:c\.currentHeadVersion/.test(conflicts), "the reviewer confirms the head version they saw");
 assert.ok(/reasonCode/.test(conflicts) && /cf\.acceptWarn/.test(conflicts));
 const tr = read("components/offline/Transfers.tsx");
 assert.ok(/lastSourceVersion:open\.currentHeadVersion/.test(tr) && /"fence"/.test(tr) && /"completion"/.test(tr));
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/offline/offline.module.css");
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
 const ui = read("components/offline/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
