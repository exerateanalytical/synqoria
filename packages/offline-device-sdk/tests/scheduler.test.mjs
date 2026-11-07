import test from "node:test";
import assert from "node:assert/strict";
import {SyncScheduler, DEFAULT_SCHEDULER, webLockLeader} from "../src/index.ts";
import {FakeServer, ready, input} from "./helpers.mjs";

function fakeTimers() {
 const q = []; let id = 0, now = 0;
 return {q, set(fn, ms) { q.push({id: ++id, at: now + ms, fn}); return id; }, clear(h) { const i = q.findIndex(x => x.id === h); if (i >= 0) q.splice(i, 1); },
  async advance(ms) { now += ms; for (;;) { q.sort((a, b) => a.at - b.at); const t = q[0]; if (!t || t.at > now) return; q.shift(); t.fn(); await new Promise(r => setTimeout(r, 40)); } },
  pending: () => q.map(x => x.at - now)};
}

test("scheduler drains, goes idle, wakes on nudge, and backs off on failure", async () => {
 const s = new FakeServer(), c = await ready(s), timers = fakeTimers();
 const sch = new SyncScheduler(c.engine, timers);
 await c.engine.submit(input());
 sch.start();
 await timers.advance(0);
 assert.equal(s.applied.length, 1);
 assert.deepEqual(timers.pending(), [DEFAULT_SCHEDULER.idleMs], "idle: poll later");
 await c.engine.submit(input({baseVersion: 2}));
 sch.nudge(); await timers.advance(0);
 assert.equal(s.applied.length, 2, "a nudge syncs immediately");
 await c.engine.submit(input({baseVersion: 3}));
 s.faults.push({at: s.callCount + 1, kind: "drop"});
 sch.nudge(); await timers.advance(0);
 assert.equal(sch.last.status, "RETRY_LATER");
 const wait = timers.pending()[0];
 assert.ok(wait > 0 && wait <= 1000, "first retry uses the bounded jittered delay: " + wait);
 await timers.advance(wait);
 assert.equal(s.applied.length, 3, "the retry succeeded");
 sch.stop(); assert.deepEqual(timers.pending(), []);
});

test("scheduler stops on suspension and never overlaps ticks", async () => {
 const s = new FakeServer(), c = await ready(s), timers = fakeTimers();
 const sch = new SyncScheduler(c.engine, timers);
 await c.engine.submit(input());
 s.sessionExpired = true;
 sch.start(); await timers.advance(0);
 assert.equal(sch.last.status, "SUSPENDED");
 assert.deepEqual(timers.pending(), [], "no next tick: a person must sign in again");
 sch.nudge(); assert.deepEqual(timers.pending(), []);
 const t2 = fakeTimers(), sch2 = new SyncScheduler((await ready(new FakeServer())).engine, t2);
 sch2.start(); const a = sch2.tick(), b = sch2.tick(); await Promise.all([a, b]);
 assert.equal(sch2.ticks, 1, "a second tick while one is running is ignored");
});

test("Web Locks: only the lock holder drains; others wait for the leader", async () => {
 const s = new FakeServer(), c = await ready(s), timers = fakeTimers();
 let held = false;
 const locks = {request: async (_n, opts, cb) => { if (held && opts.ifAvailable) return cb(null); held = true; try { return await cb({}); } finally { held = false; } }};
 held = true;
 const sch = new SyncScheduler(c.engine, timers, DEFAULT_SCHEDULER, locks);
 await c.engine.submit(input());
 sch.start(); await timers.advance(0);
 assert.equal(s.applied.length, 0, "another tab holds the lock: nothing sent");
 assert.deepEqual(timers.pending(), [DEFAULT_SCHEDULER.leaderWaitMs]);
 held = false; await timers.advance(DEFAULT_SCHEDULER.leaderWaitMs);
 assert.equal(s.applied.length, 1);
 assert.equal(await webLockLeader(locks, async () => 7), 7);
});
