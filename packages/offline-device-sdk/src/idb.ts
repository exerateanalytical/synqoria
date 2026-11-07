// Browser (PWA) persistence adapters over IndexedDB. NOT exercised by the Node test-suite (no IndexedDB there); the Store contract is covered
// by MemoryStore tests and this adapter is deliberately thin. Pending: real-browser / emulator evidence (see handoff, package 10 and 24).
//  - IdbStore: every transaction takes an exclusive Web Lock (when available), loads a fresh snapshot, runs on a staged copy and writes the diff in
//    ONE IndexedDB transaction, so a crash can never persist half of a transaction and two tabs never interleave.
//  - IdbKeyVault: persists the non-extractable AES key as a CryptoKey (structured clone); the raw key bytes are never visible to script.
import type { KeyVault } from "./crypto.ts";
import type { Store, Tx } from "./store.ts";

const STORE = "kv";
function open(name: string): Promise<IDBDatabase> {
 return new Promise((resolve, reject) => {
  const req = indexedDB.open(name, 1);
  req.onupgradeneeded = () => { req.result.createObjectStore(STORE); };
  req.onsuccess = () => resolve(req.result);
  req.onerror = () => reject(req.error);
 });
}
const wrap = <T>(r: IDBRequest<T>) => new Promise<T>((res, rej) => { r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });

export class IdbStore implements Store {
 private name: string;
 private dbp: Promise<IDBDatabase> | null = null;
 constructor(name: string) { this.name = name; }
 private db() { return (this.dbp ??= open(this.name)); }
 private async snapshot(): Promise<Map<string, string>> {
  const db = await this.db();
  const tx = db.transaction(STORE, "readonly"), os = tx.objectStore(STORE);
  const [keys, vals] = await Promise.all([wrap(os.getAllKeys()), wrap(os.getAll())]);
  return new Map(keys.map((k, i) => [String(k), vals[i] as string]));
 }
 async get(key: string) { return (await this.snapshot()).get(key); }
 async put(key: string, value: string) { await this.transaction(async tx => tx.put(key, value)); }
 async delete(key: string) { await this.transaction(async tx => tx.delete(key)); }
 async keys(prefix: string) { return [...(await this.snapshot()).keys()].filter(k => k.startsWith(prefix)).sort(); }
 async clear() { const db = await this.db(); const tx = db.transaction(STORE, "readwrite"); await wrap(tx.objectStore(STORE).clear()); }
 async transaction<T>(fn: (tx: Tx) => Promise<T>): Promise<T> {
  const run = async (): Promise<T> => {
   const before = await this.snapshot(), staged = new Map(before);
   const tx: Tx = {
    async get(k) { return staged.get(k); }, async put(k, v) { staged.set(k, v); }, async delete(k) { staged.delete(k); },
    async keys(p) { return [...staged.keys()].filter(k => k.startsWith(p)).sort(); }
   };
   const out = await fn(tx);
   const db = await this.db();
   const w = db.transaction(STORE, "readwrite"), os = w.objectStore(STORE);
   for (const [k, v] of staged) if (before.get(k) !== v) os.put(v, k);
   for (const k of before.keys()) if (!staged.has(k)) os.delete(k);
   await new Promise<void>((res, rej) => { w.oncomplete = () => res(); w.onerror = () => rej(w.error); w.onabort = () => rej(w.error); });
   return out;
  };
  const locks = (globalThis.navigator as (Navigator & { locks?: LockManager }) | undefined)?.locks;
  return locks ? locks.request("synqoria-offline-store-" + this.name, { mode: "exclusive" }, run) as Promise<T> : run();
 }
}

export class IdbKeyVault implements KeyVault {
 private name: string;
 constructor(name: string) { this.name = name + "-key"; }
 async load() { const db = await open(this.name); return ((await wrap(db.transaction(STORE).objectStore(STORE).get("k"))) as CryptoKey | undefined) ?? null; }
 async save(k: CryptoKey) { const db = await open(this.name); await wrap(db.transaction(STORE, "readwrite").objectStore(STORE).put(k, "k")); }
 async erase() { const db = await open(this.name); await wrap(db.transaction(STORE, "readwrite").objectStore(STORE).clear()); }
}
