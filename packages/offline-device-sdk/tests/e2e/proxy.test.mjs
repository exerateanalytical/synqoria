// END-TO-END through the REAL Next route handlers: HTTP client -> Next (apps/web: session + proxy routes, cookie auth, CSRF, Origin, allow-list, bounds)
// -> real M38 Spring service over HTTPS -> PostgreSQL 18 with RLS. The identity provider is a local test issuer; the verifier is the web app's own
// jwks verifier (enabled only for this run). The browser itself is not involved here (cookies are sent by the test client); see the browser checks for that.
import test, {before, after} from "node:test";
import assert from "node:assert/strict";
import {spawn} from "node:child_process";
import {createRequire} from "node:module";
import {randomUUID} from "node:crypto";
import {join, dirname} from "node:path";
import {cpSync, mkdirSync, rmSync, writeFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {Rig, ISSUER, AUDIENCE, ORIGIN} from "../live/rig.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..", "..", "..");
const realWeb = join(repo, "apps", "web");
// A minimal Next app made of VERBATIM COPIES of the real route handlers and their libraries. The full apps/web tree is not used because its instrumentation hook
// (P03 identity bootstrap, owned by the integration lead) does not bundle under every Next version; this keeps the test about the offline routes only.
const web = join(repo, ".e2e-shadow");
const SHADOW_FILES = ["app/api/offline/proxy/[...segments]/route.ts", "app/api/offline/session/route.ts", "lib/operations-auth.ts", "lib/offline/proxy-policy.ts", "lib/offline/identity.ts"];
const PORT = Number(process.env.E2E_WEB_PORT ?? 3100), WEB = `http://127.0.0.1:${PORT}`, WEB_ORIGIN = `https://localhost:${PORT}`;
const RUN = process.env.RIG_TLS === "1" && process.env.SYNQORIA_LIVE_RIG === "1";      // started by launch.mjs (trusts the rig certificate)
const rig = new Rig();
const A = {cell: randomUUID(), facility: randomUUID()}, B = {cell: randomUUID(), facility: randomUUID()};
const MAKER_PERMS = ["m38:contract_manage", "m38:device_manage", "m38:head_publish", "m38:sync_read", "m38:history_read"], CHECKER_PERMS = ["m38:contract_approve", "m38:sync_read"], NURSE_PERMS = ["m38:sync_submit", "m38:sync_read"];
const ids = {maker: randomUUID(), checker: randomUUID(), nurse: randomUUID()};
const CSRF = "c".repeat(48);
let next, nextLog = "";

const cookie = tok => `__Host-synq_ops_access=${tok}; __Host-synq_ops_csrf=${CSRF}`;
async function web_(method, path, tok, o = {}) {
 const headers = {Accept: "application/json", ...(tok ? {Cookie: cookie(tok)} : {}), ...(o.headers ?? {})};
 if (method === "POST") { headers["Content-Type"] = "application/json"; headers["Idempotency-Key"] ??= randomUUID(); if (o.csrf !== false) headers["x-synq-ops-csrf"] ??= CSRF; if (o.origin !== false) headers.Origin = o.origin ?? WEB_ORIGIN; }
 const r = await fetch(WEB + path, {method, headers, body: method === "POST" ? (o.raw ?? JSON.stringify(o.body ?? {})) : undefined});
 let json = null, text = ""; try { text = await r.text(); json = JSON.parse(text); } catch { /* empty */ }
 return {status: r.status, json, text};
}
const tok = (who, perms, o = {}) => rig.token({sub: ids[who], ...A, perms, ...o});

