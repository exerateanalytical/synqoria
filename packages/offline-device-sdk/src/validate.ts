import { MAX_FIELDS, MAX_PAYLOAD_JSON, SERVER_STATUSES } from "./types.ts";
import type { BatchResult, CommandIn, CommandResult } from "./types.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const OP = /^[a-z][a-z0-9_.-]{2,63}$/;
const KEY = /^[a-z][a-z0-9_.-]{1,47}$/;
const FIELD = /^[a-z][a-z0-9_]{0,39}$/;
export const isUuid = (v: unknown): v is string => typeof v === "string" && UUID.test(v);

/** Same rules the M38 server applies; a command failing here is never queued and never sent. Returns problem codes (empty = valid). */
export function validateCommand(c: CommandIn): string[] {
 const p: string[] = [];
 if (!isUuid(c.commandId)) p.push("invalid_command_id");
 if (!Number.isSafeInteger(c.seq) || c.seq < 1) p.push("invalid_seq");
 if (!OP.test(c.operationType ?? "")) p.push("invalid_operation");
 if (!KEY.test(c.recordType ?? "")) p.push("invalid_record_type");
 if (typeof c.recordKey !== "string" || c.recordKey.length < 1 || c.recordKey.length > 120) p.push("invalid_record_key");
 if (!Number.isSafeInteger(c.baseVersion) || c.baseVersion < 1) p.push("invalid_base_version");
 if (!Number.isSafeInteger(c.baseEpoch) || c.baseEpoch < 1) p.push("invalid_base_epoch");
 if (!Array.isArray(c.fields) || c.fields.length > MAX_FIELDS || c.fields.some(f => !FIELD.test(f))) p.push("invalid_fields");
 if (typeof c.payload !== "object" || c.payload === null || Array.isArray(c.payload)) p.push("invalid_payload");
 else if (JSON.stringify(sortKeys(c.payload)).length > MAX_PAYLOAD_JSON) p.push("payload_too_large");
 const a = Date.parse(c.createdLocalAt), b = Date.parse(c.expiresAt);
 if (Number.isNaN(a) || Number.isNaN(b) || b <= a) p.push("invalid_times");
 return p;
}
export function sortKeys(v: unknown): unknown {
 if (Array.isArray(v)) return v.map(sortKeys);
 if (v && typeof v === "object") return Object.fromEntries(Object.keys(v as object).sort().map(k => [k, sortKeys((v as Record<string, unknown>)[k])]));
 return v;
}

/** Strict response validation: anything unexpected is a protocol error, never trusted. */
export function parseBatchResult(raw: unknown, expectDevice: string, sentSeqs: number[]): { ok: true; value: BatchResult } | { ok: false; reason: string } {
 const o = raw as Record<string, unknown> | null;
 if (!o || typeof o !== "object") return { ok: false, reason: "not_an_object" };
 if (o.deviceId !== expectDevice) return { ok: false, reason: "device_mismatch" };
 if (!Array.isArray(o.results) || o.results.length > sentSeqs.length) return { ok: false, reason: "bad_results" };
 if (typeof o.cursor !== "number" || !Number.isSafeInteger(o.cursor) || o.cursor < 0) return { ok: false, reason: "bad_cursor" };
 if (typeof o.serverTime !== "string" || Number.isNaN(Date.parse(o.serverTime))) return { ok: false, reason: "bad_server_time" };
 const results: CommandResult[] = [];
 for (const r of o.results as Record<string, unknown>[]) {
  if (!r || typeof r !== "object") return { ok: false, reason: "bad_result" };
  if (typeof r.status !== "string" || !(SERVER_STATUSES as readonly string[]).includes(r.status)) return { ok: false, reason: "unknown_status" };
  if (typeof r.code !== "string" || !/^[a-z][a-z0-9_]{1,63}$/.test(r.code)) return { ok: false, reason: "bad_code" };      // server vocabulary only: free text never enters local state
  if (r.seq !== null && (typeof r.seq !== "number" || !sentSeqs.includes(r.seq))) return { ok: false, reason: "unexpected_seq" };
  if (r.commandId !== null && !isUuid(r.commandId)) return { ok: false, reason: "bad_command_id" };
  if (r.appliedVersion !== null && r.appliedVersion !== undefined && !Number.isSafeInteger(r.appliedVersion)) return { ok: false, reason: "bad_version" };
  if (r.conflictId !== null && r.conflictId !== undefined && !isUuid(r.conflictId)) return { ok: false, reason: "bad_conflict_id" };
  results.push({ commandId: r.commandId as string | null, seq: r.seq as number | null, status: r.status as CommandResult["status"], code: r.code,
   appliedVersion: (r.appliedVersion as number | null | undefined) ?? null, conflictId: (r.conflictId as string | null | undefined) ?? null, originalStatus: r.originalStatus as string | undefined });
 }
 return { ok: true, value: { deviceId: o.deviceId as string, results, cursor: o.cursor, serverTime: o.serverTime } };
}
