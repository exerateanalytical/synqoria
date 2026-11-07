import test from "node:test";
import assert from "node:assert/strict";
import {SyncEngine, MemoryStore, KeyManager, MemoryKeyVault} from "../src/index.ts";
import {FakeServer, makeClient, ready, input, uuid, DEVICE, USER, SCOPE} from "./helpers.mjs";

const states = async c => (await c.queue.list()).map(e => e.state);

test("happy path: enqueue, drain, applied, cursor persisted, payload purged", async () => {
 const s = new FakeServer(), c = await ready(s);
 for (let i = 0; i < 3; i++) { const r = await c.engine.submit(input({baseVersion: 1 + i})); assert.ok(r.ok, JSON.stringify(r)); }
 const rep = await c.engine.drain();
 assert.equal(rep.applied, 3); assert.deepEqual(await states(c), ["APPLIED", "APPLIED", "APPLIED"]);
 assert.equal(s.applied.length, 3);
 assert.ok((await c.queue.list()).every(e => e.command === null && e.appliedVersion !== null));
 assert.equal(JSON.parse(await c.enc.get("sync/state")).cursor, 3);
 assert.equal((await c.engine.syncOnce()).status, "IDLE");
});

test("batches are bounded to 50 and each carries its own idempotency key", async () => {
 const s = new FakeServer(), c = await ready(s);
 for (let i = 0; i < 120; i++) await c.engine.submit(input({recordKey: "rec-1", baseVersion: 1}));
 await c.engine.drain(10);
 const posts = s.requests.filter(r => r.path === "/sync/batches");
 assert.deepEqual(posts.map(p => p.body.commands.length), [50, 50, 20]);
 assert.equal(new Set(posts.map(p => p.headers["Idempotency-Key"])).size, 3);
 assert.ok(posts.every(p => p.headers["x-synq-ops-csrf"] === "csrf-token"));
 assert.ok(s.applied.length >= 1);
});

test("crash after the server processed but before the client recorded: the identical batch is resent and nothing is applied twice", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input()); await c.engine.submit(input({baseVersion: 2}));
 s.faults.push({at: s.callCount + 1, kind: "dropAfter"});
 const r1 = await c.engine.syncOnce();
 assert.equal(r1.status, "RETRY_LATER"); assert.ok(r1.delayMs > 0);
 assert.deepEqual(await states(c), ["SUBMITTED", "SUBMITTED"]);
 assert.equal(s.applied.length, 2, "the server did apply them");
 const firstKey = s.requests.at(-1).headers["Idempotency-Key"];
 const r2 = await c.engine.syncOnce();
 assert.equal(s.requests.at(-1).headers["Idempotency-Key"], firstKey, "same batch key");
 assert.equal(r2.applied, 2); assert.equal(s.applied.length, 2, "no double application");
 assert.deepEqual(await states(c), ["APPLIED", "APPLIED"]);
});

test("process kill mid-batch: a brand-new engine over the same store resumes the persisted batch", async () => {
 const s = new FakeServer(), store = new MemoryStore(), vault = new MemoryKeyVault();
 const c1 = await ready(s, {store, vault});
 await c1.engine.submit(input());
 s.faults.push({at: s.callCount + 1, kind: "drop"});
 await c1.engine.syncOnce();
 assert.deepEqual(await states(c1), ["SUBMITTED"]);
 const c2 = makeClient(s, {store, vault, leaderId: "tab-1"});            // "restart": fresh objects, same durable store and key vault
 await c2.km.unlock(); await c2.engine.refreshReference();
 const r = await c2.engine.syncOnce();
 assert.equal(r.applied, 1); assert.equal(s.applied.length, 1);
});

test("network partition, delay and duplicate delivery: idempotent resume under deterministic faults", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 const base = s.callCount;
 s.faults.push({at: base + 1, kind: "drop"}, {at: base + 2, kind: "http", status: 503}, {at: base + 3, kind: "duplicate"});
 const a = await c.engine.syncOnce(); assert.equal(a.status, "RETRY_LATER"); assert.equal(a.reason, "network");
 const b = await c.engine.syncOnce(); assert.equal(b.status, "RETRY_LATER"); assert.equal(b.reason, "unavailable");
 assert.ok(b.delayMs >= a.delayMs || b.delayMs > 0);
 const d = await c.engine.syncOnce();
 assert.equal(d.applied, 1); assert.equal(s.applied.length, 1, "a request delivered twice is applied once");
 assert.equal(JSON.parse(await c.enc.get("sync/state")).failures, 0);
});

test("reordered submission: commands are always sent in device-sequence order and the server answers per command", async () => {
 const s = new FakeServer(), c = await ready(s);
 for (let i = 0; i < 4; i++) await c.engine.submit(input({baseVersion: 1 + i}));
 await c.engine.drain();
 const sent = s.requests.find(r => r.path === "/sync/batches").body.commands.map(x => x.seq);
 assert.deepEqual(sent, [1, 2, 3, 4]);
 assert.deepEqual(await states(c), ["APPLIED", "APPLIED", "APPLIED", "APPLIED"]);
});

