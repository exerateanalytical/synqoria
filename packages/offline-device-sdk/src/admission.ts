// Local admission control. This is a convenience gate: the server (M38) remains the only authority and re-evaluates every command.
// The client NEVER decides a clinical action; it only refuses what the approved operation contracts already say cannot run offline.
import type { CommandIn, DeviceBinding, Session } from "./types.ts";

export type ContractView = { operationType: string; recordType: string; offlineAllowed: boolean; requiresSafetyGate: boolean; mergeable: boolean; maxQueueAgeMinutes: number; status: string };
export type HeadView = { recordType: string; recordKey: string; ownershipEpoch: number; headVersion: number; frozen: boolean };
export type Verdict = { ok: true } | { ok: false; code: string };

export class ContractCache {
 private byOp = new Map<string, ContractView>();
 private heads = new Map<string, HeadView>();
 loadContracts(list: ContractView[]) { this.byOp = new Map(list.map(c => [c.operationType, c])); }
 loadHeads(list: HeadView[]) { for (const h of list) this.heads.set(h.recordType + "/" + h.recordKey, h); }
 contract(op: string) { return this.byOp.get(op); }
 head(type: string, key: string) { return this.heads.get(type + "/" + key); }
 get ready(): boolean { return this.byOp.size > 0; }
}

/** Order: scope -> contract exists/approved -> not safety-gated -> offline allowed -> record type matches -> lifetime -> epoch. */
export function admit(cmd: Omit<CommandIn, "seq">, cache: ContractCache, binding: DeviceBinding, session: Session, now: number): Verdict {
 if (session.userId !== binding.userId) return { ok: false, code: "device_user_mismatch" };
 if (session.scope !== binding.scope) return { ok: false, code: "cross_scope_denied" };            // a different hospital cell/facility never shares a device queue
 if (session.expiresAt <= now) return { ok: false, code: "session_expired" };
 const c = cache.contract(cmd.operationType);
 if (!c || c.status !== "APPROVED") return { ok: false, code: "no_offline_contract" };
 if (c.requiresSafetyGate) return { ok: false, code: "safety_gate_required" };
 if (!c.offlineAllowed) return { ok: false, code: "operation_not_offline" };
 if (c.recordType !== cmd.recordType) return { ok: false, code: "record_type_mismatch" };
 const created = Date.parse(cmd.createdLocalAt), expires = Date.parse(cmd.expiresAt);
 if (Number.isNaN(created) || Number.isNaN(expires) || expires <= now) return { ok: false, code: "command_expired" };
 if (expires - created > c.maxQueueAgeMinutes * 60_000) return { ok: false, code: "lifetime_exceeds_contract" };
 const h = cache.head(cmd.recordType, cmd.recordKey);
 if (h) {
  if (cmd.baseEpoch < h.ownershipEpoch) return { ok: false, code: "stale_epoch" };                // fenced owner: refresh before writing
  if (cmd.baseEpoch > h.ownershipEpoch) return { ok: false, code: "invalid_epoch" };
  if (h.frozen) return { ok: false, code: "record_frozen" };
 }
 return { ok: true };
}
