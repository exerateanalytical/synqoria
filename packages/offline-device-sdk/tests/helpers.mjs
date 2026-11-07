// Deterministic in-memory stand-in for the M38 server, mirroring the contract semantics the SDK depends on:
// per-batch-key idempotency (same key + same body -> stored reply; different body -> 409 idempotency_collision), per-device command ids
// (duplicate -> DUPLICATE, altered replay -> replay_mismatch), epoch/version checks, safety-gate refusal, device revocation and session expiry.
// It is NOT the real service: the real-service exercise is tracked separately (see handoff, package 24).
import {createHash} from "node:crypto";
import {KeyManager, MemoryKeyVault, EncryptedStore, MemoryStore, DurableQueue, ContractCache, Transport, SyncEngine, LeaderElector, Diagnostics} from "../src/index.ts";

export const DEVICE = "11111111-1111-4111-8111-111111111111";
export const USER = "22222222-2222-4222-8222-222222222222";
export const SCOPE = "cell-A/facility-1";
let n = 0;
export const uuid = () => { n++; return `aaaaaaaa-0000-4000-8000-${String(n).padStart(12, "0")}`; };

export class FakeServer {
 constructor(opts = {}) {
  this.cell = opts.cell ?? SCOPE;
  this.heads = new Map(); this.contracts = [];
  this.devices = new Map([[DEVICE, {status: "ACTIVE", boundUser: USER, lastSeq: 0}]]);
  this.batches = new Map(); this.commands = new Map(); this.applied = []; this.requests = [];
  this.sessionExpired = false; this.faults = []; this.callCount = 0;
 }
 addContract(c) { this.contracts.push({status: "APPROVED", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, ...c}); }
 addHead(recordType, recordKey, o = {}) { this.heads.set(recordType + "/" + recordKey, {recordType, recordKey, ownershipEpoch: 1, headVersion: 1, frozen: false, ...o}); }
 json(status, body) { return {ok: status < 300, status, json: async () => body}; }
 /** faults: array of {at: callNumber, kind: "drop" | "dropAfter" | "duplicate" | "http", status?} consumed in order */
 async fetch(url, init) {
  this.callCount++;
  const fault = this.faults.find(f => f.at === this.callCount);
  if (fault?.kind === "drop") throw new TypeError("network_down");
  const u = new URL(url, "http://x");
  const path = u.pathname.replace("/api/offline/proxy", "");
  const body = init.body ? JSON.parse(init.body) : null;
  this.requests.push({method: init.method, path, headers: init.headers, body});
  let res = this.route(init.method, path, u.searchParams, body, init.headers);
  if (fault?.kind === "duplicate") res = this.route(init.method, path, u.searchParams, body, init.headers);     // the same request delivered twice
  if (fault?.kind === "http") return this.json(fault.status, {error: fault.code ?? "unavailable"});
  if (fault?.kind === "dropAfter") throw new TypeError("connection_reset_after_processing");                     // server processed, client never saw the reply
  return res;
 }
 route(method, path, q, body, headers) {
  if (this.sessionExpired) return this.json(401, {error: "grant_expired"});
  if (method === "GET" && path === "/contracts") return this.json(200, this.contracts);
  if (method === "GET" && path === "/heads") return this.json(200, {items: [...this.heads.values()], page: 0, size: 100, total: this.heads.size});
  let m;
  if (method === "GET" && (m = path.match(/^\/devices\/([^/]+)\/cursor$/))) {
   const d = this.devices.get(m[1]); if (!d) return this.json(404, {error: "not_found"});
   return this.json(200, {deviceId: m[1], cursor: d.lastSeq, status: d.status});
  }
  if (method === "POST" && path === "/sync/batches") return this.batch(body, headers["Idempotency-Key"]);
  return this.json(404, {error: "not_found"});
 }
 batch(body, key) {
  const hash = createHash("sha256").update(JSON.stringify(body)).digest("hex");
  const prior = this.batches.get(key);
  if (prior) return prior.hash === hash ? prior.reply : this.json(409, {error: "idempotency_collision"});
  const dev = this.devices.get(body.deviceId);
  if (!dev) return this.json(404, {error: "not_found"});
  if (dev.status === "REVOKED") return this.json(403, {error: "device_revoked"});
  if (!Array.isArray(body.commands) || body.commands.length < 1 || body.commands.length > 50) return this.json(422, {error: "invalid_batch"});
  const results = []; let stop = false;
  for (const c of [...body.commands].sort((a, b) => a.seq - b.seq)) {
   if (stop) { results.push({commandId: c.commandId, seq: c.seq, status: "NOT_PROCESSED", code: "batch_stopped", appliedVersion: null, conflictId: null}); continue; }
   const known = this.commands.get(c.commandId);
   const h = createHash("sha256").update(JSON.stringify([c.operationType, c.recordKey, c.baseVersion, c.baseEpoch, c.payload])).digest("hex");
   if (known) { results.push(known.h === h ? {commandId: c.commandId, seq: c.seq, status: "DUPLICATE", code: known.code, appliedVersion: known.appliedVersion, conflictId: null, originalStatus: known.status}
      : {commandId: c.commandId, seq: c.seq, status: "REJECTED_UNSTORED", code: "replay_mismatch", appliedVersion: null, conflictId: null}); continue; }
   const k = this.contracts.find(x => x.operationType === c.operationType);
   const head = this.heads.get(c.recordType + "/" + c.recordKey);
   const out = (status, code, extra = {}) => ({commandId: c.commandId, seq: c.seq, status, code, appliedVersion: null, conflictId: null, ...extra});
   let r;
   if (!k || k.status !== "APPROVED") r = out("REJECTED", "no_offline_contract");
   else if (k.requiresSafetyGate) r = out("REJECTED", "safety_gate_required");
   else if (!k.offlineAllowed) r = out("REJECTED", "operation_not_offline");
   else if (!head) r = out("REJECTED", "unknown_record");
   else if (c.baseEpoch < head.ownershipEpoch) r = out("CONFLICT", "stale_writer_fenced", {conflictId: uuid()});
   else if (head.frozen) { results.push(out("RETRY", "record_frozen")); stop = true; continue; }
   else if (c.baseVersion > head.headVersion) r = out("REJECTED", "invalid_base");
   else if (c.baseVersion < head.headVersion) r = out("CONFLICT", "stale_base", {conflictId: uuid()});
   else { head.headVersion++; r = out("APPLIED", "applied", {appliedVersion: head.headVersion}); this.applied.push(c.commandId); }
   this.commands.set(c.commandId, {h, status: r.status, code: r.code, appliedVersion: r.appliedVersion});
   results.push(r);
  }
  dev.lastSeq = Math.max(dev.lastSeq, ...body.commands.map(c => c.seq));
  const reply = this.json(200, {deviceId: body.deviceId, results, cursor: dev.lastSeq, serverTime: new Date().toISOString()});
  this.batches.set(key, {hash, reply});
  return reply;
 }
}