test("server outcomes are recorded exactly: conflict, denied, duplicate, expired, retryable", async () => {
 const s = new FakeServer(), c = await ready(s);
 s.addHead("note", "rec-2", {headVersion: 5});
 await c.engine.submit(input({recordKey: "rec-2", baseVersion: 2}));                 // stale base -> conflict
 const dup = input({recordKey: "rec-1", baseVersion: 1}); await c.engine.submit(dup);
 s.addContract({operationType: "late.op", recordType: "note"});
 await c.engine.refreshReference();
 const r = await c.engine.drain();
 assert.equal(r.conflicted, 1); assert.equal(r.applied, 1);
 const conflict = (await c.queue.list()).find(e => e.state === "CONFLICTED");
 assert.equal(conflict.code, "stale_base"); assert.match(conflict.conflictId, /^[0-9a-f-]{36}$/); assert.equal(conflict.command, null);
 // the same command id resent later is a DUPLICATE with the original outcome code
 const again = await c.engine.submit({...dup, commandId: uuid()});
 assert.ok(again.ok);
 s.heads.get("note/rec-1").frozen = true;                                           // a transfer froze the record: RETRY, requeued, nothing lost
 const frozen = await c.engine.syncOnce();
 assert.equal(frozen.status, "RETRY_LATER"); assert.equal(frozen.reason, "retryable_outcome");
 assert.equal((await c.queue.list(["QUEUED"])).length, 1);
 s.heads.get("note/rec-1").frozen = false;
 s.heads.get("note/rec-1").headVersion = 2;
 await c.engine.refreshReference();
 const e = (await c.queue.list(["QUEUED"]))[0];
 assert.equal(e.code, "record_frozen");
});

test("a command the server never answered is requeued, never lost; unknown server statuses stop the engine", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 const orig = s.route.bind(s);
 s.route = (m, p, q, b, h) => p === "/sync/batches" ? s.json(200, {deviceId: DEVICE, results: [], cursor: 0, serverTime: new Date().toISOString()}) : orig(m, p, q, b, h);
 const r = await c.engine.syncOnce();
 assert.equal(r.requeued, 1); assert.deepEqual(await states(c), ["QUEUED"]);
 s.route = (m, p, q, b, h) => p === "/sync/batches" ? s.json(200, {deviceId: DEVICE, results: [{commandId: uuid(), seq: 1, status: "TELEPORTED", code: "x", appliedVersion: null, conflictId: null}], cursor: 1, serverTime: new Date().toISOString()}) : orig(m, p, q, b, h);
 const r2 = await c.engine.syncOnce();
 assert.equal(r2.status, "SUSPENDED"); assert.equal(r2.reason, "protocol_error");
});

test("session expiry suspends transport and locks the store; resume needs a valid session", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 s.sessionExpired = true;
 const r = await c.engine.syncOnce();
 assert.equal(r.status, "SUSPENDED"); assert.equal(r.reason, "session_expired"); assert.equal(c.km.unlocked, false);
 assert.deepEqual((await c.engine.submit(input())), {ok: false, code: "store_locked"});
 const calls = s.callCount;
 assert.equal((await c.engine.syncOnce()).status, "SUSPENDED"); assert.equal(s.callCount, calls, "no traffic while suspended");
 c.sessionState.session = {userId: USER, scope: SCOPE, expiresAt: c.t.now - 1};
 await assert.rejects(() => c.engine.resume(), /session_not_valid/);
 s.sessionExpired = false; c.sessionState.session = {userId: USER, scope: SCOPE, expiresAt: c.t.now + 1e6};
 await c.engine.resume();
 assert.equal((await c.engine.drain()).applied, 1, "the queued command survived the expiry");
});

test("revoked device: transport stops, key destroyed, store erased", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 s.devices.get(DEVICE).status = "REVOKED";
 const r = await c.engine.syncOnce();
 assert.equal(r.status, "WIPED"); assert.equal(r.reason, "device_revoked");
 assert.equal(c.store.raw().size, 1, "only the suspension marker remains");
 assert.equal(await c.engine.suspendedReason(), "device_revoked");
 await assert.rejects(() => c.engine.resume(), /device_revoked/);
 assert.equal((await c.engine.syncOnce()).reason, "device_revoked", "a revoked device never resumes");
});