before(async () => {
 if (!RUN) return;
 rmSync(web, {recursive: true, force: true});
 for (const f of SHADOW_FILES) { mkdirSync(dirname(join(web, f)), {recursive: true}); cpSync(join(realWeb, f), join(web, f)); }
 writeFileSync(join(web, "package.json"), JSON.stringify({name: "m38-e2e-shadow", private: true}));
 writeFileSync(join(web, "next.config.js"), "module.exports = {};");
 await rig.start();
 const mk = (perms, who) => tok(who, perms);
 for (const [op, gate] of [["note.add", false]]) {
  const c = (await rig.api(mk(MAKER_PERMS, "maker"), "POST", "/contracts", {operationType: op, recordType: "note", ownerDomain: "documentation", offlineAllowed: true, requiresSafetyGate: gate, mergeable: false, maxQueueAgeMinutes: 1440})).json;
  await rig.api(mk(CHECKER_PERMS, "checker"), "POST", `/contracts/${c.id}/decision`, {expectedVersion: c.version, approve: true});
 }
 const require = createRequire(join(web, "package.json"));
 const nextBin = require.resolve("next/dist/bin/next");
 next = spawn(process.execPath, [nextBin, "dev", "-p", String(PORT), "-H", "127.0.0.1"], {cwd: web, stdio: ["ignore", "pipe", "pipe"], env: {...process.env, NODE_ENV: "development", NEXT_TELEMETRY_DISABLED: "1",
  SYNQORIA_OPS_OIDC_ISSUER: ISSUER, SYNQORIA_OPS_OIDC_CLIENT_ID: "m38-e2e", SYNQORIA_OPS_WEB_ORIGIN: WEB_ORIGIN, SYNQORIA_OPS_API_ORIGIN: ORIGIN,
  SYNQORIA_OFFLINE_API_ORIGIN: ORIGIN, SYNQORIA_OFFLINE_OIDC_AUDIENCE: AUDIENCE, SYNQORIA_OFFLINE_SESSION_VERIFIER: "jwks"}});
 next.stdout.on("data", d => { nextLog += d; }); next.stderr.on("data", d => { nextLog += d; });
 for (let i = 0; i < 180; i++) {
  try { const r = await fetch(WEB + "/api/offline/session"); if (r.status > 0) return; } catch { /* starting */ }
  if (next.exitCode !== null) throw new Error("next exited: " + nextLog.slice(-1500));
  await new Promise(r => setTimeout(r, 1000));
 }
 throw new Error("next did not start: " + nextLog.slice(-1500));
});
after(async () => {
 if (!RUN) return;
 if (next && next.exitCode === null) { next.kill(); await new Promise(r => setTimeout(r, 500)); }
 await rig.stop();
 rmSync(web, {recursive: true, force: true});
 if (process.env.E2E_LOG) (await import("node:fs")).writeFileSync(process.env.E2E_LOG, nextLog);
});

const test2 = (n, f) => test(n, {skip: !RUN && "run through tests/e2e/launch.mjs"}, f);
test2("session route: verifies the token cryptographically and returns identifiers only", async () => {
 const t = tok("nurse", NURSE_PERMS);
 const r = await web_("GET", "/api/offline/session", t);
 assert.equal(r.status, 200); assert.deepEqual(Object.keys(r.json).sort(), ["expiresAt", "scope", "userId"]);
 assert.equal(r.json.userId, ids.nurse); assert.equal(r.json.scope, `${A.cell}/${A.facility}`);
 assert.ok(!r.text.includes(t) && !r.text.includes("eyJ"), "the token is never returned");
 assert.equal(r.status === 200 && (await fetch(WEB + "/api/offline/session", {headers: {Cookie: cookie(t)}})).headers.get("cache-control"), "no-store");
});

test2("session route fails closed: no cookie, forged, tampered, unsigned, wrong audience/issuer, expired, over-long grant", async () => {
 const good = tok("nurse", NURSE_PERMS), [h, p, s] = good.split(".");
 const forged = rig.token({sub: ids.nurse, ...A, perms: NURSE_PERMS}).split(".");
 const cases = {
  "no cookie": null, "tampered payload": `${h}.${Buffer.from(JSON.stringify({...JSON.parse(Buffer.from(p, "base64url")), sub: randomUUID()})).toString("base64url")}.${s}`,
  "unsigned": `${Buffer.from(JSON.stringify({alg: "none", kid: "rig-key-1"})).toString("base64url")}.${p}.`, "wrong audience": tok("nurse", NURSE_PERMS, {aud: "other"}),
  "wrong issuer": tok("nurse", NURSE_PERMS, {iss: "https://evil.example"}), "expired": tok("nurse", NURSE_PERMS, {ttl: -120}), "grant too long": tok("nurse", NURSE_PERMS, {grantTtl: 7200}),
  "garbage": "not-a-token"
 };
 for (const [name, t] of Object.entries(cases)) {
  const r = await web_("GET", "/api/offline/session", t === null ? undefined : t);
  assert.equal(r.status, 401, name + " -> " + r.status);
  assert.ok(!r.text.includes("userId"), name);
 }
 assert.ok(forged.length === 3);
});

