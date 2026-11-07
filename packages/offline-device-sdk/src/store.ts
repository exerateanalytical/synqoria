// Transactional key/value store abstraction. All SDK state (queue, cursor, in-flight batch, lease, caches) lives behind it.
// Production adapter: IndexedDB in the PWA (see idb-store.ts). Tests and the reference semantics: MemoryStore.
export interface Tx {
 get(key: string): Promise<string | undefined>;
 put(key: string, value: string): Promise<void>;
 delete(key: string): Promise<void>;
 keys(prefix: string): Promise<string[]>;
}
export interface Store extends Tx {
 /** All-or-nothing: if fn throws, nothing it wrote is visible. Concurrent transactions are serialised. */
 transaction<T>(fn: (tx: Tx) => Promise<T>): Promise<T>;
 clear(): Promise<void>;
}

export class MemoryStore implements Store {
 private data = new Map<string, string>();
 private chain: Promise<unknown> = Promise.resolve();
 /** Fault injection for tests: throw on the Nth write inside a transaction. */
 failWriteAt: number | null = null;
 async get(key: string) { return this.data.get(key); }
 async put(key: string, value: string) { this.data.set(key, value); }
 async delete(key: string) { this.data.delete(key); }
 async keys(prefix: string) { return [...this.data.keys()].filter(k => k.startsWith(prefix)).sort(); }
 async clear() { this.data.clear(); }
 transaction<T>(fn: (tx: Tx) => Promise<T>): Promise<T> {
  const run = async (): Promise<T> => {
   const staged = new Map(this.data);
   let writes = 0;
   const self = this;
   const tx: Tx = {
    async get(k) { return staged.get(k); },
    async put(k, v) { writes++; if (self.failWriteAt !== null && writes === self.failWriteAt) throw new Error("injected_write_failure"); staged.set(k, v); },
    async delete(k) { staged.delete(k); },
    async keys(prefix) { return [...staged.keys()].filter(x => x.startsWith(prefix)).sort(); }
   };
   const out = await fn(tx);
   this.data = staged;                                                   // commit
   return out;
  };
  const next = this.chain.then(run, run);
  this.chain = next.catch(() => undefined);
  return next;
 }
 /** Raw snapshot for assertions (what is physically stored). */
 raw(): Map<string, string> { return new Map(this.data); }
}
