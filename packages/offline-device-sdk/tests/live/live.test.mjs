// LIVE end-to-end exercise: SDK -> real HTTP -> real M38 Spring service (JWT validated against a real OIDC issuer) -> real PostgreSQL 18 with FORCE RLS.
// Synthetic data only. Opt-in: SYNQORIA_LIVE_RIG=1 plus RIG_PG_CONTAINER, RIG_PG_DB, RIG_JDBC_URL (see .github/workflows/m38-device-client-ci.yml).
import test, {before, after} from "node:test";
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {verifyBinding, KeyManager, MemoryKeyVault, EncryptedStore, MemoryStore, DurableQueue, ContractCache, Transport, SyncEngine, LeaderElector, Diagnostics, buildResolution} from "../../src/index.ts";
import {Rig, BASE} from "./rig.mjs";

const live = process.env.SYNQORIA_LIVE_RIG === "1";
const rig = new Rig();
const A = {cell: randomUUID(), facility: randomUUID()}, B = {cell: randomUUID(), facility: randomUUID()};
const scopeA = `${A.cell}/${A.facility}`;
const PERMS = {
 maker: ["m38:contract_manage", "m38:device_manage", "m38:head_publish", "m38:sync_read", "m38:history_read"],
 checker: ["m38:contract_approve", "m38:sync_read"],
 nurse: ["m38:sync_submit", "m38:sync_read"],
 resolver: ["m38:conflict_resolve", "m38:sync_read"]
};
const ids = {maker: randomUUID(), checker: randomUUID(), nurse: randomUUID(), nurse2: randomUUID(), resolver: randomUUID(), otherNurse: randomUUID()};
const tok = (who, perms, o = {}) => rig.token({sub: ids[who], ...A, perms, ...o});
const maker = () => tok("maker", PERMS.maker), checker = () => tok("checker", PERMS.checker), resolver = () => tok("resolver", PERMS.resolver);
let contractsReady = false;
const state = {devices: {}};

async function ok(p, status = [200, 201]) { const r = await p; assert.ok(status.includes(r.status), `${r.status} ${JSON.stringify(r.json)}`); return r.json; }
async function setupHospital() {
 if (contractsReady) return;
 for (const [op, offline, gate] of [["note.add", true, false], ["meds.give", false, true]]) {
  const c = await ok(rig.api(maker(), "POST", "/contracts", {operationType: op, recordType: "note", ownerDomain: gate ? "clinical-safety" : "documentation", offlineAllowed: offline, requiresSafetyGate: gate, mergeable: false, maxQueueAgeMinutes: 1440}));
  await ok(rig.api(checker(), "POST", `/contracts/${c.id}/decision`, {expectedVersion: c.version, approve: true}));
 }
 contractsReady = true;
}
async function head(key) { return ok(rig.api(maker(), "POST", "/heads", {recordType: "note", recordKey: key, ownerRef: randomUUID(), headHash: "a".repeat(64)})); }
async function device(who) {
 const d = await ok(rig.api(maker(), "POST", "/devices", {deviceKey: "live-" + randomUUID().slice(0, 8), label: "Live tablet", boundUser: ids[who], sessionMinutes: 120, queueLimit: 100}));
 return d;
}
function client(who, dev, opts = {}) {
 const getToken = opts.token ?? (() => tok(who, PERMS.nurse));
 const km = new KeyManager(new MemoryKeyVault()), plain = opts.store ?? new MemoryStore(), enc = new EncryptedStore(plain, km);
 const clock = () => Date.now(), queue = new DurableQueue(enc, clock), cache = new ContractCache();
 const lines = [];
 let wrap = opts.wrapFetch;
 const baseFetch = rig.fetchAs(getToken);
 const transport = new Transport({fetch: (u, i) => (wrap ? wrap(baseFetch, u, i) : baseFetch(u, i)), basePath: BASE, csrf: () => "n/a"});
 const binding = {deviceId: dev.id, userId: ids[who], scope: opts.scope ?? scopeA};
 const session = {userId: ids[who], scope: opts.scope ?? scopeA, expiresAt: Date.now() + 600_000};
 const engine = new SyncEngine({store: enc, plain, km, queue, transport, cache, binding, session: () => session, clock, rng: () => 0.5, diag: new Diagnostics(l => lines.push(l)), leader: new LeaderElector(plain, clock, "live-" + randomUUID())});
 return {engine, km, queue, plain, enc, transport, lines, session, cache};
}
const cmd = (key, o = {}) => ({commandId: randomUUID(), operationType: "note.add", recordType: "note", recordKey: key, baseVersion: 1, baseEpoch: 1, fields: ["text"], payload: {text: "synthetic"},
 createdLocalAt: new Date(Date.now() - 60_000).toISOString(), expiresAt: new Date(Date.now() + 82_800_000).toISOString(), ...o});
