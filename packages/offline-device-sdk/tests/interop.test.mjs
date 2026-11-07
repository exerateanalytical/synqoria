// Replays batch replies RECORDED FROM THE REAL M38 SyncService (PostgreSQL 18) through the SDK. The fixture is generated and structurally guarded by
// services/offline-sync-core/.../SdkInteropFixtureTest.java, so a change in the service's reply shape fails the Java build until it is reviewed here.
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {parseBatchResult} from "../src/index.ts";
import {makeClient, DEVICE} from "./helpers.mjs";

const fixture = JSON.parse(readFileSync(new URL("./fixtures/m38-real-batch-replies.json", import.meta.url), "utf8"));
const expected = {
 "applied": ["APPLIED"], "duplicate": ["DUPLICATE"], "conflict-stale-base": ["CONFLICTED"], "rejected-safety-gate": ["DENIED"], "expired": ["EXPIRED"],
 "retry-frozen-then-not-processed": ["QUEUED", "QUEUED"]
};

test("the fixture was produced by the real service", () => {
 assert.match(fixture.source, /SyncService on PostgreSQL/);
 assert.deepEqual(fixture.scenarios.map(s => s.name), Object.keys(expected));
});

for (const sc of fixture.scenarios) {
 test(`SDK understands the real service reply: ${sc.name}`, async () => {
  const sent = sc.request.commands.map(c => c.seq);
  const parsed = parseBatchResult(sc.reply, sc.request.deviceId, sent);
  assert.ok(parsed.ok, JSON.stringify(parsed));

  // drive the whole engine with the recorded request/reply
  const c = makeClient({fetch: async () => { throw new Error("unused"); }}, {binding: {deviceId: sc.request.deviceId, userId: "22222222-2222-4222-8222-222222222222", scope: "cell-A/facility-1"}});
  await c.km.unlock();
  const ids = [];
  for (const cmd of sc.request.commands) {
   const {seq, ...rest} = cmd;
   const iso = v => typeof v === "number" ? new Date(v * 1000).toISOString() : v;      // the Java recorder serialised its own request Instants as epoch seconds; the reply is what matters
   const e = await c.queue.enqueue({...rest, createdLocalAt: iso(cmd.createdLocalAt), expiresAt: new Date(c.clock() + 86400_000).toISOString()});
   ids.push([cmd.commandId, e.seq]);
  }
  const seqOf = new Map(ids);
  const reply = {...sc.reply, results: sc.reply.results.map(r => ({...r, seq: seqOf.get(r.commandId)})), cursor: sc.reply.cursor};
  const transport = {call: async () => ({ok: true, status: 200, data: reply}), submitBatch: async () => ({ok: true, status: 200, data: reply})};
  c.engine.d.transport = transport;                                           // swap the wire for the recorded reply
  const report = await c.engine.syncOnce();
  assert.ok(["PROGRESS", "RETRY_LATER"].includes(report.status), report.status);
  const states = (await c.queue.list()).map(e => e.state);
  assert.deepEqual(states, expected[sc.name]);
  const first = (await c.queue.list())[0];
  assert.equal(first.code, sc.reply.results[0].code, "the server's exact outcome code is kept, not reinterpreted");
  if (sc.name === "conflict-stale-base") assert.match(first.conflictId, /^[0-9a-f-]{36}$/);
  if (sc.name === "applied") assert.ok(first.appliedVersion >= 2 && first.command === null);
  if (sc.name === "retry-frozen-then-not-processed") assert.equal(report.status, "RETRY_LATER");
 });
}
