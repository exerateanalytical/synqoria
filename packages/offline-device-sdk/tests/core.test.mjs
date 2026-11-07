import test from "node:test";
import assert from "node:assert/strict";
import {KeyManager, MemoryKeyVault, EncryptedStore, MemoryStore, DurableQueue, EnqueueError, LockedError, ContractCache, admit, nextDelay, DEFAULT_BACKOFF,
 validateCommand, parseBatchResult, sanitize, Diagnostics, redactedExport, buildResolution, applyLockPolicy, hashKey, seal, open, LeaderElector} from "../src/index.ts";
import {input, uuid, DEVICE, USER, SCOPE} from "./helpers.mjs";

const NOW = Date.parse("2026-11-07T10:00:00Z");
const cmd = (o = {}) => ({...input(o), seq: 1});

test("command validation mirrors the server rules", () => {
 assert.deepEqual(validateCommand(cmd()), []);
 assert.ok(validateCommand(cmd({operationType: "X"})).includes("invalid_operation"));
 assert.ok(validateCommand(cmd({fields: ["Bad-Field"]})).includes("invalid_fields"));
 assert.ok(validateCommand(cmd({baseVersion: 0})).includes("invalid_base_version"));
 assert.ok(validateCommand(cmd({payload: {t: "x".repeat(17000)}})).includes("payload_too_large"));
 assert.ok(validateCommand(cmd({expiresAt: "2026-11-07T09:00:00.000Z"})).includes("invalid_times"));
 assert.ok(validateCommand(cmd({commandId: "nope"})).includes("invalid_command_id"));
});

test("batch results are validated strictly and unknown statuses are protocol errors", () => {
 const good = {deviceId: DEVICE, results: [{commandId: uuid(), seq: 1, status: "APPLIED", code: "applied", appliedVersion: 2, conflictId: null}], cursor: 1, serverTime: "2026-11-07T10:00:00Z"};
 assert.ok(parseBatchResult(good, DEVICE, [1]).ok);
 assert.equal(parseBatchResult({...good, deviceId: "x"}, DEVICE, [1]).reason, "device_mismatch");
 assert.equal(parseBatchResult({...good, results: [{...good.results[0], status: "TELEPORTED"}]}, DEVICE, [1]).reason, "unknown_status");
 assert.equal(parseBatchResult({...good, results: [{...good.results[0], seq: 99}]}, DEVICE, [1]).reason, "unexpected_seq");
 assert.equal(parseBatchResult({...good, cursor: -1}, DEVICE, [1]).reason, "bad_cursor");
 assert.equal(parseBatchResult(null, DEVICE, [1]).reason, "not_an_object");
});

test("everything at rest is ciphertext, bound to its slot, tamper-evident, lockable and crypto-shreddable", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 const q = new DurableQueue(enc, () => NOW);
 await q.enqueue(input({payload: {text: "PLAINTEXT-MARKER-12345"}, recordKey: "MRN-SECRET-99"}));
 const raw = [...store.raw().values()].join("|");
 assert.ok(!raw.includes("PLAINTEXT-MARKER") && !raw.includes("MRN-SECRET") && !raw.includes("synthetic"), "no clinical text or identifier in the stored bytes");
 // slot swapping: moving one sealed value to another key must fail to open
 const entries = [...store.raw().entries()];
 await assert.rejects(() => open(km, "q/e/000000000002", entries.find(([k]) => k === "q/e/000000000001")[1]), /decryption_failed/);
 // tampering flips a byte of the ciphertext
 const sealed = await seal(km, "k", "hello");
 const parts = sealed.split("."); const bytes = Buffer.from(parts[2], "base64"); bytes[0] ^= 1;
 await assert.rejects(() => open(km, "k", [parts[0], parts[1], bytes.toString("base64")].join(".")), /decryption_failed/);
 km.lock();
 await assert.rejects(() => q.list(), LockedError);
 await km.unlock();
 assert.equal((await q.list()).length, 1);
 await km.destroy();
 await km.unlock();                                                        // a brand-new key: the old ciphertext is unreadable forever
 await assert.rejects(() => q.list(), /decryption_failed/);
});

test("enqueue is atomic: an injected write failure leaves no partial command and consumes no sequence number", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 const q = new DurableQueue(enc, () => NOW);
 store.failWriteAt = 2;                                                    // the entry is written, the meta write fails
 await assert.rejects(() => q.enqueue(input()), /injected_write_failure/);
 store.failWriteAt = null;
 assert.equal((await q.list()).length, 0);
 const e = await q.enqueue(input());
 assert.equal(e.seq, 1, "no sequence gap after a failed enqueue");
});

