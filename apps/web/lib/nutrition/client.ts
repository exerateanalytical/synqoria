// Typed browser client for the M26 proxy. Never throws for expected failures: it classifies them so
// every screen renders loading / empty / error / offline / unauthorized / forbidden states explicitly.
export type FailureKind =
 "unauthorized" | "forbidden" | "notfound" | "conflict" | "invalid" | "offline" | "unavailable" | "notconfigured";
export type Failure = { ok: false; kind: FailureKind; status: number; code: string; details: string[] };
export type Success<T> = { ok: true; status: number; data: T };
export type Result<T> = Success<T> | Failure;

export function classify(status: number): FailureKind {
 if (status === 401) return "unauthorized";
 if (status === 403) return "forbidden";
 if (status === 404) return "notfound";
 if (status === 409) return "conflict";
 if (status === 400 || status === 422) return "invalid";
 return "unavailable";
}

const CSRF_COOKIE = "__Host-synq_ops_csrf";
function csrf(): string {
 if (typeof document === "undefined") return "";
 const pair = document.cookie.split("; ").find(x => x.startsWith(CSRF_COOKIE + "="));
 return pair ? decodeURIComponent(pair.slice(CSRF_COOKIE.length + 1)) : "";
}
export function newKey(): string {
 return globalThis.crypto.randomUUID();
}
export function buildPath(path: string, query?: Record<string, string | number | boolean | undefined | null>): string {
 const q = new URLSearchParams();
 for (const [k, v] of Object.entries(query ?? {})) if (v !== undefined && v !== null && v !== "") q.set(k, String(v));
 const s = q.toString();
 return "/api/nutrition/proxy/" + path + (s ? "?" + s : "");
}

export async function call<T>(method: "GET" | "POST", path: string, opts?: {
 query?: Record<string, string | number | boolean | undefined | null>; body?: unknown; key?: string; signal?: AbortSignal;
}): Promise<Result<T>> {
 if (typeof navigator !== "undefined" && navigator.onLine === false)
  return { ok: false, kind: "offline", status: 0, code: "offline", details: [] };
 try {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method === "POST") {
   headers["Content-Type"] = "application/json";
   headers["Idempotency-Key"] = opts?.key ?? newKey();
   headers["x-synq-ops-csrf"] = csrf();
  }
  const res = await fetch(buildPath(path, opts?.query), {
   method, headers, credentials: "same-origin", cache: "no-store", signal: opts?.signal,
   body: method === "POST" ? JSON.stringify(opts?.body ?? {}) : undefined
  });
  let json: any = null;
  try { json = await res.json(); } catch { /* non-JSON error bodies are treated as unavailable */ }
  if (res.ok) return { ok: true, status: res.status, data: json as T };
  const code = typeof json?.error === "string" ? json.error : "nutrition_unavailable";
  const kind = code === "nutrition_not_configured" ? "notconfigured" : classify(res.status);
  return { ok: false, kind, status: res.status, code, details: Array.isArray(json?.details) ? json.details.map(String) : [] };
 } catch (e) {
  if ((e as Error)?.name === "AbortError") return { ok: false, kind: "unavailable", status: 0, code: "aborted", details: [] };
  return { ok: false, kind: typeof navigator !== "undefined" && navigator.onLine === false ? "offline" : "unavailable",
   status: 0, code: "nutrition_unavailable", details: [] };
 }
}

