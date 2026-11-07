// Retention, lock and wipe policy (packages 15, 16, 20). All values are configuration, not clinical decisions; defaults are conservative.
import type { KeyManager } from "./crypto.ts";
import type { Store } from "./store.ts";
import { DurableQueue } from "./queue.ts";
import type { QueueEntry } from "./types.ts";

export type LogoutMode = "LOCK" | "WIPE";
export type DevicePolicy = { onLogout: LogoutMode; onRevocation: "WIPE"; onSessionExpiry: "LOCK" };
export const DEFAULT_DEVICE_POLICY: DevicePolicy = { onLogout: "LOCK", onRevocation: "WIPE", onSessionExpiry: "LOCK" };

/** LOCK: drop the in-memory key; queued data stays sealed and syncing stops. WIPE: crypto-shred (destroy the key) and erase the store. */
export async function applyLockPolicy(km: KeyManager, store: Store, mode: LogoutMode): Promise<{ lost: number }> {
 let lost = 0;
 if (mode === "WIPE") {
  try { lost = (await new DurableQueue(store, () => Date.now()).list(["QUEUED", "SUBMITTED"])).length; } catch { lost = -1; }   // -1: could not be counted (already locked)
  await km.destroy();
  await store.clear();
 } else km.lock();
 return { lost };
}

export type MetadataRow = { seq: number; operationType: string; recordType: string; recordTag: string; state: string; code: string | null; attempts: number; createdLocalAt: string; expiresAt: string };
/** Staff-controlled recovery export (package 20): metadata only. No payload, no raw record key (a 48-bit tag), no commandId, no conflict id. */
export function redactedExport(entries: QueueEntry[], exportedAt: string): { exportedAt: string; kind: "queue-metadata"; rows: MetadataRow[] } {
 return { exportedAt, kind: "queue-metadata", rows: entries.map(e => ({ seq: e.seq, operationType: e.operationType, recordType: e.recordType, recordTag: e.recordKeyHash, state: e.state, code: e.code,
  attempts: e.attempts, createdLocalAt: e.createdLocalAt, expiresAt: e.expiresAt })) };
}