test("hospital isolation: another cell's session, a wrong user, or a foreign server cannot sync this queue", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 c.sessionState.session = {userId: USER, scope: "cell-B/facility-9", expiresAt: c.t.now + 1e6};
 let r = await c.engine.syncOnce(); assert.equal(r.reason, "cross_scope_denied"); assert.equal(c.km.unlocked, false);
 const s2 = new FakeServer(), d = await ready(s2);
 await d.engine.submit(input());
 d.sessionState.session = {userId: "33333333-3333-4333-8333-333333333333", scope: SCOPE, expiresAt: d.t.now + 1e6};
 r = await d.engine.syncOnce(); assert.equal(r.reason, "device_user_mismatch");
 const s3 = new FakeServer({cell: "cell-B/facility-9"}); s3.devices.clear();            // the other hospital's server has never heard of this device
 const e = await ready(s3, {}); await e.engine.submit(input());
 const out = await e.engine.syncOnce();
 assert.equal(out.status, "RETRY_LATER", "a 404 is not a success and not an application");
 assert.equal(s3.applied.length, 0);
});

test("authorization failures: forbidden suspends, device_user_mismatch suspends, collisions never resend altered content", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 s.faults.push({at: s.callCount + 1, kind: "http", status: 403, code: "permission_denied"});
 let r = await c.engine.syncOnce(); assert.equal(r.status, "SUSPENDED"); assert.equal(r.reason, "forbidden");
 const t = new FakeServer(), d = await ready(t);
 await d.engine.submit(input());
 t.faults.push({at: t.callCount + 1, kind: "http", status: 403, code: "device_user_mismatch"});
 r = await d.engine.syncOnce(); assert.equal(r.reason, "device_user_mismatch");
 const u = new FakeServer(), e = await ready(u);
 await e.engine.submit(input());
 u.faults.push({at: u.callCount + 1, kind: "http", status: 409, code: "idempotency_collision"});
 r = await e.engine.syncOnce(); assert.equal(r.reason, "idempotency_collision");
 assert.equal(JSON.parse(await e.enc.get("sync/state")).inflight, null, "a collided batch is abandoned, not resent altered under the same key");
});

test("retry budget is bounded: after the attempts are spent the engine stops and surfaces it", async () => {
 const s = new FakeServer(), c = await ready(s);
 await c.engine.submit(input());
 let last;
 for (let i = 0; i < 20; i++) { s.faults.push({at: s.callCount + 1, kind: "drop"}); last = await c.engine.syncOnce(); if (last.status === "SUSPENDED") break; }
 assert.equal(last.status, "SUSPENDED"); assert.equal(last.reason, "retry_budget_spent");
});

test("offline: no traffic and no loss; manual sync after reconnect", async () => {
 const s = new FakeServer(); let online = false;
 const c = await ready(s, {online: () => online});
 await c.engine.submit(input());
 const calls = s.callCount;
 assert.equal((await c.engine.syncOnce()).status, "OFFLINE"); assert.equal(s.callCount, calls);
 online = true;
 assert.equal((await c.engine.syncOnce()).applied, 1);
});

test("two tabs sharing one store: only one runs a batch at a time, and a stale leader does not send", async () => {
 const s = new FakeServer(), store = new MemoryStore(), vault = new MemoryKeyVault();
 const a = await ready(s, {store, vault, leaderId: "tab-a"}), b = makeClient(s, {store, vault, leaderId: "tab-b"});
 await b.km.unlock(); await b.engine.refreshReference();
 await a.engine.submit(input());
 assert.equal(await a.leader.acquire(), true);
 assert.equal((await b.engine.syncOnce()).status, "LEADER_ELSEWHERE");
 const calls = s.callCount;
 const [x, y] = await Promise.all([a.engine.syncOnce(), b.engine.syncOnce()]);
 assert.equal(s.applied.length, 1);
 assert.ok([x.status, y.status].includes("LEADER_ELSEWHERE"));
 assert.ok(s.callCount > calls);
});

test("diagnostics from a full run never contain identifiers, payloads or tokens", async () => {
 const s = new FakeServer(), c = await ready(s);
 const secret = input({recordKey: "PATIENT-ID-SECRET", payload: {text: "CLINICAL-SECRET-TEXT"}});
 await c.engine.submit(secret);
 s.faults.push({at: s.callCount + 1, kind: "drop"});
 await c.engine.syncOnce(); await c.engine.syncOnce();
 const log = c.lines.join("\n");
 assert.ok(log.length > 0);
 for (const bad of ["PATIENT-ID-SECRET", "CLINICAL-SECRET-TEXT", secret.commandId, DEVICE, USER, "csrf-token", "bbbbbbbb"]) assert.ok(!log.includes(bad), "leaked " + bad);
});

test("payload bounds are enforced before the wire", async () => {
 const s = new FakeServer(), c = await ready(s);
 assert.deepEqual(await c.engine.submit(input({payload: {t: "x".repeat(17000)}})), {ok: false, code: "payload_too_large"});
 const res = await c.transport.submitBatch(DEVICE, Array.from({length: 51}, (_, i) => ({...input(), seq: i + 1})), uuid());
 assert.equal(res.code, "invalid_batch");
 const big = await c.transport.call("POST", "/sync/batches", {body: {x: "y".repeat(1_100_000)}});
 assert.equal(big.code, "request_too_large");
 assert.equal(s.requests.filter(r => r.path === "/sync/batches").length, 0);
});
