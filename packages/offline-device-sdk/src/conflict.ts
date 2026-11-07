// Conflict handling on the device. The server's outcome is shown verbatim (code and status); the device never reinterprets or auto-resolves.
// Resolution requests are built deterministically from the server's own conflict view and are refused locally when the reviewer wrote the change.
import { REASON_CODES } from "./types.ts";

export type ConflictView = { id: string; kind: string; status: string; authorId: string; version: number; currentHeadVersion: number; currentEpoch: number; authoritativeEpoch: number };
export type ResolutionRequest = { expectedVersion: number; resolution: "ACCEPT_LOCAL" | "KEEP_AUTHORITATIVE"; reasonCode: string; note: string | null; expectedHeadVersion: number };
export type Built = { ok: true; request: ResolutionRequest } | { ok: false; code: string };

export function buildResolution(c: ConflictView, decision: ResolutionRequest["resolution"], reasonCode: string, note: string | null, reviewerId: string): Built {
 if (c.status !== "OPEN") return { ok: false, code: "invalid_transition" };
 if (reviewerId === c.authorId) return { ok: false, code: "resolver_must_be_independent" };            // the server enforces it too; refusing early saves a round trip
 if (!(REASON_CODES as readonly string[]).includes(reasonCode)) return { ok: false, code: "reason_required" };
 if (reasonCode === "OTHER" && (note ?? "").trim().length < 5) return { ok: false, code: "reason_required" };
 if (decision === "ACCEPT_LOCAL" && c.currentEpoch !== c.authoritativeEpoch) return { ok: false, code: "epoch_changed" };   // ownership moved: re-detect, never accept across epochs
 return { ok: true, request: { expectedVersion: c.version, resolution: decision, reasonCode, note: note === null ? null : note.trim(), expectedHeadVersion: c.currentHeadVersion } };
}
/** The exact server vocabulary is presented to the user; unknown codes are shown as-is rather than guessed. */
export function presentOutcome(status: string, code: string): { status: string; code: string; i18nKey: string } {
 return { status, code, i18nKey: "code." + code };
}
