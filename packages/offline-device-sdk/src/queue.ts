// Durable local command queue with an append-only device sequence. Enqueue is atomic: sequence allocation, quota checks and the write
// happen in one store transaction, so a crash can never leave a gap, a duplicate sequence or a half-written command.
import type { Store } from "./store.ts";
import { sortKeys, validateCommand } from "./validate.ts";
import { TERMINAL_STATES } from "./types.ts";
import type { Clock, CommandIn, LocalState, QueueEntry } from "./types.ts";

export type QueuePolicy = { maxEntries: number; maxBytes: number; terminalRetentionMs: number; maxTerminalEntries: number };
export const DEFAULT_QUEUE_POLICY: QueuePolicy = { maxEntries: 1000, maxBytes: 4 * 1024 * 1024, terminalRetentionMs: 7 * 86400_000, maxTerminalEntries: 500 };
type Meta = { nextSeq: number; bytes: number; count: number };
const META = "q/meta";
const slot = (seq: number) => "q/e/" + String(seq).padStart(12, "0");

export async function hashKey(recordKey: string): Promise<string> {
 const d = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(recordKey));
 return [...new Uint8Array(d)].slice(0, 6).map(b => b.toString(16).padStart(2, "0")).join("");     // 48-bit tag: groups entries without exposing identifiers
}

export type EnqueueInput = Omit<CommandIn, "seq">;
export class EnqueueError extends Error { code: string; constructor(code: string) { super(code); this.code = code; this.name = "EnqueueError"; } }

export class DurableQueue {
 private store: Store;
 private clock: Clock;
 private policy: QueuePolicy;
 constructor(store: Store, clock: Clock, policy: QueuePolicy = DEFAULT_QUEUE_POLICY) { this.store = store; this.clock = clock; this.policy = policy; }

 private async meta(tx: { get(k: string): Promise<string | undefined> }): Promise<Meta> {
  const raw = await tx.get(META);
  return raw ? JSON.parse(raw) as Meta : { nextSeq: 1, bytes: 0, count: 0 };
 }

 /** Atomically appends a command. Admission (contract/epoch/scope) is the caller's job and happens BEFORE this. */
 async enqueue(input: EnqueueInput): Promise<QueueEntry> {
  return this.store.transaction(async tx => {
   const m = await this.meta(tx);
   const command: CommandIn = { ...input, seq: m.nextSeq };
   const problems = validateCommand(command);
   if (problems.length) throw new EnqueueError(problems[0]!);
   const json = JSON.stringify(sortKeys(command));
   const bytes = json.length;
   if (m.count + 1 > this.policy.maxEntries) throw new EnqueueError("queue_entries_quota");
   if (m.bytes + bytes > this.policy.maxBytes) throw new EnqueueError("queue_bytes_quota");
   for (const k of await tx.keys("q/e/")) {                         // client-side duplicate command ids are refused (the server would also answer DUPLICATE)
    const e = JSON.parse((await tx.get(k))!) as QueueEntry;
    if (e.commandId === command.commandId) throw new EnqueueError("duplicate_command_id");
   }
   const now = new Date(this.clock()).toISOString();
   const entry: QueueEntry = { seq: command.seq, commandId: command.commandId, operationType: command.operationType, recordType: command.recordType,
    recordKeyHash: await hashKey(command.recordKey), baseVersion: command.baseVersion, baseEpoch: command.baseEpoch, state: "QUEUED", attempts: 0, code: null,
    appliedVersion: null, conflictId: null, createdLocalAt: command.createdLocalAt, expiresAt: command.expiresAt, updatedAt: now, bytes, command };
   await tx.put(slot(entry.seq), JSON.stringify(entry));
   await tx.put(META, JSON.stringify({ nextSeq: m.nextSeq + 1, bytes: m.bytes + bytes, count: m.count + 1 }));
   return entry;
  });
 }

 async list(states?: LocalState[]): Promise<QueueEntry[]> {
  return this.store.transaction(async tx => {
   const out: QueueEntry[] = [];
   for (const k of await tx.keys("q/e/")) { const e = JSON.parse((await tx.get(k))!) as QueueEntry; if (!states || states.includes(e.state)) out.push(e); }
   return out;
  });
 }
 async get(seq: number): Promise<QueueEntry | undefined> {
  const raw = await this.store.get(slot(seq));
  return raw ? JSON.parse(raw) as QueueEntry : undefined;
 }
 async stats(): Promise<Meta & { queued: number }> {
  const m = await this.store.transaction(tx => this.meta(tx));
  return { ...m, queued: (await this.list(["QUEUED", "SUBMITTED"])).length };
 }

 /** Applies a state change inside the caller's transaction. Terminal states purge the clinical payload (data minimisation). */
 async transition(seq: number, to: LocalState, patch: Partial<Pick<QueueEntry, "code" | "appliedVersion" | "conflictId" | "attempts">> = {}): Promise<void> {
  await this.store.transaction(async tx => {
   const raw = await tx.get(slot(seq));
   if (!raw) return;
   const e = JSON.parse(raw) as QueueEntry;
   if ((TERMINAL_STATES as readonly string[]).includes(e.state) && e.state !== to) return;   // terminal outcomes never regress
   const next: QueueEntry = { ...e, ...patch, state: to, updatedAt: new Date(this.clock()).toISOString() };
   if ((TERMINAL_STATES as readonly string[]).includes(to)) {
    const m = await this.meta(tx);
    if (next.command) { next.command = null; await tx.put(META, JSON.stringify({ ...m, bytes: Math.max(0, m.bytes - e.bytes) })); next.bytes = 0; }
   }
   await tx.put(slot(seq), JSON.stringify(next));
  });
 }

 /** Marks every unsent command whose lifetime ended as EXPIRED (the server would refuse it anyway). */
 async expireDue(): Promise<number> {
  let n = 0;
  for (const e of await this.list(["QUEUED"])) if (Date.parse(e.expiresAt) <= this.clock()) { await this.transition(e.seq, "EXPIRED", { code: "command_expired" }); n++; }
  return n;
 }
 /** Bounded retention: terminal metadata older than the policy, and the oldest beyond the cap, are deleted. Unsent commands are never deleted. */
 async purgeTerminal(): Promise<number> {
  return this.store.transaction(async tx => {
   const m = await this.meta(tx);
   const terminal: QueueEntry[] = [];
   for (const k of await tx.keys("q/e/")) { const e = JSON.parse((await tx.get(k))!) as QueueEntry; if ((TERMINAL_STATES as readonly string[]).includes(e.state)) terminal.push(e); }
   terminal.sort((a, b) => a.seq - b.seq);
   const cutoff = this.clock() - this.policy.terminalRetentionMs;
   const doomed = terminal.filter((e, i) => Date.parse(e.updatedAt) < cutoff || i < terminal.length - this.policy.maxTerminalEntries);
   for (const e of doomed) await tx.delete(slot(e.seq));
   await tx.put(META, JSON.stringify({ ...m, count: m.count - doomed.length }));         // the sequence counter is NOT reduced: seq numbers are never reused
   return doomed.length;
  });
 }
}
