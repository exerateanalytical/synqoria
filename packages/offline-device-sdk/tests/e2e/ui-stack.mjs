// Disposable UI stack for manual/browser audits: real M38 service (HTTPS) + PostgreSQL + local OIDC issuer + a minimal Next app made of verbatim copies of the
// M38 screens, components, libraries and route handlers. Seeds synthetic hospital data and prints cookies to paste into a browser.
// Run with NODE_EXTRA_CA_CERTS pointing at the rig certificate: node ui-stack.mjs <dir>   (see tests/e2e/launch.mjs for how the certificate is created)
import {spawn} from "node:child_process";
import {createRequire} from "node:module";
import {randomUUID} from "node:crypto";
import {cpSync, mkdirSync, rmSync, writeFileSync} from "node:fs";
import {join, dirname} from "node:path";
import {fileURLToPath} from "node:url";
import {Rig, ISSUER, AUDIENCE, ORIGIN} from "../live/rig.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..", "..", "..");
const realWeb = join(repo, "apps", "web"), web = join(repo, "apps", ".e2e-shadow-ui");
const PORT = Number(process.env.E2E_WEB_PORT ?? 3200);
const rig = new Rig();
const A = {cell: randomUUID(), facility: randomUUID()};
const ids = {maker: randomUUID(), checker: randomUUID(), nurse: randomUUID(), resolver: randomUUID(), transferOp: randomUUID(), transferChecker: randomUUID()};
const ALL = ["m38:device_manage", "m38:sync_submit", "m38:sync_read", "m38:contract_manage", "m38:contract_approve", "m38:head_publish", "m38:conflict_resolve", "m38:transfer_propose", "m38:transfer_operate", "m38:transfer_approve", "m38:history_read"];
const tok = (who, perms) => rig.token({sub: ids[who], ...A, perms, grantTtl: 890, ttl: 890});

rmSync(web, {recursive: true, force: true});
for (const f of ["app/layout.tsx", "app/globals.css", "app/workforce/offline", "app/api/offline", "components/offline", "lib/offline", "lib/operations-auth.ts"]) { mkdirSync(dirname(join(web, f)), {recursive: true}); cpSync(join(realWeb, f), join(web, f), {recursive: true}); }
writeFileSync(join(web, "package.json"), JSON.stringify({name: "m38-ui-shadow", private: true}));
writeFileSync(join(web, "next.config.js"), "module.exports = { reactStrictMode: true };");
await rig.start();

// synthetic data: contracts, heads, devices, a conflict, a transfer
const mk = async (who, perms, m, p, b) => (await rig.api(tok(who, perms), m, p, b)).json;
const maker = ["m38:contract_manage", "m38:device_manage", "m38:head_publish", "m38:sync_read"], checker = ["m38:contract_approve", "m38:sync_read"];
for (const [op, off, gate] of [["note.add", true, false], ["meds.give", false, true]]) {
 const c = await mk("maker", maker, "POST", "/contracts", {operationType: op, recordType: "note", ownerDomain: gate ? "clinical-safety" : "documentation", offlineAllowed: off, requiresSafetyGate: gate, mergeable: false, maxQueueAgeMinutes: 1440});
 await mk("checker", checker, "POST", `/contracts/${c.id}/decision`, {expectedVersion: c.version, approve: true});
}
const h1 = await mk("maker", maker, "POST", "/heads", {recordType: "note", recordKey: "ward-3-bed-12", ownerRef: randomUUID(), headHash: "a".repeat(64)});
const h2 = await mk("maker", maker, "POST", "/heads", {recordType: "note", recordKey: "ward-3-bed-14", ownerRef: randomUUID(), headHash: "b".repeat(64)});
const dev = await mk("maker", maker, "POST", "/devices", {deviceKey: "ward3-tablet-01", label: "Ward 3 tablet", boundUser: ids.nurse, sessionMinutes: 480, queueLimit: 200});
await mk("maker", maker, "POST", "/devices", {deviceKey: "ward3-tablet-02", label: "Ward 3 spare", boundUser: randomUUID(), sessionMinutes: 480, queueLimit: 200});
await mk("maker", maker, "POST", `/heads/${h2.id}/revisions`, {expectedVersion: h2.version, headHash: "c".repeat(64), changedFields: ["text"]});
const cmd = (key, seq, base) => ({commandId: randomUUID(), seq, operationType: "note.add", recordType: "note", recordKey: key, baseVersion: base, baseEpoch: 1, fields: ["text"], payload: {text: "synthetic"}, createdLocalAt: new Date(Date.now() - 60000).toISOString(), expiresAt: new Date(Date.now() + 82800000).toISOString()});
await mk("nurse", ["m38:sync_submit", "m38:sync_read"], "POST", "/sync/batches", {deviceId: dev.id, commands: [cmd("ward-3-bed-12", 1, 1), cmd("ward-3-bed-14", 2, 1)]});
await mk("maker", ["m38:transfer_propose", "m38:sync_read", "m38:head_publish"], "POST", "/transfers", {headId: h1.id, toOwner: randomUUID(), reason: "Patient moved to partner cell"}).catch(() => null);

const require = createRequire(join(web, "package.json"));
const next = spawn(process.execPath, [require.resolve("next/dist/bin/next"), "dev", "-p", String(PORT), "-H", "127.0.0.1"], {cwd: web, stdio: ["ignore", "pipe", "pipe"], env: {...process.env, NEXT_TELEMETRY_DISABLED: "1",
 SYNQORIA_OPS_OIDC_ISSUER: ISSUER, SYNQORIA_OPS_OIDC_CLIENT_ID: "m38-ui", SYNQORIA_OPS_WEB_ORIGIN: `https://localhost:${PORT}`, SYNQORIA_OPS_API_ORIGIN: ORIGIN,
 SYNQORIA_OFFLINE_API_ORIGIN: ORIGIN, SYNQORIA_OFFLINE_OIDC_AUDIENCE: AUDIENCE, SYNQORIA_OPS_OIDC_AUDIENCE: AUDIENCE}});
let log = ""; next.stdout.on("data", d => { log += d; }); next.stderr.on("data", d => { log += d; });
for (let i = 0; i < 180; i++) { try { await fetch(`http://127.0.0.1:${PORT}/api/operations/auth/verified-session`); break; } catch { await new Promise(r => setTimeout(r, 1000)); } }
const csrf = "c".repeat(48);
const out = {url: `http://localhost:${PORT}/workforce/offline/status`, maker: tok("maker", ALL), nurseToken: tok("nurse", ["m38:sync_submit", "m38:sync_read"]), nurse: ids.nurse, deviceId: dev.id, csrf};
writeFileSync(process.env.UI_STACK_INFO ?? join(rig.dir, "ui-stack.json"), JSON.stringify(out));
console.log("READY " + JSON.stringify({url: out.url, deviceId: dev.id}));
const stop = async () => { next.kill(); await rig.stop(); rmSync(web, {recursive: true, force: true}); process.exit(0); };
process.on("SIGINT", stop); process.on("SIGTERM", stop);
setInterval(() => {}, 1 << 30);
