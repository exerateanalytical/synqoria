// M30 frontend verification: dictionaries, proxy allow-list, client logic and static UX rules.
// Run: node --test tests/m30_content_frontend.test.mjs   (Node 22.18+ strips TypeScript types natively)
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,readdirSync,existsSync} from "node:fs";
import {dirname,join} from "node:path";
import {fileURLToPath,pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const web = join(root, "apps/web");
const imp = p => import(pathToFileURL(join(web, p)).href);
const i18n = await imp("lib/content/i18n.ts");
const policy = await imp("lib/content/proxy-policy.ts");
const client = await imp("lib/content/client.ts");
const read = p => readFileSync(join(web, p), "utf8");
const U = "123e4567-e89b-42d3-a456-426614174000";

test("English and French dictionaries define exactly the same keys with matching placeholders", () => {
 const en = Object.keys(i18n.dictionaries.en).sort(), fr = Object.keys(i18n.dictionaries.fr).sort();
 assert.deepEqual(fr, en);
 const ph = s => (s.match(/\{\w+\}/g) ?? []).sort().join();
 for (const k of en) {
  assert.ok(i18n.dictionaries.en[k].trim() && i18n.dictionaries.fr[k].trim(), `empty ${k}`);
  assert.equal(ph(i18n.dictionaries.fr[k]), ph(i18n.dictionaries.en[k]), `placeholder mismatch ${k}`);
 }
 assert.notEqual(i18n.dictionaries.fr["nav.packages"], i18n.dictionaries.en["nav.packages"]);
});

test("every translation key used by a component exists", () => {
 const keys = new Set(Object.keys(i18n.dictionaries.en));
 const dir = join(web, "components/content");
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  for (const m of src.matchAll(/\bt\("([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/(?:title|intro|message)="([\w.]+)"/g)) if (m[1].includes(".")) assert.ok(keys.has(m[1]), `${f}: unknown key ${m[1]}`);
  for (const m of src.matchAll(/\bkey:"([\w.]+)"/g)) assert.ok(keys.has(m[1]), `${f}: key ${m[1]}`);
  for (const m of src.matchAll(/,"((?:tm|fm|pk|ap|ac|nav)\.[\w.]+)"\]/g)) assert.ok(keys.has(m[1]), `${f}: key ${m[1]}`);
 }
});

test("server error, enum and validation codes are translated", () => {
 const dir = join(root, "services/clinical-content-core/src/main/java/org/synqoria/content");
 const codes = new Set();
 for (const f of ["TerminologyService", "FormularyService", "PackageService", "ActivationService", "domain/Domain"])
  for (const m of readFileSync(join(dir, `${f}.java`), "utf8").matchAll(/"([a-z]+(?:_[a-z]+)+)"/g)) codes.add(m[1]);
 const mustHave = ["permission_denied", "reviewer_must_be_independent", "activator_must_be_independent", "stale_version", "import_rejected", "version_exists",
  "mapping_conflict", "approvals_incomplete", "approval_expired", "activation_conflict", "nothing_to_roll_back", "dependency_not_active", "invalid_transition", "reason_required"];
 for (const c of mustHave) { assert.ok(codes.has(c), `server no longer emits ${c}`); assert.notEqual(i18n.errorKey(c), "err.generic", c); }
 assert.equal(i18n.errorKey("brand_new_code"), "err.generic");
 const dom = readFileSync(join(dir, "domain/Domain.java"), "utf8") + readFileSync(join(dir, "PackageService.java"), "utf8") + readFileSync(join(dir, "ActivationService.java"), "utf8");
 for (const m of dom.matchAll(/"((?:CHECKSUM_MISMATCH|DUPLICATE_CODE|DANGLING_PARENT|CYCLE|INVALID_CODE|INVALID_STATUS|INVALID_DISPLAY_EN|REMOVED_WITHOUT_DEPRECATION|EMPTY_IMPORT|NO_GOLDEN_CASES|CODE_UNKNOWN|CODE_NOT_ACTIVE|UNDECLARED_SYSTEM|DEPENDENCY_UNAVAILABLE|NOT_APPROVED|APPROVALS_INCOMPLETE|APPROVAL_EXPIRED|NOT_YET_EFFECTIVE|EXPIRED|NOT_NEWER_THAN_ACTIVE|ACTIVATOR_IS_AUTHOR|DEPENDENCY_NOT_ACTIVE|NOT_PUBLISHED|LICENSE_MISSING))[:"]/g))
  assert.ok(i18n.dictionaries.en[`code.${m[1]}`], `no label for ${m[1]}`);
 assert.equal(i18n.safetyLabel("en", "CODE_UNKNOWN:D9"), "Referenced code does not exist (D9)");
 assert.equal(i18n.translate("fr", "common.page", {page: 2, pages: 5}), "Page 2 sur 5");
 assert.equal(i18n.detectLang(null, "fr-CA"), "fr");
 for (const s of ["PUBLISHED", "REJECTED"]) assert.ok(i18n.dictionaries.en[`enum.vstatus.${s}`]);
 for (const s of ["DRAFT", "VALIDATED", "VALIDATION_FAILED", "IN_REVIEW", "APPROVED", "REJECTED", "REVOKED", "RETIRED"]) assert.ok(i18n.dictionaries.fr[`enum.package.${s}`], s);
});

test("proxy allow-list admits only documented browser routes", () => {
 for (const p of ["code-systems", `code-systems/${U}/versions`, "concepts", `code-system-versions/${U}/roots`, `code-system-versions/${U}/concepts/D1A`, "mappings", "formulary",
  `formulary/${U}`, "packages", `packages/${U}`, "activations/active", "activations", "activations/impact"]) assert.ok(policy.isAllowed("GET", p), p);
 for (const p of ["code-systems", `code-systems/${U}/imports`, "mappings", `mappings/${U}/decision`, "formulary", `formulary/${U}/update`, `formulary/${U}/submit`, `formulary/${U}/withdraw`,
  `formulary/${U}/decision`, "packages", `packages/${U}/validate`, `packages/${U}/submit`, `packages/${U}/review`, `packages/${U}/evaluate`, `approvals/${U}/revoke`, "activations", `activations/${U}/rollback`])
  assert.ok(policy.isAllowed("POST", p), p);
 for (const p of ["../x", "actuator/health", `packages/${U}/delete`, "packages/not-a-uuid/validate", `code-system-versions/${U}/concepts/a b`, `orders/${U}`])
  assert.ok(!policy.isAllowed("POST", p) && !policy.isAllowed("GET", p), p);
 assert.ok(!policy.isAllowed("PUT", "packages") && !policy.isAllowed("DELETE", `packages/${U}`));
 assert.ok(!policy.isAllowed("GET", `packages/${U}/validate`));
 assert.ok(policy.isSafeSegments(["code-system-versions", U, "concepts", "D1"]));
 assert.ok(!policy.isSafeSegments(["a", ".."]) && !policy.isSafeSegments(["a%2fb"]) && !policy.isSafeSegments([]));
 assert.ok(policy.safeQuery(new URLSearchParams("q=a&page=0&size=25&systemId=" + U)));
 assert.equal(policy.safeQuery(new URLSearchParams("cell=x")), null);
 assert.equal(policy.safeQuery(new URLSearchParams("facility=x")), null);
});

test("client: CSV parsing, canonical checksum equals the server algorithm, JSON guard, failure classification", async () => {
 const csv = 'code,status,displayEn,displayFr,parent,synonyms\r\nD1,ACTIVE,"Alpha, with comma",Alpha fr,,a;b\nD1A,ACTIVE,Beta,,D1,\n';
 const {concepts, problems} = client.parseConceptCsv(csv);
 assert.deepEqual(problems, []);
 assert.equal(concepts.length, 2);
 assert.equal(concepts[0].displayEn, "Alpha, with comma");
 assert.deepEqual(concepts[0].synonyms, ["a", "b"]);
 assert.equal(concepts[1].parent, "D1");
 assert.ok(client.parseConceptCsv('a,b,"unterminated').problems.includes("UNTERMINATED_QUOTE"));
 assert.ok(client.parseConceptCsv("only,two").problems[0].startsWith("BAD_COLUMNS"));
 // server algorithm: sha256("D1|ACTIVE|Alpha, with comma|Alpha fr|\nD1A|ACTIVE|Beta||D1")
 const {createHash} = await import("node:crypto");
 const expected = createHash("sha256").update("D1|ACTIVE|Alpha, with comma|Alpha fr|\nD1A|ACTIVE|Beta||D1").digest("hex");
 assert.equal(await client.contentChecksum(concepts), expected);
 assert.equal(await client.contentChecksum([...concepts].reverse()), expected, "order independent");
 assert.equal(client.parseJsonObject("{").problem, "invalid_json");
 assert.equal(client.parseJsonObject("[]").problem, "not_an_object");
 assert.equal(client.parseJsonObject('{"a":1}').value.a, 1);
 assert.equal(client.parseJsonObject("x".repeat(500_000)).problem, "definition_too_large");
 assert.deepEqual([401, 403, 404, 409, 422, 500].map(client.classify), ["unauthorized", "forbidden", "notfound", "conflict", "invalid", "unavailable"]);
 assert.deepEqual(client.parseCodes("peanut, EGG;peanut"), ["PEANUT", "EGG"]);
});

test("client call() never throws and sends idempotency + CSRF on writes", async () => {
 const realFetch = globalThis.fetch, realNav = Object.getOwnPropertyDescriptor(globalThis, "navigator");
 try {
  Object.defineProperty(globalThis, "navigator", {value: {onLine: false}, configurable: true});
  assert.equal((await client.call("GET", "packages")).kind, "offline");
  Object.defineProperty(globalThis, "navigator", {value: {onLine: true}, configurable: true});
  let seen;
  globalThis.fetch = async (url, init) => { seen = {url, init}; return new Response(JSON.stringify({error: "approvals_incomplete", details: ["APPROVALS_INCOMPLETE"]}), {status: 409}); };
  const r = await client.call("POST", "activations", {body: {a: 1}, key: U});
  assert.equal(r.kind, "conflict"); assert.equal(r.code, "approvals_incomplete"); assert.deepEqual(r.details, ["APPROVALS_INCOMPLETE"]);
  assert.equal(seen.init.headers["Idempotency-Key"], U); assert.equal(seen.init.credentials, "same-origin");
  assert.match(String(seen.url), /^\/api\/content\/proxy\/activations$/);
  globalThis.fetch = async () => { throw new TypeError("net"); };
  assert.equal((await client.call("GET", "packages")).kind, "unavailable");
  globalThis.fetch = async () => new Response("<html>", {status: 502});
  assert.equal((await client.call("GET", "packages")).kind, "unavailable");
 } finally {
  globalThis.fetch = realFetch;
  if (realNav) Object.defineProperty(globalThis, "navigator", realNav); else delete globalThis.navigator;
 }
});

test("the five canonical routes, hub and guarded proxy exist", () => {
 for (const [dir, comp] of [["terminology", "Terminology"], ["formulary", "Formulary"], ["packages", "Packages"], ["approvals", "Approvals"], ["activation", "Activation"]]) {
  const page = `app/workforce/content/${dir}/page.tsx`;
  assert.ok(existsSync(join(web, page)), page);
  assert.match(read(page), new RegExp(`import \\{${comp}\\}`));
  assert.ok(existsSync(join(web, `components/content/${comp}.tsx`)));
 }
 assert.ok(existsSync(join(web, "app/workforce/content/page.tsx")));
 const proxy = read("app/api/content/proxy/[...segments]/route.ts");
 for (const needle of ["isAllowed", "opsCheckCsrf", "opsCheckOrigin", "Idempotency-Key", 'redirect:"error"', "no-store", "SYNQORIA_CONTENT_API_ORIGIN", "/api/v1/content/"]) assert.ok(proxy.includes(needle), needle);
});

test("static UX rules (no glow, no scaling, no clipping, approved palette, 44px targets)", () => {
 const css = read("components/content/content.module.css");
 assert.ok(!/box-shadow|text-shadow|drop-shadow|transform:\s*scale|margin:\s*-/.test(css));
 assert.ok(!/overflow:\s*hidden/.test(css) && !/font-size:\s*(?:[0-9]|1[01])px/.test(css));
 assert.ok(/prefers-reduced-motion/.test(css) && /minmax\(0,1fr\)/.test(css) && /@media\(max-width:720px\)/.test(css));
 const allowed = new Set(["#073656", "#1664d4", "#1253b1", "#0e438f", "#0b4aa2", "#f7f5f0", "#fff", "#ffffff", "#f0eee7", "#172b4d", "#475569", "#64748b", "#dedcd5",
  "#f5f7fa", "#e2e8f0", "#e8f0fe", "#eaf2ff", "#174ea6", "#e8f5ec", "#166534", "#fff4d6", "#854d0e", "#fdecec", "#b42318", "#912018", "#edf2f7"]);
 for (const m of css.matchAll(/#[0-9a-fA-F]{3,6}\b/g)) assert.ok(allowed.has(m[0].toLowerCase()), `colour ${m[0]}`);
 assert.ok(/\.btn\{[^}]*min-height:44px/.test(css) && /\.input\{[^}]*min-height:44px/.test(css));
});

test("every control is wired or disabled with a visible reason; no dead links", () => {
 const dir = join(web, "components/content");
 for (const f of readdirSync(dir).filter(x => x.endsWith(".tsx"))) {
  const src = readFileSync(join(dir, f), "utf8");
  assert.ok(!/href="#"|onClick=\{\(\)=>\{\}\}|onClick=\{undefined\}/.test(src), `${f}: dead control`);
  for (const m of src.matchAll(/<button\b[^>]*>/g)) {
   assert.match(m[0], /type="button"|type=\{/, `${f}: button without type`);
   if (!/role="tab"/.test(m[0]) && !/aria-pressed/.test(m[0]) && f !== "ui.tsx") assert.match(m[0], /onClick=/, `${f}: button without handler: ${m[0].slice(0, 80)}`);
  }
  for (const m of src.matchAll(/<ActionButton\b[^>]*?\/?>/gs)) assert.match(m[0], /onClick=/, `${f}: ActionButton without handler`);
 }
 const ui = read("components/content/ui.tsx");
 assert.ok(/aria-describedby=\{why\?id:undefined\}/.test(ui) && /disabled=\{!!why\|\|busy\}/.test(ui));
 assert.ok(/role="alert"/.test(ui) && /role="status"/.test(ui) && /htmlFor=\{id\}/.test(ui));
});