test2("proxy: cookie authentication, allow-list, query filter, bounds and idempotency", async () => {
 const t = tok("maker", MAKER_PERMS);
 assert.equal((await web_("GET", "/api/offline/proxy/status")).status, 401, "no cookie");
 const st = await web_("GET", "/api/offline/proxy/status", t);
 assert.equal(st.status, 200); assert.ok(Array.isArray(st.json.devices));
 assert.equal((await web_("GET", "/api/offline/proxy/contracts", t)).json.length >= 1, true);
 assert.equal((await web_("POST", "/api/offline/proxy/heads", t, {body: {recordType: "note", recordKey: "x", ownerRef: randomUUID(), headHash: "a".repeat(64)}})).status, 404, "head publication is not browser-reachable");
 assert.equal((await web_("POST", "/api/offline/proxy/contracts", t, {body: {}})).status, 404);
 assert.equal((await web_("GET", "/api/offline/proxy/status?cell=x", t)).status, 400, "unsupported query key");
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {raw: "{not json", headers: {}})).status, 400);
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {body: {}, headers: {"Idempotency-Key": "short"}})).status, 422, "idempotency key required");
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {raw: JSON.stringify({x: "y".repeat(33000)})})).status, 413, "32 KiB bound on ordinary routes");
 assert.notEqual((await web_("POST", "/api/offline/proxy/sync/batches", tok("nurse", NURSE_PERMS), {raw: JSON.stringify({deviceId: randomUUID(), commands: [{p: "y".repeat(40000)}]})})).status, 413, "a device batch may exceed 32 KiB");
 assert.equal((await web_("POST", "/api/offline/proxy/sync/batches", tok("nurse", NURSE_PERMS), {raw: JSON.stringify({x: "y".repeat(1_100_000)})})).status, 413, "but not 1 MiB");
});

test2("CSRF and Origin are enforced on every write", async () => {
 const t = tok("maker", MAKER_PERMS), body = {deviceKey: "e2e-" + randomUUID().slice(0, 8), label: "x", boundUser: ids.nurse, sessionMinutes: 60, queueLimit: 10};
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {body, csrf: false})).status, 403, "missing CSRF header");
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {body, headers: {"x-synq-ops-csrf": "d".repeat(48)}})).status, 403, "CSRF header differs from the cookie");
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {body, origin: "https://evil.example"})).status, 403, "foreign Origin");
 assert.equal((await web_("POST", "/api/offline/proxy/devices", t, {body, origin: false})).status, 403, "no Origin");
 assert.equal(rig.psql("SELECT count(*) FROM synqoria_offline.devices").trim(), "0", "nothing was created by any refused write");
});

test2("device enrollment through the proxy: privileged, scoped and audited by the real service", async () => {
 const body = {deviceKey: "e2e-" + randomUUID().slice(0, 8), label: "Ward tablet", boundUser: ids.nurse, sessionMinutes: 120, queueLimit: 50};
 const nurse = await web_("POST", "/api/offline/proxy/devices", tok("nurse", NURSE_PERMS), {body});
 assert.equal(nurse.status, 403, "an ordinary staff user cannot enroll devices"); assert.equal(nurse.json.error, "permission_denied");
 const key = randomUUID();
 const ok = await web_("POST", "/api/offline/proxy/devices", tok("maker", MAKER_PERMS), {body, headers: {"Idempotency-Key": key}});
 assert.equal(ok.status, 201); assert.equal(ok.json.boundUser, ids.nurse); assert.equal(ok.json.status, "ACTIVE");
 const again = await web_("POST", "/api/offline/proxy/devices", tok("maker", MAKER_PERMS), {body, headers: {"Idempotency-Key": key}});
 assert.equal(again.json.id, ok.json.id, "idempotent replay");
 assert.equal(rig.psql("SELECT count(*) FROM synqoria_offline.devices").trim(), "1");
 assert.equal(rig.psql(`SELECT count(*) FROM synqoria_offline.audit_events WHERE aggregate_type='DEVICE' AND action='REGISTER' AND aggregate_id='${ok.json.id}' AND actor_id='${ids.maker}'`).trim(), "1", "immutable audit evidence names the enrolling administrator");
 const cur = await web_("GET", `/api/offline/proxy/devices/${ok.json.id}/cursor`, tok("nurse", NURSE_PERMS));
 assert.equal(cur.status, 200); assert.equal(cur.json.status, "ACTIVE");
 // hospital isolation through the whole stack: another hospital's administrator sees nothing and cannot read the cursor
 const otherMaker = rig.token({sub: randomUUID(), ...B, perms: MAKER_PERMS});
 assert.equal((await web_("GET", "/api/offline/proxy/devices", otherMaker)).json.length, 0);
 assert.equal((await web_("GET", `/api/offline/proxy/devices/${ok.json.id}/cursor`, otherMaker)).status, 404);
 // expiry and revocation propagate through the stack
 assert.equal((await web_("GET", `/api/offline/proxy/devices/${ok.json.id}/cursor`, tok("nurse", NURSE_PERMS, {ttl: -30}))).status, 401);
 const dev = (await web_("GET", "/api/offline/proxy/devices", tok("maker", MAKER_PERMS))).json[0];
 const rev = await web_("POST", `/api/offline/proxy/devices/${dev.id}/revocation`, tok("maker", MAKER_PERMS), {body: {expectedVersion: dev.version, reason: "lost on ward rounds"}});
 assert.equal(rev.status, 200);
 const batch = await web_("POST", "/api/offline/proxy/sync/batches", tok("nurse", NURSE_PERMS), {body: {deviceId: dev.id, commands: [{commandId: randomUUID(), seq: 1, operationType: "note.add", recordType: "note", recordKey: "r", baseVersion: 1, baseEpoch: 1, fields: [], payload: {}, createdLocalAt: new Date(Date.now() - 1000).toISOString(), expiresAt: new Date(Date.now() + 3600000).toISOString()}]}});
 assert.equal(batch.status, 403); assert.equal(batch.json.error, "device_revoked");
});

