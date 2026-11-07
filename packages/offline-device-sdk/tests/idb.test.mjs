// Exercises IdbStore / IdbKeyVault against a minimal in-memory IndexedDB double. This verifies OUR adapter logic (snapshot, staged diff,
// single write transaction, rollback on error, key persistence) - it is NOT a real browser engine and does not prove browser behaviour.
import test from "node:test";
import assert from "node:assert/strict";
import {IdbStore, IdbKeyVault, KeyManager, EncryptedStore, DurableQueue} from "../src/index.ts";
import {input} from "./helpers.mjs";

function installFakeIndexedDB() {
 const dbs = new Map();
 const req = fn => { const r = {}; queueMicrotask(() => { try { r.result = fn(); r.onsuccess?.(); } catch (e) { r.error = e; r.onerror?.(); } }); return r; };
 globalThis.indexedDB = {
  open(name) {
   const r = {};
   queueMicrotask(() => {
    let data = dbs.get(name); const fresh = !data;
    if (fresh) { data = new Map(); dbs.set(name, data); }
    const db = {
     transaction(_s, mode) {
      const tx = {};
      const pending = [];
      const os = {
       getAllKeys: () => req(() => [...data.keys()]), getAll: () => req(() => [...data.values()]), get: k => req(() => data.get(k)),
       put: (v, k) => { const q = req(() => { data.set(k, v); return k; }); pending.push(q); return q; },
       delete: k => { const q = req(() => { data.delete(k); }); pending.push(q); return q; },
       clear: () => { const q = req(() => { data.clear(); }); pending.push(q); return q; }
      };
      tx.objectStore = () => os;
      if (mode === "readwrite") setTimeout(() => tx.oncomplete?.(), 5);
      return tx;
     }, createObjectStore() {}
    };
    r.result = db;
    if (fresh) r.onupgradeneeded?.();
    r.onsuccess?.();
   });
   return r;
  }
 };
 return dbs;
}

test("IdbStore: transactions are staged and written as one diff; a throwing transaction writes nothing", async () => {
 const dbs = installFakeIndexedDB();
 const s = new IdbStore("t1");
 await s.transaction(async tx => { await tx.put("a", "1"); await tx.put("b", "2"); });
 assert.equal(await s.get("a"), "1");
 await assert.rejects(() => s.transaction(async tx => { await tx.put("c", "3"); await tx.delete("a"); throw new Error("boom"); }), /boom/);
 assert.equal(await s.get("c"), undefined); assert.equal(await s.get("a"), "1", "rolled back");
 assert.deepEqual(await s.keys(""), ["a", "b"]);
 await s.transaction(async tx => { await tx.delete("b"); await tx.put("a", "9"); });
 assert.deepEqual([await s.get("a"), await s.get("b")], ["9", undefined]);
 await s.clear(); assert.deepEqual(await s.keys(""), []);
 assert.ok(dbs.has("t1"));
});

test("IdbStore serialises concurrent transactions (no lost update)", async () => {
 installFakeIndexedDB();
 const s = new IdbStore("t2");
 let chain = Promise.resolve();
 const locks = {request: (_n, _o, cb) => (chain = chain.then(() => cb({})))};
 Object.defineProperty(globalThis, "navigator", {value: {locks}, configurable: true});
 await s.put("n", "0");
 await Promise.all(Array.from({length: 10}, () => s.transaction(async tx => { const v = Number(await tx.get("n")); await new Promise(r => setTimeout(r, 1)); await tx.put("n", String(v + 1)); })));
 assert.equal(await s.get("n"), "10");
 delete globalThis.navigator;
});

test("the encrypted queue works on top of IdbStore and the key survives a reopen; destroying the key shreds the data", async () => {
 installFakeIndexedDB();
 const s = new IdbStore("t3"), vault = new IdbKeyVault("t3");
 const km = new KeyManager(vault); await km.unlock();
 const q = new DurableQueue(new EncryptedStore(s, km), () => Date.parse("2026-11-07T10:00:00Z"));
 await q.enqueue(input({payload: {text: "SECRET-IN-IDB"}}));
 const raw = JSON.stringify([...(await Promise.all((await s.keys("")).map(k => s.get(k))))]);
 assert.ok(!raw.includes("SECRET-IN-IDB"), "ciphertext only in the object store");
 const km2 = new KeyManager(new IdbKeyVault("t3")); await km2.unlock();            // "app restart": the key is loaded from its own database
 assert.equal((await new DurableQueue(new EncryptedStore(new IdbStore("t3"), km2), () => 0).list()).length, 1);
 await km2.destroy();
 const km3 = new KeyManager(new IdbKeyVault("t3")); await km3.unlock();
 await assert.rejects(() => new DurableQueue(new EncryptedStore(new IdbStore("t3"), km3), () => 0).list(), /decryption_failed/);
});
