// The sync engine. Idempotent by construction: a batch is persisted (key + sequence numbers) BEFORE it is sent, results are applied per command,
// and the in-flight marker is cleared last. A crash, kill, partition or timeout at any point leads to re-sending the identical batch with the
// identical key, for which the server returns the stored reply instead of applying anything twice.
import { admit, ContractCache } from "./admission.ts";
import type { ContractView, HeadView } from "./admission.ts";
import { nextDelay } from "./backoff.ts";
import { LockedError } from "./crypto.ts";
import type { KeyManager } from "./crypto.ts";
import { Diagnostics } from "./diagnostics.ts";
import { LeaderElector } from "./leader.ts";
import { applyLockPolicy, DEFAULT_DEVICE_POLICY } from "./policy.ts";
import type { DevicePolicy } from "./policy.ts";
import { DurableQueue } from "./queue.ts";
import type { EnqueueInput } from "./queue.ts";
import type { Store } from "./store.ts";
import { Transport } from "./transport.ts";
import { MAX_BATCH } from "./types.ts";
import type { Clock, CommandIn, DeviceBinding, QueueEntry, Session } from "./types.ts";
import { parseBatchResult } from "./validate.ts";

export type SyncStatus = "IDLE" | "PROGRESS" | "RETRY_LATER" | "SUSPENDED" | "LEADER_ELSEWHERE" | "OFFLINE" | "WIPED";
export type SyncReport = { status: SyncStatus; reason?: string; delayMs?: number; sent: number; applied: number; duplicate: number; conflicted: number; denied: number; expired: number; requeued: number };
type State = { cursor: number; inflight: { batchKey: string; seqs: number[] } | null; failures: number; lastProbe?: number };
export const PROBE_INTERVAL_MS = 5 * 60_000;
const STATE = "sync/state", SUSPENDED = "sync/suspended";

export type EngineDeps = {
 store: Store; plain: Store; km: KeyManager; queue: DurableQueue; transport: Transport; cache: ContractCache; binding: DeviceBinding;
 session: () => Session | null; clock: Clock; rng: () => number; diag: Diagnostics; leader: LeaderElector; online?: () => boolean; policy?: DevicePolicy;
 newBatchKey?: () => string;
};
const empty = (status: SyncStatus, reason?: string): SyncReport => ({ status, reason, sent: 0, applied: 0, duplicate: 0, conflicted: 0, denied: 0, expired: 0, requeued: 0 });

export class SyncEngine {
 private d: EngineDeps;
 constructor(d: EngineDeps) { this.d = d; }

 private async state(): Promise<State> {
  const raw = await this.d.store.get(STATE);
  return raw ? JSON.parse(raw) as State : { cursor: 0, inflight: null, failures: 0 };
 }
 private async save(s: State) { await this.d.store.put(STATE, JSON.stringify(s)); }
 async suspendedReason(): Promise<string | null> { return (await this.d.plain.get(SUSPENDED)) ?? null; }      // plain on purpose: a reason code is not sensitive and must be readable while locked
 private async suspend(reason: string, lock: boolean): Promise<SyncReport> {
  await this.d.plain.put(SUSPENDED, reason);
  this.d.diag.log({ event: "sync_suspended", code: reason });
  if (lock) this.d.km.lock();
  return empty("SUSPENDED", reason);
 }
 /** Only after a fresh, server-verified sign-in. Unlocks the key and lifts a session suspension (a revoked device is gone and cannot resume). */
 async resume(): Promise<void> {
  if ((await this.suspendedReason()) === "device_revoked") throw new Error("device_revoked");     // revocation is final: register a new device instead
  const s = this.d.session();
  if (!s || s.userId !== this.d.binding.userId || s.scope !== this.d.binding.scope || s.expiresAt <= this.d.clock()) throw new Error("session_not_valid");
  await this.d.km.unlock();
  await this.d.plain.delete(SUSPENDED);
  const st = await this.state(); st.failures = 0; await this.save(st);
 }

