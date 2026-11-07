// Encryption at rest for everything the device stores. AES-256-GCM through WebCrypto (browsers and Node 20+), non-extractable keys,
// ciphertext bound to its storage slot (AAD = the key name) so entries cannot be swapped between slots.
// Key lifecycle: generated at device binding -> held non-extractable -> LOCK drops the in-memory handle (data stays sealed until a verified
// sign-in unlocks it) -> DESTROY erases the key from the vault, which crypto-shreds all stored data (revocation, wipe policy).
import type { Store, Tx } from "./store.ts";

export interface KeyVault { load(): Promise<CryptoKey | null>; save(k: CryptoKey): Promise<void>; erase(): Promise<void> }
export class MemoryKeyVault implements KeyVault {
 private k: CryptoKey | null = null;
 async load() { return this.k; }
 async save(k: CryptoKey) { this.k = k; }
 async erase() { this.k = null; }
}
export class LockedError extends Error { constructor() { super("store_locked"); this.name = "LockedError"; } }

export class KeyManager {
 private key: CryptoKey | null = null;
 private vault: KeyVault;
 private subtle: SubtleCrypto;
 constructor(vault: KeyVault, subtle: SubtleCrypto = globalThis.crypto.subtle) { this.vault = vault; this.subtle = subtle; }
 /** Creates a key on first use, otherwise loads the persisted one. Call only after the server confirmed the session. */
 async unlock(): Promise<void> {
  let k = await this.vault.load();
  if (!k) { k = await this.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]); await this.vault.save(k); }
  this.key = k;
 }
 lock(): void { this.key = null; }
 async destroy(): Promise<void> { this.key = null; await this.vault.erase(); }
 get unlocked(): boolean { return this.key !== null; }
 handle(): CryptoKey { if (!this.key) throw new LockedError(); return this.key; }
 get crypto(): SubtleCrypto { return this.subtle; }
}

const enc = new TextEncoder(), dec = new TextDecoder();
const b64 = (u: Uint8Array) => { let s = ""; for (const b of u) s += String.fromCharCode(b); return btoa(s); };
const unb64 = (s: string) => Uint8Array.from(atob(s), c => c.charCodeAt(0));

export async function seal(km: KeyManager, slot: string, plaintext: string): Promise<string> {
 const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));
 const ct = new Uint8Array(await km.crypto.encrypt({ name: "AES-GCM", iv, additionalData: enc.encode(slot) }, km.handle(), enc.encode(plaintext)));
 return "v1." + b64(iv) + "." + b64(ct);
}
export async function open(km: KeyManager, slot: string, sealed: string): Promise<string> {
 const [v, iv, ct] = sealed.split(".");
 if (v !== "v1" || !iv || !ct) throw new Error("bad_envelope");
 try {
  return dec.decode(await km.crypto.decrypt({ name: "AES-GCM", iv: unb64(iv), additionalData: enc.encode(slot) }, km.handle(), unb64(ct)));
 } catch (e) {
  if (e instanceof LockedError) throw e;
  throw new Error("decryption_failed");                      // wrong key (shredded), tampered ciphertext or swapped slot
 }
}

/** Store decorator: values are sealed on write and opened on read. Key names (seq numbers, fixed words) are the only plaintext. */
export class EncryptedStore implements Store {
 private inner: Store;
 private km: KeyManager;
 constructor(inner: Store, km: KeyManager) { this.inner = inner; this.km = km; }
 private wrap(tx: Tx): Tx {
  const km = this.km;
  return {
   async get(k) { const v = await tx.get(k); return v === undefined ? undefined : open(km, k, v); },
   async put(k, v) { await tx.put(k, await seal(km, k, v)); },
   delete: k => tx.delete(k), keys: p => tx.keys(p)
  };
 }
 get(k: string) { return this.wrap(this.inner).get(k); }
 put(k: string, v: string) { return this.wrap(this.inner).put(k, v); }
 delete(k: string) { return this.inner.delete(k); }
 keys(p: string) { return this.inner.keys(p); }
 clear() { return this.inner.clear(); }
 transaction<T>(fn: (tx: Tx) => Promise<T>): Promise<T> { return this.inner.transaction(tx => fn(this.wrap(tx))); }
}