export function makeClient(server, o = {}) {
 const store = o.store ?? new MemoryStore();
 const km = o.km ?? new KeyManager(o.vault ?? new MemoryKeyVault());
 const enc = new EncryptedStore(store, km);
 const t = {now: o.now ?? Date.parse("2026-11-07T10:00:00Z")};
 const clock = () => t.now;
 const queue = new DurableQueue(enc, clock, o.queuePolicy);
 const cache = new ContractCache();
 const binding = o.binding ?? {deviceId: DEVICE, userId: USER, scope: SCOPE};
 const sessionState = {session: o.session ?? {userId: USER, scope: SCOPE, expiresAt: t.now + 3600_000}};
 const transport = new Transport({fetch: (u, i) => server.fetch(u, i), csrf: () => "csrf-token"});
 const lines = [];
 const diag = new Diagnostics(l => lines.push(l));
 const leader = new LeaderElector(store, clock, o.leaderId ?? "tab-1", o.leaseMs);
 let keyN = 0;
 const engine = new SyncEngine({store: enc, plain: store, km, queue, transport, cache, binding, session: () => sessionState.session, clock, rng: () => 0.5, diag, leader,
  online: o.online, newBatchKey: () => `bbbbbbbb-0000-4000-8000-${String(++keyN).padStart(12, "0")}` });
 return {store, enc, km, t, clock, queue, cache, binding, sessionState, transport, diag, lines, leader, engine};
}
export const input = (o = {}) => ({commandId: uuid(), operationType: "note.add", recordType: "note", recordKey: "rec-1", baseVersion: 1, baseEpoch: 1, fields: ["text"],
 payload: {text: "synthetic text"}, createdLocalAt: "2026-11-07T09:59:00.000Z", expiresAt: "2026-11-08T09:59:00.000Z", ...o});
export async function ready(server, o = {}) {
 server.addContract({operationType: "note.add", recordType: "note"});
 server.addHead("note", "rec-1");
 const c = makeClient(server, o);
 await c.km.unlock();
 await c.engine.refreshReference();
 return c;
}