// ---- response shapes used by the screens (subset of contracts/openapi/synqoria-m26-nutrition-v1.yaml)
export type Page<T> = { items: T[]; page: number; size: number; total: number };
export type WorklistRow = {
 patientRef: string; patientLabel: string; wardCode: string; bedCode: string; orderId: string | null; orderStatus: string | null;
 dietCategory: string | null; texture: string | null; restrictions: string[] | null; excludedAllergens: string[] | null;
 allergenCodes: string[] | null; noKnownAllergies: boolean | null; allergyVerifiedAt: string | null;
 fastingReason: string | null; fastingUntil: string | null; mealId: string | null; mealStatus: string | null; mealType: string | null;
 mealUpdatedAt: string | null; holdReason: string | null; openIncidents: number; hasException: boolean; exceptions: string[];
};
export type Order = {
 id: string; patientRef: string; clinicalOrderRef: string; status: string; dietCategory: string; texture: string;
 restrictions: string[]; excludedAllergens: string[]; mealTiming: string[]; specialInstructions: string;
 effectiveFrom: string; effectiveTo: string | null; prescriberId: string; approverId: string | null; approvedAt: string | null;
 reconciledAllergyVersion: number | null; supersedesId: string | null; statusReason: string | null; version: number;
 createdAt: string; updatedAt: string;
};
export type AuditEvent = {
 eventId: string; action: string; fromStatus: string | null; toStatus: string; aggregateVersion: number; actorId: string;
 details: Record<string, unknown>; occurredAt: string;
};
export type BatchLine = {
 id: string; patientRef: string; patientLabel: string; bedCode: string; dietCategory: string; texture: string;
 status: string; holdReason: string | null; version: number;
};
export type Substitution = {
 id: string; mealServiceId: string; originalItemCode: string; substituteItemCode: string; substituteAllergens: string[];
 reasonCode: string; status: string; proposedBy: string; decidedBy: string | null; version: number;
};
export type Batch = {
 id: string; wardCode: string; mealDate: string; mealType: string; status: string; cutoffAt: string; plannedBy: string;
 preparedBy: string | null; dispatchApprovedBy: string | null; portions: Record<string, number>;
 exceptions: { patientRef: string; codes: string[] }[]; lines?: BatchLine[]; substitutions?: Substitution[]; version: number;
 updatedAt: string;
};
export type Incident = {
 id: string; mealServiceId: string; trayId: string | null; incidentType: string; severity: string; status: string;
 description: string; reporterId: string; escalatedToRole: string | null; resolvedBy: string | null; version: number; createdAt: string;
};
export type TrayEvent = { eventId: string; action: string; toStatus: string; actorId: string; occurredAt: string };
export type Tray = {
 id: string; trayCode: string; status: string; version: number; patientRef: string; patientLabel: string; wardCode: string; bedCode: string;
 mealType: string; dietCategory: string; texture: string; mealStatus: string; holdReason?: string | null; updatedAt: string;
 loadedBy?: string; dispatchedBy?: string | null; dispatchedAt?: string | null; deliveredBy?: string | null; deliveredAt?: string | null;
 identityMethod?: string | null; acknowledgedBy?: string | null; acknowledgedAt?: string | null;
 incidents?: Incident[]; events?: TrayEvent[];
};
export type AwaitingMeal = {
 id: string; patientRef: string; patientLabel: string; wardCode: string; bedCode: string; mealType: string; dietCategory: string; texture: string;
};

/** Mirrors the server's diet contradiction rules so the form can explain a problem before submission. */
export function dietProblem(diet: string, texture: string, meals: string[], restrictions: string[]): string | null {
 const nbm = diet === "NIL_BY_MOUTH";
 if (nbm !== (texture === "NONE")) return "diet_contradictory";
 if (!nbm && (diet === "CLEAR_LIQUID" || diet === "FULL_LIQUID") !== (texture === "LIQUIDISED")) return "diet_contradictory";
 if (diet === "PUREED" && texture !== "PUREED") return "diet_contradictory";
 if (nbm && meals.length > 0) return "diet_contradictory";
 if (!nbm && meals.length === 0) return "meal_timing_required";
 const r = new Set(restrictions);
 for (const pair of [["FLUID_RESTRICTED", "HIGH_FLUID"], ["LOW_POTASSIUM", "HIGH_POTASSIUM"], ["LOW_PROTEIN", "HIGH_PROTEIN"], ["VEGAN", "CONTAINS_ANIMAL_PRODUCT"]])
  if (pair.every(x => r.has(x))) return "diet_contradictory";
 return null;
}
export function parseCodes(text: string): string[] {
 return [...new Set(text.split(/[\s,;]+/).map(s => s.trim().toUpperCase()).filter(Boolean))];
}
export function validCodes(codes: string[], max: number): boolean {
 return codes.length <= max && codes.every(c => /^[A-Z0-9_]{2,40}$/.test(c));
}