 /** Admission + atomic enqueue. The server re-adjudicates everything; this only refuses what contracts already forbid offline. */
 async submit(input: EnqueueInput): Promise<{ ok: true; entry: QueueEntry } | { ok: false; code: string }> {
  const s = this.d.session();
  if (!s) return { ok: false, code: "no_session" };
  if (!this.d.km.unlocked) return { ok: false, code: "store_locked" };
  if (!this.d.cache.ready) return { ok: false, code: "contracts_not_loaded" };
  const v = admit(input, this.d.cache, this.d.binding, s, this.d.clock());
  if (!v.ok) { this.d.diag.log({ event: "admission_denied", code: v.code }); return { ok: false, code: v.code }; }
  try { return { ok: true, entry: await this.d.queue.enqueue(input) }; }
  catch (e) { return { ok: false, code: (e as { code?: string }).code ?? "enqueue_failed" }; }
 }

 /** Fetches approved contracts and authoritative heads (read-only, server-owned) so admission and epoch checks use current facts. */
 async refreshReference(): Promise<boolean> {
  const c = await this.d.transport.call<ContractView[]>("GET", "/contracts");
  const h = await this.d.transport.call<{ items: HeadView[] }>("GET", "/heads", { query: { size: 100 } });
  if (!c.ok || !h.ok || !Array.isArray(c.data) || !Array.isArray(h.data?.items)) return false;
  this.d.cache.loadContracts(c.data);this.d.cache.loadHeads(h.data.items);
  return true;
 }

 async syncOnce(): Promise<SyncReport> {
  const d = this.d, now = d.clock();
  if (await this.suspendedReason()) return empty("SUSPENDED", (await this.suspendedReason())!);
  const sess = d.session();
  if (!sess) return this.suspend("no_session", true);
  if (sess.userId !== d.binding.userId) return this.suspend("device_user_mismatch", true);
  if (sess.scope !== d.binding.scope) return this.suspend("cross_scope_denied", true);
  if (sess.expiresAt <= now) return this.suspend("session_expired", true);
  if (d.online && !d.online()) return empty("OFFLINE");
  if (!d.km.unlocked) return empty("SUSPENDED", "store_locked");
  if (!(await d.leader.acquire())) return empty("LEADER_ELSEWHERE");
  try {
   await d.queue.expireDue();
   const st = await this.state();
   let entries: QueueEntry[];
   if (st.inflight) {                                                  // resume: the very same batch, the very same key
    const all = await d.queue.list(["QUEUED", "SUBMITTED"]);
    entries = all.filter(e => st.inflight!.seqs.includes(e.seq));
    if (entries.length === 0) { st.inflight = null; await this.save(st); return this.syncOnce(); }
   } else {
    entries = (await d.queue.list(["QUEUED"])).slice(0, MAX_BATCH);
    if (entries.length === 0) return (await this.probe(st)) ?? empty("IDLE");
    st.inflight = { batchKey: (d.newBatchKey ?? (() => globalThis.crypto.randomUUID()))(), seqs: entries.map(e => e.seq) };
    await this.save(st);
   }
   for (const e of entries) if (e.state === "QUEUED") await d.queue.transition(e.seq, "SUBMITTED", { attempts: e.attempts + 1 });
   const commands: CommandIn[] = entries.map(e => e.command!).filter(Boolean);
   if (commands.length !== entries.length) { st.inflight = null; await this.save(st); return this.suspend("queue_corrupt", false); }
   if (!(await d.leader.stillLeader())) return empty("LEADER_ELSEWHERE");        // a paused tab must not send after losing the lease
   d.diag.log({ event: "batch_send", batchSize: commands.length, attempt: st.failures + 1 });
   const res = await d.transport.submitBatch(d.binding.deviceId, commands, st.inflight!.batchKey);
   if (!res.ok) return this.onFailure(res, st);
   const parsed = parseBatchResult(res.data, d.binding.deviceId, commands.map(c => c.seq));
   if (!parsed.ok) { d.diag.log({ event: "protocol_error", code: parsed.reason }); return this.suspend("protocol_error", false); }
   const report = empty("PROGRESS");
   report.sent = commands.length;
   const seen = new Set<number>();
   let retry = false;
   for (const r of parsed.value.results) {
    if (r.seq === null) continue;
    seen.add(r.seq);
    switch (r.status) {
     case "APPLIED": await d.queue.transition(r.seq, "APPLIED", { code: r.code, appliedVersion: r.appliedVersion }); report.applied++; break;
     case "DUPLICATE": await d.queue.transition(r.seq, "DUPLICATE", { code: r.code, appliedVersion: r.appliedVersion }); report.duplicate++; break;
     case "CONFLICT": await d.queue.transition(r.seq, "CONFLICTED", { code: r.code, conflictId: r.conflictId }); report.conflicted++; break;
     case "EXPIRED": await d.queue.transition(r.seq, "EXPIRED", { code: r.code }); report.expired++; break;
     case "REJECTED": case "INVALID": case "REJECTED_UNSTORED": await d.queue.transition(r.seq, "DENIED", { code: r.code }); report.denied++; break;
     case "RETRY": case "NOT_PROCESSED": await d.queue.transition(r.seq, "QUEUED", { code: r.code }); report.requeued++; retry = r.status === "RETRY" || retry; break;
    }
   }
   for (const c of commands) if (!seen.has(c.seq)) { await d.queue.transition(c.seq, "QUEUED", { code: "no_result" }); report.requeued++; }   // never lose a command the server did not answer
   st.cursor = Math.max(st.cursor, parsed.value.cursor); st.inflight = null; st.failures = retry ? st.failures + 1 : 0;
   await this.save(st);
   d.diag.log({ event: "batch_done", count: report.applied + report.duplicate, cursor: st.cursor, queued: report.requeued });
   if (retry) { const delay = nextDelay(st.failures, d.rng); return { ...report, status: delay === null ? "SUSPENDED" : "RETRY_LATER", delayMs: delay ?? undefined, reason: "retryable_outcome" }; }
   return report;
  } catch (e) {
   if (e instanceof LockedError) return empty("SUSPENDED", "store_locked");
   throw e;
  }
 }