test("the device sequence is append-only and never reused, even after retention deletes old entries", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 let now = NOW;
 const q = new DurableQueue(enc, () => now, {maxEntries: 100, maxBytes: 1e6, terminalRetentionMs: 1000, maxTerminalEntries: 100});
 const a = await q.enqueue(input()), b = await q.enqueue(input());
 assert.deepEqual([a.seq, b.seq], [1, 2]);
 await q.transition(1, "APPLIED", {code: "applied", appliedVersion: 2});
 await q.transition(2, "APPLIED", {code: "applied", appliedVersion: 3});
 now += 5000;
 assert.equal(await q.purgeTerminal(), 2);
 assert.equal((await q.enqueue(input())).seq, 3, "sequence continues, never restarts");
 await q.transition(3, "DENIED", {code: "x"});
 assert.equal((await q.get(3)).state, "DENIED");
 await q.transition(3, "QUEUED");
 assert.equal((await q.get(3)).state, "DENIED", "terminal outcomes never regress");
});

test("quotas, duplicate ids, payload purge on terminal state and expiry", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 let now = NOW;
 const q = new DurableQueue(enc, () => now, {maxEntries: 3, maxBytes: 1e6, terminalRetentionMs: 1e9, maxTerminalEntries: 100});
 const dup = input();
 await q.enqueue(dup);
 await assert.rejects(() => q.enqueue(dup), e => e instanceof EnqueueError && e.code === "duplicate_command_id");
 await q.enqueue(input()); await q.enqueue(input());
 await assert.rejects(() => q.enqueue(input()), e => e.code === "queue_entries_quota");
 const small = new DurableQueue(new EncryptedStore(new MemoryStore(), km), () => now, {maxEntries: 100, maxBytes: 100, terminalRetentionMs: 1, maxTerminalEntries: 1});
 await assert.rejects(() => small.enqueue(input()), e => e.code === "queue_bytes_quota");
 await q.transition(1, "APPLIED", {code: "applied"});
 const done = await q.get(1);
 assert.equal(done.command, null, "the clinical payload is gone once the outcome is final");
 assert.equal(done.bytes, 0);
 now = Date.parse("2026-11-09T00:00:00Z");
 assert.equal(await q.expireDue(), 2);
 assert.deepEqual((await q.list()).map(e => e.state), ["APPLIED", "EXPIRED", "EXPIRED"]);
});

const binding = {deviceId: DEVICE, userId: USER, scope: SCOPE};
const session = {userId: USER, scope: SCOPE, expiresAt: NOW + 3600_000};
function cacheWith(contract, head) {
 const c = new ContractCache();
 c.loadContracts([{operationType: "note.add", recordType: "note", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, status: "APPROVED", ...contract}]);
 if (head) c.loadHeads([{recordType: "note", recordKey: "rec-1", ownershipEpoch: 1, headVersion: 1, frozen: false, ...head}]);
 return c;
}
test("admission denies unknown, unapproved, safety-gated, non-offline, stale-epoch, frozen, cross-scope and expired commands", () => {
 const ok = (c, s = session, b = binding) => admit(input(), c, b, s, NOW);
 assert.deepEqual(ok(cacheWith({}, {})), {ok: true});
 assert.equal(admit(input({operationType: "surgery.plan"}), cacheWith({}), binding, session, NOW).code, "no_offline_contract");
 assert.equal(ok(cacheWith({status: "DRAFT"})).code, "no_offline_contract");
 assert.equal(ok(cacheWith({requiresSafetyGate: true, offlineAllowed: false})).code, "safety_gate_required");
 assert.equal(ok(cacheWith({offlineAllowed: false})).code, "operation_not_offline");
 assert.equal(ok(cacheWith({recordType: "other"})).code, "record_type_mismatch");
 assert.equal(ok(cacheWith({}, {ownershipEpoch: 2})).code, "stale_epoch");
 assert.equal(admit(input({baseEpoch: 3}), cacheWith({}, {ownershipEpoch: 2}), binding, session, NOW).code, "invalid_epoch");
 assert.equal(ok(cacheWith({}, {frozen: true})).code, "record_frozen");
 assert.equal(ok(cacheWith({}), {...session, scope: "cell-B/facility-9"}).code, "cross_scope_denied");
 assert.equal(ok(cacheWith({}), {...session, userId: "someone-else"}).code, "device_user_mismatch");
 assert.equal(ok(cacheWith({}), {...session, expiresAt: NOW - 1}).code, "session_expired");
 assert.equal(admit(input({expiresAt: "2026-11-07T09:00:00.000Z"}), cacheWith({}), binding, session, NOW).code, "command_expired");
 assert.equal(admit(input({createdLocalAt: "2026-11-05T00:00:00.000Z", expiresAt: "2026-11-09T00:00:00.000Z"}), cacheWith({maxQueueAgeMinutes: 60}), binding, session, NOW).code, "lifetime_exceeds_contract");
});

