// Automatic sync scheduling (package 08). Timers, the lock API and connectivity events are injected so the behaviour is deterministic in tests.
// One tick = one bounded drain; the next tick is chosen from the outcome: retry delay (already backed off with jitter) for retryable outcomes,
// a polling interval when idle, the lease length when another tab leads, and NO next tick when suspended or wiped (a human must resume).
import { webLockLeader } from "./leader.ts";
import type { LockManagerLike } from "./leader.ts";
import type { SyncEngine, SyncReport } from "./sync.ts";

export type Timers = { set(fn: () => void, ms: number): unknown; clear(id: unknown): void };
export type LockApi = LockManagerLike;
export type SchedulerConfig = { idleMs: number; leaderWaitMs: number; offlineWaitMs: number; minGapMs: number };      // minGapMs is reserved for coalescing bursts of nudges
export const DEFAULT_SCHEDULER: SchedulerConfig = { idleMs: 60_000, leaderWaitMs: 15_000, offlineWaitMs: 30_000, minGapMs: 250 };

export class SyncScheduler {
 private engine: SyncEngine;
 private timers: Timers;
 private cfg: SchedulerConfig;
 private locks: LockApi | null;
 private handle: unknown = null;
 private running = false;
 private stopped = true;
 last: SyncReport | null = null;
 ticks = 0;
 constructor(engine: SyncEngine, timers: Timers, cfg: SchedulerConfig = DEFAULT_SCHEDULER, locks: LockApi | null = null) { this.engine = engine; this.timers = timers; this.cfg = cfg; this.locks = locks; }
 start() { this.stopped = false; this.schedule(0); }
 stop() { this.stopped = true; if (this.handle !== null) this.timers.clear(this.handle); this.handle = null; }
 /** Call after an enqueue, on the browser "online" event, or when the user presses Sync now. */
 nudge() { if (!this.stopped) this.schedule(0); }
 private schedule(ms: number) {
  if (this.stopped) return;
  if (this.handle !== null) this.timers.clear(this.handle);
  this.handle = this.timers.set(() => { this.handle = null; void this.tick(); }, ms);
 }
 async tick(): Promise<void> {
  if (this.running || this.stopped) return;                      // never two overlapping drains in this tab
  this.running = true;
  try {
   const run = async () => this.engine.drain();
   const rep: SyncReport | undefined = this.locks ? await webLockLeader<SyncReport>(this.locks, run) : await run();     // across tabs the Web Lock gives mutual exclusion; the lease is the second line
   this.ticks++;
   if (!rep) { this.last = null; this.schedule(this.cfg.leaderWaitMs); return; }
   this.last = rep;
   switch (rep.status) {
    case "PROGRESS": this.schedule(0); break;
    case "IDLE": this.schedule(this.cfg.idleMs); break;
    case "RETRY_LATER": this.schedule(rep.delayMs ?? this.cfg.idleMs); break;
    case "LEADER_ELSEWHERE": this.schedule(this.cfg.leaderWaitMs); break;
    case "OFFLINE": this.schedule(this.cfg.offlineWaitMs); break;
    case "SUSPENDED": case "WIPED": this.stopped = true; break;       // needs a person (sign-in, resume or re-registration)
   }
  } catch { this.schedule(this.cfg.idleMs); } finally { this.running = false; }
 }
}
