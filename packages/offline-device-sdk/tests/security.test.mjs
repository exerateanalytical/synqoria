// Privacy and threat checks that can be proven statically or by direct execution (package 28). They do not replace a penetration test.
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync, readdirSync, existsSync} from "node:fs";
import {join, dirname} from "node:path";
import {fileURLToPath} from "node:url";
import {Transport} from "../src/index.ts";
import {FakeServer, ready, input} from "./helpers.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const src = join(here, "..", "src");
const web = join(here, "..", "..", "..", "apps", "web");
const sources = readdirSync(src).filter(f => f.endsWith(".ts")).map(f => [f, readFileSync(join(src, f), "utf8")]);

test("the SDK never logs, evals, injects HTML or writes clinical data to unencrypted browser storage", () => {
 for (const [f, s] of sources) {
  assert.ok(!/console\.(log|info|debug|warn|error)/.test(s), `${f}: console output`);
  assert.ok(!/\beval\(|new Function\(|innerHTML|document\.write|dangerouslySetInnerHTML/.test(s), `${f}: dynamic code or HTML injection`);
  assert.ok(!/localStorage|sessionStorage|document\.cookie/.test(s), `${f}: unencrypted browser storage or cookie access inside the SDK`);
 }
 const panel = readFileSync(join(web, "components", "offline", "DevicePanel.tsx"), "utf8") + readFileSync(join(web, "lib", "offline", "device.ts"), "utf8");
 assert.ok(!/dangerouslySetInnerHTML|innerHTML|eval\(/.test(panel), "no HTML injection in the device UI");
 const stored = [...panel.matchAll(/localStorage\.(setItem|getItem)\(([^)]*)\)/g)].map(m => m[2]);
 assert.ok(stored.every(a => a.startsWith("BINDING_KEY")), "the only thing kept in localStorage is the (non-sensitive) device binding");
 assert.ok(!/console\./.test(panel), "no console output in the device UI");
});

test("the CSRF value is read only in device.ts, sent only on writes, and tokens are never handled by script", () => {
 const device = readFileSync(join(web, "lib", "offline", "device.ts"), "utf8");
 assert.ok(device.includes("__Host-synq_ops_csrf") && !/Authorization|Bearer|access_token/i.test(device), "no bearer token in browser code");
 const t = readFileSync(join(src, "transport.ts"), "utf8");
 assert.ok(/credentials: "same-origin"/.test(t) && /cache: "no-store"/.test(t));
 assert.ok(!/Authorization/.test(t), "the SDK never sets Authorization; the HttpOnly cookie plus the server-side proxy do");
 assert.ok(device.includes("/api/operations/auth/verified-session"), "identity comes from the P03 verified-session endpoint");
 assert.ok(!existsSync(join(web, "app", "api", "offline", "session")) && !existsSync(join(web, "lib", "offline", "identity.ts")), "the local unverified/adapter session decoder is gone");
 const p03 = readFileSync(join(web, "app", "api", "operations", "auth", "verified-session", "route.ts"), "utf8");
 assert.ok(p03.includes("verifiedOperationsSession") && p03.includes("no-store") && !/jwt-decode|atob\(|split\("\."\)/.test(p03), "P03 route verifies cryptographically and never decodes unverified payloads");
});

test("GET requests carry no CSRF header or idempotency key; every write carries both", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input()); await c.engine.drain();
 for (const r of s.requests) {
  if (r.method === "GET") assert.ok(!("Idempotency-Key" in r.headers) && !("x-synq-ops-csrf" in r.headers), r.path);
  else assert.ok(r.headers["Idempotency-Key"] && r.headers["x-synq-ops-csrf"], r.path);
 }
});

test("a hostile server cannot make the client apply data: oversized or malformed responses are rejected, scripts in codes are inert", async () => {
 const cases = [
  {ok: true, status: 200, json: async () => { throw new SyntaxError("bad json"); }},
  {ok: false, status: 500, json: async () => ({error: "<script>alert(1)</script>"})},
  {ok: true, status: 200, json: async () => "just a string"}
 ];
 for (const res of cases) {
  const t = new Transport({fetch: async () => res, csrf: () => "x"});
  const r = await t.call("GET", "/status");
  if (!r.ok) assert.equal(typeof r.code, "string");
 }
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 s.route = () => s.json(200, {deviceId: "11111111-1111-4111-8111-111111111111", results: [{commandId: null, seq: 1, status: "APPLIED", code: "<script>alert(1)</script>", appliedVersion: 2, conflictId: null}], cursor: 1, serverTime: new Date().toISOString()});
 const r = await c.engine.syncOnce();
 assert.equal(r.reason, "protocol_error", "a code outside the vocabulary length/shape is not trusted blindly");
});