async function ready(who, dev, opts) { const c = client(who, dev, opts); await c.km.unlock(); assert.ok(await c.engine.refreshReference()); return c; }
const headVersion = async key => { const p = await ok(rig.api(maker(), "GET", "/heads?size=100")); return p.items.find(h => h.recordKey === key).headVersion; };

before(async () => { if (live) { await rig.start(); await setupHospital(); } });
after(async () => { if (live) await rig.stop(); });
const T = (name, fn) => test(name, {skip: !live && "set SYNQORIA_LIVE_RIG=1 with the live rig"}, fn);

T("rig sanity: unauthenticated and foreign-issuer tokens are refused by the real service", async () => {
 assert.equal((await fetch(BASE + "/status")).status, 401);
 const bad = rig.token({...A, perms: PERMS.nurse, iss: "https://evil.example"});
 assert.equal((await rig.api(bad, "GET", "/status")).status, 401);
 const wrongAud = rig.token({...A, perms: PERMS.nurse, aud: "someone-else"});
 assert.equal((await rig.api(wrongAud, "GET", "/status")).status, 401);
});

T("end to end: contracts, heads and cursor are read from the real service; a change is applied exactly once", async () => {
 await head("e2e-1"); const dev = await device("nurse");
 const c = await ready("nurse", dev);
 assert.ok(c.cache.contract("note.add") && c.cache.contract("meds.give").requiresSafetyGate);
 const r = await c.engine.submit(cmd("e2e-1")); assert.ok(r.ok, JSON.stringify(r));
 const rep = await c.engine.drain();
 assert.equal(rep.applied, 1);
 assert.equal(await headVersion("e2e-1"), 2);
 const cur = await c.transport.cursor(dev.id);
 assert.ok(cur.ok && cur.data.cursor >= 1 && cur.data.status === "ACTIVE");
 const e = (await c.queue.list())[0];
 assert.equal(e.state, "APPLIED"); assert.equal(e.command, null);
 assert.equal((await ok(rig.api(maker(), "GET", "/reconciliations"))).items.filter(x => x.outcome === "APPLIED").length >= 1, true);
});

T("crash after the real server applied the batch: the identical batch is resent and the record advances only once", async () => {
 await head("crash-1"); const dev = await device("nurse");
 let drop = true;
 const c = await ready("nurse", dev, {wrapFetch: async (f, u, i) => { const res = await f(u, i); if (drop && String(u).endsWith("/sync/batches")) { drop = false; throw new TypeError("connection reset after processing"); } return res; }});
 await c.engine.submit(cmd("crash-1"));
 const first = await c.engine.syncOnce();
 assert.equal(first.status, "RETRY_LATER");
 assert.equal(await headVersion("crash-1"), 2, "the server did apply it");
 const second = await c.engine.syncOnce();
 assert.equal(second.applied, 1);
 assert.equal(await headVersion("crash-1"), 2, "and the replay did not apply it again");
});

T("stale base becomes a conflict; the author cannot resolve it, an independent reviewer can (maker/checker, live)", async () => {
 const h = await head("conf-1"); const dev = await device("nurse");
 await ok(rig.api(maker(), "POST", `/heads/${h.id}/revisions`, {expectedVersion: h.version, headHash: "b".repeat(64), changedFields: ["text"]}));
 const c = await ready("nurse", dev);
 await c.engine.submit(cmd("conf-1", {baseVersion: 1}));
 const rep = await c.engine.drain();
 assert.equal(rep.conflicted, 1);
 const e = (await c.queue.list())[0];
 assert.equal(e.code, "stale_base");
 const view = await ok(rig.api(resolver(), "GET", `/conflicts/${e.conflictId}`));
 const asAuthor = buildResolution({...view, authorId: view.authorId}, "KEEP_AUTHORITATIVE", "DATA_ERROR", null, view.authorId);
 assert.equal(asAuthor.code, "resolver_must_be_independent", "refused locally");
 const body = buildResolution(view, "KEEP_AUTHORITATIVE", "DATA_ERROR", null, ids.resolver).request;
 const authorToken = rig.token({sub: view.authorId, ...A, perms: PERMS.resolver});
 assert.equal((await rig.api(authorToken, "POST", `/conflicts/${e.conflictId}/resolution`, body)).status, 403, "and refused by the server");
 assert.equal((await rig.api(resolver(), "POST", `/conflicts/${e.conflictId}/resolution`, body)).status, 200);
 assert.equal(await headVersion("conf-1"), 2, "the authoritative record was never overwritten");
});