test("backoff is bounded, jittered, never zero and ends", () => {
 for (let a = 1; a <= DEFAULT_BACKOFF.maxAttempts; a++) {
  const lo = nextDelay(a, () => 0), hi = nextDelay(a, () => 0.999999);
  assert.ok(lo >= 1 && hi <= DEFAULT_BACKOFF.capMs && hi >= lo, `attempt ${a}`);
 }
 assert.ok(nextDelay(1, () => 0.999999) <= 1000 && nextDelay(3, () => 0.999999) <= 4000);
 assert.equal(nextDelay(DEFAULT_BACKOFF.maxAttempts + 1, () => 0.5), null);
 assert.equal(nextDelay(0, () => 0.5), null);
});

test("diagnostics drop everything that is not an allow-listed, vocabulary-like value", () => {
 const s = sanitize({event: "batch_send", code: "record_frozen", count: 3, patient: "Jane Example", recordKey: "MRN-1", token: "eyJhbGciOi.x.y", payload: {a: 1}, state: "123e4567-e89b-42d3-a456-426614174000",
  kind: "network down", seq: 12, status: "1234567890"});
 assert.deepEqual(s, {event: "batch_send", code: "record_frozen", count: 3, seq: 12});
 const d = new Diagnostics(); d.log({event: "x", token: "secret"});
 assert.ok(!d.recent().join().includes("secret"));
});

test("the staff recovery export contains metadata only", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 const q = new DurableQueue(enc, () => NOW);
 await q.enqueue(input({payload: {text: "CLINICAL-TEXT"}, recordKey: "PATIENT-ID-77"}));
 const out = JSON.stringify(redactedExport(await q.list(), "2026-11-07T10:00:00Z"));
 assert.ok(!out.includes("CLINICAL-TEXT") && !out.includes("PATIENT-ID-77") && !out.includes("commandId") && !out.includes("payload"));
 assert.ok(out.includes(await hashKey("PATIENT-ID-77")) && out.includes("note.add"));
});

test("conflict resolution requests are deterministic and refuse the author as reviewer", () => {
 const c = {id: uuid(), kind: "STALE_BASE", status: "OPEN", authorId: "author-1", version: 4, currentHeadVersion: 7, currentEpoch: 2, authoritativeEpoch: 2};
 const r = buildResolution(c, "KEEP_AUTHORITATIVE", "DATA_ERROR", null, "reviewer-1");
 assert.deepEqual(r, {ok: true, request: {expectedVersion: 4, resolution: "KEEP_AUTHORITATIVE", reasonCode: "DATA_ERROR", note: null, expectedHeadVersion: 7}});
 assert.deepEqual(buildResolution(c, "KEEP_AUTHORITATIVE", "DATA_ERROR", null, "reviewer-1"), r, "same input, same request");
 assert.equal(buildResolution(c, "ACCEPT_LOCAL", "DATA_ERROR", null, "author-1").code, "resolver_must_be_independent");
 assert.equal(buildResolution(c, "ACCEPT_LOCAL", "OTHER", "no", "reviewer-1").code, "reason_required");
 assert.equal(buildResolution(c, "ACCEPT_LOCAL", "MOOD", null, "reviewer-1").code, "reason_required");
 assert.equal(buildResolution({...c, authoritativeEpoch: 1}, "ACCEPT_LOCAL", "DATA_ERROR", null, "reviewer-1").code, "epoch_changed");
 assert.equal(buildResolution({...c, status: "RESOLVED"}, "KEEP_AUTHORITATIVE", "DATA_ERROR", null, "reviewer-1").code, "invalid_transition");
});

test("leader election: one holder, expiry takeover, and a paused holder is no longer leader", async () => {
 const store = new MemoryStore(); let now = NOW;
 const a = new LeaderElector(store, () => now, "tab-a", 1000), b = new LeaderElector(store, () => now, "tab-b", 1000);
 assert.equal(await a.acquire(), true);
 assert.equal(await b.acquire(), false);
 assert.equal(await a.acquire(), true, "renewal");
 now += 1500;
 assert.equal(await a.stillLeader(), false, "an expired lease means the paused tab must not send");
 assert.equal(await b.acquire(), true, "takeover after expiry");
 assert.equal(await a.acquire(), false);
 await b.release();
 assert.equal(await a.acquire(), true);
 const results = await Promise.all(Array.from({length: 8}, (_, i) => new LeaderElector(store, () => now + 5000, "p" + i, 1000).acquire()));
 assert.equal(results.filter(Boolean).length, 1, "eight racing processes, one winner");
});

test("lock policy: LOCK seals, WIPE crypto-shreds and erases", async () => {
 const store = new MemoryStore(), km = new KeyManager(new MemoryKeyVault()), enc = new EncryptedStore(store, km);
 await km.unlock();
 const q = new DurableQueue(enc, () => NOW);
 await q.enqueue(input());
 await applyLockPolicy(km, enc, "LOCK");
 assert.equal(km.unlocked, false); assert.ok(store.raw().size > 0);
 await km.unlock();
 const r = await applyLockPolicy(km, enc, "WIPE");
 assert.equal(r.lost, 1);
 assert.equal(store.raw().size, 0);
 await km.unlock();
 assert.equal((await q.list()).length, 0);
});
