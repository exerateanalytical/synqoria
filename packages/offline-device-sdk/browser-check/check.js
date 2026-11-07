// Runs INSIDE a real browser against the real IndexedDB, WebCrypto and Web Locks implementations. Synthetic data only.
// Phases (query string): ?phase=all (default, single page) | write / read (queue survives a page reload) | hold / try (cross-tab Web Lock) | counter (cross-tab store serialisation)
import {KeyManager, IdbKeyVault, IdbStore, EncryptedStore, DurableQueue, ContractCache, Transport, SyncEngine, LeaderElector, Diagnostics, webLockLeader, applyLockPolicy} from "/sdk/index.js";

const q = new URLSearchParams(location.search), phase = q.get("phase") ?? "all", db = q.get("db") ?? "sdk-browser-check";
const results = []; window.__results = results; window.__done = false;
const rec = (name, ok, detail = "") => { results.push({name, ok: !!ok, detail: String(detail)}); document.getElementById("out").textContent = results.map(r => (r.ok ? "PASS " : "FAIL ") + r.name + (r.detail ? " - " + r.detail : "")).join("\n"); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const idbDelete = n => new Promise(r => { const d = indexedDB.deleteDatabase(n); d.onsuccess = d.onerror = d.onblocked = () => r(); });
const T0 = Date.parse("2026-11-07T10:00:00Z");
const input = (o = {}) => ({commandId: crypto.randomUUID(), operationType: "note.add", recordType: "note", recordKey: "rec-1", baseVersion: 1, baseEpoch: 1, fields: ["text"], payload: {text: "BROWSER-SECRET-TEXT"},
 createdLocalAt: "2026-11-07T09:59:00.000Z", expiresAt: "2026-11-08T09:59:00.000Z", ...o});
async function allRawValues(name) {
 const d = await new Promise((res, rej) => { const r = indexedDB.open(name); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
 const names = [...d.objectStoreNames]; let all = [];
 for (const n of names) all = all.concat(await new Promise(res => { const r = d.transaction(n).objectStore(n).getAll(); r.onsuccess = () => res(r.result); }));
 d.close(); return all;
}

async function main() {
 rec("secure context with WebCrypto, IndexedDB and Web Locks", isSecureContext && !!crypto.subtle && !!indexedDB && !!navigator.locks, navigator.userAgent);

 if (phase === "all") {
  await idbDelete(db); await idbDelete(db + "-key");
  const km = new KeyManager(new IdbKeyVault(db)); await km.unlock();
  const key = await new IdbKeyVault(db).load();
  let exportable = true; try { await crypto.subtle.exportKey("raw", key); } catch { exportable = false; }
  rec("the stored AES key is non-extractable and survives structured clone through IndexedDB", key && key.extractable === false && !exportable);
  const store = new IdbStore(db), enc = new EncryptedStore(store, km), queue = new DurableQueue(enc, () => T0);
  const e = await queue.enqueue(input());
  rec("enqueue persists to IndexedDB with sequence 1", e.seq === 1);
  const raw = JSON.stringify(await allRawValues(db));
  rec("no clinical text, record key or identifier is readable in the browser's IndexedDB bytes", !raw.includes("BROWSER-SECRET-TEXT") && !raw.includes("rec-1") && raw.includes("v1."));
  // atomicity: a throwing transaction leaves nothing behind
  let threw = false; try { await enc.transaction(async tx => { await tx.put("x/1", "a"); throw new Error("boom"); }); } catch { threw = true; }
  rec("a failing IndexedDB transaction is rolled back", threw && (await enc.get("x/1")) === undefined);
  // restart: brand new objects over the same databases
  const km2 = new KeyManager(new IdbKeyVault(db)); await km2.unlock();
  const q2 = new DurableQueue(new EncryptedStore(new IdbStore(db), km2), () => T0);
  const l = await q2.list();
  rec("after a 'restart' (new objects) the encrypted queue is readable with the persisted key", l.length === 1 && l[0].command.payload.text === "BROWSER-SECRET-TEXT");
  // lock then unlock
  km2.lock(); let locked = false; try { await q2.list(); } catch (x) { locked = x.name === "LockedError"; }
  rec("a locked store refuses to read", locked);
  // wipe = crypto-shred
  await applyLockPolicy(km2, new IdbStore(db), "WIPE");
  const km3 = new KeyManager(new IdbKeyVault(db)); await km3.unlock();
  const l3 = await new DurableQueue(new EncryptedStore(new IdbStore(db), km3), () => T0).list();
  rec("WIPE destroys the key and erases the store", l3.length === 0 && (await allRawValues(db)).length === 0);
  // sync engine in the browser: crash after send, then resume with the same batch key
  const dbS = db + "-sync" + Date.now();      // a fresh database name: deleting a database that still has open connections would block
  const kmA = new KeyManager(new IdbKeyVault(dbS)); await kmA.unlock();
  const plainA = new IdbStore(dbS), encA = new EncryptedStore(plainA, kmA);
  const queueA = new DurableQueue(encA, () => now), cache = new ContractCache();
  cache.loadContracts([{operationType: "note.add", recordType: "note", offlineAllowed: true, requiresSafetyGate: false, mergeable: false, maxQueueAgeMinutes: 1440, status: "APPROVED"}]);
  const binding = {deviceId: "11111111-1111-4111-8111-111111111111", userId: "22222222-2222-4222-8222-222222222222", scope: "cell/facility"};
  const keys = []; let mode = "drop"; let now = T0;      // a movable clock: a crashed tab's lease must expire before another tab may lead
  const fetchStub = async (url, init) => {
   keys.push(init.headers["Idempotency-Key"]);
   if (mode === "drop") throw new TypeError("network");
   const body = JSON.parse(init.body);
   return {ok: true, status: 200, json: async () => ({deviceId: body.deviceId, cursor: 1, serverTime: new Date().toISOString(), results: body.commands.map(c => ({commandId: c.commandId, seq: c.seq, status: "APPLIED", code: "applied", appliedVersion: 2, conflictId: null}))})};
  };
  const mk = () => new SyncEngine({store: encA, plain: plainA, km: kmA, queue: queueA, transport: new Transport({fetch: fetchStub, csrf: () => "c"}), cache, binding, session: () => ({userId: binding.userId, scope: binding.scope, expiresAt: T0 + 3600000}),
   clock: () => now, rng: () => 0.5, diag: new Diagnostics(), leader: new LeaderElector(plainA, () => now, "tab-" + Math.random())});
  const eng1 = mk(); await eng1.submit(input());
  const r1 = await eng1.syncOnce();
  rec("a dropped connection leaves the command SUBMITTED and the engine asks to retry later", r1.status === "RETRY_LATER" && (await queueA.list())[0].state === "SUBMITTED");
  mode = "ok";
  now += 20000;                                                                          // the crashed tab's 15 s lease has expired
  const eng2 = mk();                                                                       // a different engine instance = a restarted tab
  const r2 = await eng2.syncOnce().catch(e => ({status: "ERR:" + e.message}));
  rec("the resumed engine resends the identical batch key and the command is applied", keys.length === 2 && keys[0] === keys[1] && ["PROGRESS"].includes(r2.status) && (await queueA.list())[0].state === "APPLIED", JSON.stringify({keys: keys.length, same: keys[0] === keys[1], r2: r2.status}));
  const raw2 = JSON.stringify(await allRawValues(dbS));
  rec("the applied command's payload was purged from storage (data minimisation)", (await queueA.list())[0].command === null && !raw2.includes("BROWSER-SECRET-TEXT"));
 }

 if (phase === "write") {
  await idbDelete(db); await idbDelete(db + "-key");
  const km = new KeyManager(new IdbKeyVault(db)); await km.unlock();
  const queue = new DurableQueue(new EncryptedStore(new IdbStore(db), km), () => T0);
  await queue.enqueue(input({payload: {text: "SURVIVES-RELOAD"}})); await queue.enqueue(input({payload: {text: "SECOND"}}));
  rec("wrote 2 encrypted commands before reload", (await queue.list()).length === 2);
 }
 if (phase === "read") {
  const km = new KeyManager(new IdbKeyVault(db)); await km.unlock();
  const queue = new DurableQueue(new EncryptedStore(new IdbStore(db), km), () => T0);
  const l = await queue.list();
  rec("after a real page reload the encrypted queue is intact and readable", l.length === 2 && l[0].seq === 1 && l[1].seq === 2 && l[0].command.payload.text === "SURVIVES-RELOAD");
  const next = await queue.enqueue(input());
  rec("the append-only sequence continues after the reload", next.seq === 3);
  const raw = JSON.stringify(await allRawValues(db));
  rec("storage is still ciphertext only", !raw.includes("SURVIVES-RELOAD"));
 }
 if (phase === "hold") {
  const ms = Number(q.get("ms") ?? 6000);
  const got = await webLockLeader(navigator.locks, async () => { rec("this tab holds the cross-tab sync lock", true); await sleep(ms); return "held"; });
  rec("lock released after use", got === "held");
 }
 if (phase === "try") {
  const got = await webLockLeader(navigator.locks, async () => "ran");
  rec("another tab holds the lock: this tab must not run a sync", got === undefined, String(got));
 }
 if (phase === "counter") {
  const store = new IdbStore(db + "-counter"); const n = Number(q.get("n") ?? 20);
  if (q.get("start")) { const at = Number(q.get("start")); while (Date.now() < at) await sleep(20); }
  for (let i = 0; i < n; i++) await store.transaction(async tx => { const v = Number((await tx.get("n")) ?? "0"); await new Promise(r => setTimeout(r, 2)); await tx.put("n", String(v + 1)); });
  rec("finished " + n + " increments", true);
 }
 if (phase === "counter-read") {
  const store = new IdbStore(db + "-counter");
  rec("two tabs incremented concurrently without a lost update", (await store.get("n")) === q.get("expect"), String(await store.get("n")));
 }
}
main().catch(e => rec("unexpected error", false, e.stack || e)).finally(() => { document.getElementById("status").textContent = results.every(r => r.ok) ? "ALL PASS" : "FAILURES"; window.__done = true; });