T("safety-gated and unknown operations: refused on the device and, independently, by the real server", async () => {
 await head("gate-1"); const dev = await device("nurse");
 const c = await ready("nurse", dev);
 assert.deepEqual(await c.engine.submit(cmd("gate-1", {operationType: "meds.give"})), {ok: false, code: "safety_gate_required"});
 assert.deepEqual(await c.engine.submit(cmd("gate-1", {operationType: "surgery.plan"})), {ok: false, code: "no_offline_contract"});
 const raw = await ok(rig.api(tok("nurse", PERMS.nurse), "POST", "/sync/batches", {deviceId: dev.id, commands: [{...cmd("gate-1", {operationType: "meds.give"}), seq: 1}]}));
 assert.equal(raw.results[0].status, "REJECTED"); assert.equal(raw.results[0].code, "safety_gate_required");
 assert.equal(await headVersion("gate-1"), 1);
});

T("authorization failures against the real service: expired token, over-long grant, missing permission, wrong user", async () => {
 await head("auth-1"); const dev = await device("nurse");
 const expired = await ready("nurse", dev, {token: () => tok("nurse", PERMS.nurse, {ttl: -30})}).catch(() => null);
 assert.equal(expired, null, "an expired token cannot even load reference data");
 const c1 = client("nurse", dev, {token: () => tok("nurse", PERMS.nurse, {ttl: -30})}); await c1.km.unlock();
 await c1.cache.loadContracts([{operationType: "note.add", recordType: "note", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, status: "APPROVED"}]);
 await c1.engine.submit(cmd("auth-1"));
 let r = await c1.engine.syncOnce();
 assert.equal(r.reason, "session_expired"); assert.equal(c1.km.unlocked, false, "401 suspends and locks");
 const c2 = client("nurse", dev, {token: () => tok("nurse", PERMS.nurse, {grantTtl: 7200})}); await c2.km.unlock();
 c2.cache.loadContracts([{operationType: "note.add", recordType: "note", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, status: "APPROVED"}]);
 await c2.engine.submit(cmd("auth-1")); r = await c2.engine.syncOnce();
 assert.equal(r.reason, "session_expired", "a grant longer than 15 minutes is refused (grant_expired)");
 const c3 = client("nurse", dev, {token: () => tok("nurse", ["m38:sync_read"])}); await c3.km.unlock();
 c3.cache.loadContracts([{operationType: "note.add", recordType: "note", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, status: "APPROVED"}]);
 await c3.engine.submit(cmd("auth-1")); r = await c3.engine.syncOnce();
 assert.equal(r.reason, "forbidden", "missing m38:sync_submit -> 403 -> suspended");
 assert.equal((await rig.api(rig.token({sub: ids.nurse2, ...A, perms: PERMS.nurse}), "POST", "/sync/batches", {deviceId: dev.id, commands: [{...cmd("auth-1"), seq: 1}]})).json.error, "device_user_mismatch");
 assert.equal(await headVersion("auth-1"), 1, "nothing was applied by any refused attempt");
});

T("revoked device: the real service refuses it and the SDK wipes itself", async () => {
 await head("rev-1"); const dev = await device("nurse");
 const c = await ready("nurse", dev);
 await c.engine.submit(cmd("rev-1"));
 const d = (await ok(rig.api(maker(), "GET", "/devices"))).find(x => x.id === dev.id);
 await ok(rig.api(maker(), "POST", `/devices/${dev.id}/revocation`, {expectedVersion: d.version, reason: "lost on ward rounds"}));
 const r = await c.engine.syncOnce();
 assert.equal(r.status, "WIPED"); assert.equal(r.reason, "device_revoked");
 assert.equal(await headVersion("rev-1"), 1);
 await assert.rejects(() => c.engine.resume(), /device_revoked/);
});

