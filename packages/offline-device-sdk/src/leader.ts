// Leader election so that two tabs / two processes sharing one device store never run sync workers at the same time.
// Lease-based and transactional over the shared store; in browsers the Web Locks API is preferred (webLockLeader) and this lease is the fallback.
import type { Store } from "./store.ts";
import type { Clock } from "./types.ts";

export const LEASE_MS = 15_000;
const KEY = "lease/sync";
type Lease = { holder: string; expiresAt: number; term: number };

export class LeaderElector {
 private store: Store;
 private clock: Clock;
 readonly id: string;
 private leaseMs: number;
 private term = 0;
 constructor(store: Store, clock: Clock, id: string, leaseMs: number = LEASE_MS) { this.store = store; this.clock = clock; this.id = id; this.leaseMs = leaseMs; }
 /** Acquires or renews the lease. Exactly one holder at a time; an expired lease may be taken over (term increases). */
 async acquire(): Promise<boolean> {
  return this.store.transaction(async tx => {
   const raw = await tx.get(KEY);
   const cur = raw ? JSON.parse(raw) as Lease : null;
   const now = this.clock();
   if (cur && cur.holder !== this.id && cur.expiresAt > now) return false;
   const term = cur && cur.holder === this.id && cur.expiresAt > now ? cur.term : (cur?.term ?? 0) + 1;
   await tx.put(KEY, JSON.stringify({ holder: this.id, expiresAt: now + this.leaseMs, term } satisfies Lease));
   this.term = term;
   return true;
  });
 }
 /** True only while this instance still holds an unexpired lease (call before every batch; a paused tab loses it). */
 async stillLeader(): Promise<boolean> {
  const raw = await this.store.get(KEY);
  if (!raw) return false;
  const l = JSON.parse(raw) as Lease;
  return l.holder === this.id && l.term === this.term && l.expiresAt > this.clock();
 }
 async release(): Promise<void> {
  await this.store.transaction(async tx => {
   const raw = await tx.get(KEY);
   if (raw && (JSON.parse(raw) as Lease).holder === this.id) await tx.delete(KEY);
  });
 }
}

/** Browser adapter: navigator.locks gives true mutual exclusion across tabs; run(fn) executes only if the lock is free. */
export interface LockManagerLike { request<R>(name: string, opts: { ifAvailable: boolean }, cb: (lock: unknown | null) => Promise<R | undefined>): Promise<R | undefined> }
export async function webLockLeader<T>(locks: LockManagerLike, fn: () => Promise<T>): Promise<T | undefined> {
 return locks.request<T>("synqoria-offline-sync", { ifAvailable: true }, async lock => (lock ? fn() : undefined));
}