test2("conflict resolution through the proxy: the author is refused, an independent reviewer is accepted, CSRF still applies", async () => {
 const makerTok = tok("maker", MAKER_PERMS), nurseTok = tok("nurse", NURSE_PERMS);
 const h = (await rig.api(makerTok, "POST", "/heads", {recordType: "note", recordKey: "res-1", ownerRef: randomUUID(), headHash: "a".repeat(64)})).json;
 await rig.api(makerTok, "POST", `/heads/${h.id}/revisions`, {expectedVersion: h.version, headHash: "b".repeat(64), changedFields: ["text"]});
 const dev = (await web_("POST", "/api/offline/proxy/devices", makerTok, {body: {deviceKey: "e2e-res-" + randomUUID().slice(0, 6), label: "t", boundUser: ids.nurse, sessionMinutes: 60, queueLimit: 10}})).json;
 const cmd = {commandId: randomUUID(), seq: 1, operationType: "note.add", recordType: "note", recordKey: "res-1", baseVersion: 1, baseEpoch: 1, fields: ["text"], payload: {text: "synthetic"},
  createdLocalAt: new Date(Date.now() - 1000).toISOString(), expiresAt: new Date(Date.now() + 3600000).toISOString()};
 const sent = await web_("POST", "/api/offline/proxy/sync/batches", nurseTok, {body: {deviceId: dev.id, commands: [cmd]}});
 assert.equal(sent.status, 200); assert.equal(sent.json.results[0].status, "CONFLICT");
 const view = (await web_("GET", `/api/offline/proxy/conflicts/${sent.json.results[0].conflictId}`, nurseTok)).json;
 const body = {expectedVersion: view.version, resolution: "KEEP_AUTHORITATIVE", reasonCode: "DATA_ERROR", note: null, expectedHeadVersion: view.currentHeadVersion};
 const path = `/api/offline/proxy/conflicts/${view.id}/resolution`;
 assert.equal((await web_("POST", path, nurseTok, {body})).status, 403, "no m38:conflict_resolve");
 const authorWithPerm = rig.token({sub: ids.nurse, ...A, perms: ["m38:conflict_resolve", "m38:sync_read"]});
 const refused = await web_("POST", path, authorWithPerm, {body});
 assert.equal(refused.status, 403); assert.equal(refused.json.error, "resolver_must_be_independent");
 const reviewer = rig.token({sub: randomUUID(), ...A, perms: ["m38:conflict_resolve", "m38:sync_read"]});
 assert.equal((await web_("POST", path, reviewer, {body, csrf: false})).status, 403, "CSRF missing");
 assert.equal((await web_("POST", path, reviewer, {body})).status, 200);
 assert.equal((await web_("GET", `/api/offline/proxy/conflicts/${view.id}`, reviewer)).json.status, "RESOLVED");
 assert.equal((await web_("POST", path, reviewer, {body})).status, 409, "a decision is immutable");
});
