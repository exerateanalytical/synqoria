// Types and routes of the M38 offline device SDK. Field names are the M38 OpenAPI names (parity-tested in CI).
export const CONTRACT_VERSION = "1.0.0";                       // pinned M38 contract (contracts/openapi/synqoria-m38-offline-v1.yaml info.version)
export const MAX_BATCH = 50;
export const MAX_PAYLOAD_JSON = 16000;
export const MAX_FIELDS = 40;

/** Every route the SDK may call, relative to the same-origin proxy. Parity-tested against the OpenAPI document. */
export const ROUTES = {
 submitBatch: { method: "POST", path: "/sync/batches" },
 cursor: { method: "GET", path: "/devices/{id}/cursor" },
 contracts: { method: "GET", path: "/contracts" },
 heads: { method: "GET", path: "/heads" },
 conflicts: { method: "GET", path: "/conflicts" },
 conflict: { method: "GET", path: "/conflicts/{id}" },
 resolve: { method: "POST", path: "/conflicts/{id}/resolution" },
 commands: { method: "GET", path: "/commands" },
 reconciliations: { method: "GET", path: "/reconciliations" }
} as const;

export const COMMAND_FIELDS = ["commandId", "seq", "operationType", "recordType", "recordKey", "baseVersion", "baseEpoch", "fields", "payload", "createdLocalAt", "expiresAt"] as const;
export const BATCH_FIELDS = ["deviceId", "commands"] as const;
export const RESOLUTION_FIELDS = ["expectedVersion", "resolution", "reasonCode", "note", "expectedHeadVersion"] as const;
export const REASON_CODES = ["CLINICAL_REVIEW", "DUPLICATE_ENTRY", "SUPERSEDED_BY_NEWER", "DATA_ERROR", "OTHER"] as const;

export type CommandIn = {
 commandId: string; seq: number; operationType: string; recordType: string; recordKey: string; baseVersion: number; baseEpoch: number;
 fields: string[]; payload: Record<string, unknown>; createdLocalAt: string; expiresAt: string;
};
export type ServerStatus = "APPLIED" | "CONFLICT" | "REJECTED" | "EXPIRED" | "DUPLICATE" | "RETRY" | "INVALID" | "REJECTED_UNSTORED" | "NOT_PROCESSED";
export const SERVER_STATUSES: readonly ServerStatus[] = ["APPLIED", "CONFLICT", "REJECTED", "EXPIRED", "DUPLICATE", "RETRY", "INVALID", "REJECTED_UNSTORED", "NOT_PROCESSED"];
export type CommandResult = { commandId: string | null; seq: number | null; status: ServerStatus; code: string; appliedVersion: number | null; conflictId: string | null; originalStatus?: string };
export type BatchResult = { deviceId: string; results: CommandResult[]; cursor: number; serverTime: string };

/** Local lifecycle of a queued command (package 14). */
export type LocalState = "QUEUED" | "SUBMITTED" | "APPLIED" | "DUPLICATE" | "CONFLICTED" | "DENIED" | "EXPIRED";
export const TERMINAL_STATES: readonly LocalState[] = ["APPLIED", "DUPLICATE", "CONFLICTED", "DENIED", "EXPIRED"];
export type QueueEntry = {
 seq: number; commandId: string; operationType: string; recordType: string; recordKeyHash: string; baseVersion: number; baseEpoch: number;
 state: LocalState; attempts: number; code: string | null; appliedVersion: number | null; conflictId: string | null;
 createdLocalAt: string; expiresAt: string; updatedAt: string; bytes: number;
 command: CommandIn | null;                                  // the clinical payload; purged as soon as the entry is terminal (data minimisation)
};

export type DeviceBinding = { deviceId: string; userId: string; scope: string };
export type Session = { userId: string; scope: string; expiresAt: number };
export type Clock = () => number;