T("hospital isolation against the real service and PostgreSQL RLS: another cell sees and affects nothing", async () => {
 await head("iso-1"); const dev = await device("nurse");
 const otherMaker = rig.token({sub: randomUUID(), ...B, perms: PERMS.maker});
 const otherNurseTok = rig.token({sub: ids.nurse, ...B, perms: PERMS.nurse});
 assert.equal((await ok(rig.api(otherMaker, "GET", "/devices"))).length, 0);
 assert.equal((await ok(rig.api(otherMaker, "GET", "/heads?size=100"))).total, 0);
 const r = await rig.api(otherNurseTok, "POST", "/sync/batches", {deviceId: dev.id, commands: [{...cmd("iso-1"), seq: 1}]});
 assert.equal(r.status, 404, "another hospital cannot even see that the device exists");
 assert.equal((await rig.api(otherNurseTok, "GET", `/devices/${dev.id}/cursor`)).status, 404);
 const ca = client("nurse", dev); await ca.km.unlock();
 ca.session.scope = `${B.cell}/${B.facility}`;
 await ca.engine.refreshReference();
 assert.deepEqual(await ca.engine.submit(cmd("iso-1")), {ok: false, code: "cross_scope_denied"}, "a device bound to hospital A refuses a session of hospital B");
 assert.equal(await headVersion("iso-1"), 1);
});

T("two devices racing on the same base: exactly one is applied, the other becomes a conflict", async () => {
 await head("race-1"); const d1 = await device("nurse"), d2 = await device("nurse2");
 const a = await ready("nurse", d1), b = await ready("nurse2", d2, {token: () => tok("nurse2", PERMS.nurse)});
 await a.engine.submit(cmd("race-1")); await b.engine.submit(cmd("race-1"));
 const [x, y] = await Promise.all([a.engine.drain(), b.engine.drain()]);
 assert.equal(x.applied + y.applied, 1); assert.equal(x.conflicted + y.conflicted, 1);
 assert.equal(await headVersion("race-1"), 2);
});

T("bounds against the real service: 51 commands and malformed bodies are refused", async () => {
 await head("bound-1"); const dev = await device("nurse");
 const t = tok("nurse", PERMS.nurse);
 const many = Array.from({length: 51}, (_, i) => ({...cmd("bound-1"), seq: i + 1}));
 const r = await rig.api(t, "POST", "/sync/batches", {deviceId: dev.id, commands: many});
 assert.equal(r.status, 422); assert.equal(r.json.error, "invalid_batch");
 const bad = await fetch(BASE + "/sync/batches", {method: "POST", headers: {Authorization: "Bearer " + t, "Content-Type": "application/json", "Idempotency-Key": randomUUID()}, body: "{not json"});
 assert.equal(bad.status, 400);
 assert.equal((await fetch(BASE + "/sync/batches", {method: "POST", headers: {Authorization: "Bearer " + t, "Content-Type": "application/json"}, body: "{}"})).status, 422, "Idempotency-Key is mandatory");
});

T("binding verification against the real service: only the bound user, only in the right hospital, only while ACTIVE", async () => {
 const dev = await device("nurse");
 const n = client("nurse", dev), sess = {userId: ids.nurse, scope: scopeA, expiresAt: Date.now() + 600_000};
 assert.equal((await verifyBinding(n.transport, dev.id, sess, Date.now())).ok, true);
 const other = client("nurse2", dev, {token: () => tok("nurse2", PERMS.nurse)});
 assert.equal((await verifyBinding(other.transport, dev.id, {...sess, userId: ids.nurse2}, Date.now())).code, "device_user_mismatch");
 const foreign = client("nurse", dev, {token: () => rig.token({sub: ids.nurse, ...B, perms: PERMS.nurse})});
 assert.equal((await verifyBinding(foreign.transport, dev.id, {...sess, scope: `${B.cell}/${B.facility}`}, Date.now())).code, "device_not_found", "another hospital cannot learn the device exists");
 const d = (await ok(rig.api(maker(), "GET", "/devices"))).find(x => x.id === dev.id);
 await ok(rig.api(maker(), "POST", `/devices/${dev.id}/revocation`, {expectedVersion: d.version, reason: "binding test revocation"}));
 assert.equal((await verifyBinding(n.transport, dev.id, sess, Date.now())).code, "device_not_active");
});