 /**
  * Idle revocation/expiry probe: with nothing to send the engine would never learn that the device was revoked. At most every PROBE_INTERVAL_MS it asks the
  * server for this device's cursor (a read, scoped by the verified token): REVOKED or unknown wipes the device, 401/403 suspends it. Returns null when all is well.
  */
 private async probe(st: State): Promise<SyncReport | null> {
  const d = this.d;
  if (st.lastProbe !== undefined && d.clock() - st.lastProbe < PROBE_INTERVAL_MS) return null;
  const r = await d.transport.cursor(d.binding.deviceId);
  if (r.ok) {
   st.lastProbe = d.clock(); await this.save(st);
   if (r.data.status === "ACTIVE") return null;
   return this.onFailure({ kind: "forbidden", status: 403, code: "device_revoked" }, st);
  }
  if (r.kind === "network" || r.kind === "unavailable") return null;       // an unreachable server proves nothing; try again next tick
  if (r.kind === "notfound") return this.onFailure({ kind: "forbidden", status: 403, code: "device_revoked" }, st);
  return this.onFailure(r, st);
 }

 private async onFailure(f: { kind: string; status: number; code: string }, st: State): Promise<SyncReport> {
  const d = this.d;
  d.diag.log({ event: "batch_failed", kind: f.kind, status: f.status, code: f.code });
  if (f.kind === "unauthorized" || f.code === "device_session_expired" || f.code === "grant_expired") return this.suspend("session_expired", true);
  if (f.code === "device_revoked") {
   await applyLockPolicy(d.km, d.plain, (d.policy ?? DEFAULT_DEVICE_POLICY).onRevocation);       // crypto-shred: a revoked device keeps nothing
   await d.plain.put(SUSPENDED, "device_revoked");
   return empty("WIPED", "device_revoked");
  }
  if (f.code === "device_user_mismatch" || f.kind === "forbidden") return this.suspend(f.code === "device_user_mismatch" ? "device_user_mismatch" : "forbidden", true);
  if (f.code === "idempotency_collision") { st.inflight = null; await this.save(st); return empty("RETRY_LATER", "idempotency_collision"); }
  st.failures++; await this.save(st);
  const delay = nextDelay(st.failures, d.rng);
  if (delay === null) return this.suspend("retry_budget_spent", false);
  return { ...empty("RETRY_LATER", f.kind), delayMs: delay };
 }

 /** Repeats syncOnce while there is more to send, up to a bound. Manual "Sync now" and the scheduler both call this. */
 async drain(maxBatches = 20): Promise<SyncReport> {
  let last = empty("IDLE");
  const total = empty("IDLE");
  for (let i = 0; i < maxBatches; i++) {
   last = await this.syncOnce();
   for (const k of ["sent", "applied", "duplicate", "conflicted", "denied", "expired", "requeued"] as const) total[k] += last[k];
   if (last.status !== "PROGRESS") break;
  }
  return { ...total, status: last.status, reason: last.reason, delayMs: last.delayMs };
 }
}
