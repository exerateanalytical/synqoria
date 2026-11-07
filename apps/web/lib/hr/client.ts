// Typed browser client for the M24 proxy. Never throws for expected failures: it classifies them so
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
 return "/api/hr/proxy/" + path + (s ? "?" + s : "");
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
  const code = typeof json?.error === "string" ? json.error : "hr_unavailable";
  const kind = code === "hr_not_configured" ? "notconfigured" : classify(res.status);
  return { ok: false, kind, status: res.status, code, details: Array.isArray(json?.details) ? json.details.map(String) : [] };
 } catch (e) {
  if ((e as Error)?.name === "AbortError") return { ok: false, kind: "unavailable", status: 0, code: "aborted", details: [] };
  return { ok: false, kind: typeof navigator !== "undefined" && navigator.onLine === false ? "offline" : "unavailable",
   status: 0, code: "hr_unavailable", details: [] };
 }
}


export type Page<T> = { items: T[]; page: number; size: number; total: number };
/** Money is always a decimal STRING; never a JavaScript number. */
export function validMoney(s: string, maxDecimals = 4): boolean {
 return new RegExp("^[0-9]{1,14}([.][0-9]{1," + maxDecimals + "})?$").test(s.trim());
}
export function parseJsonObject(text: string, maxBytes = 400_000): { value: Record<string, unknown> | null; problem: string | null } {
 if (text.length > maxBytes) return { value: null, problem: "too_large" };
 try { const v = JSON.parse(text); return v && typeof v === "object" && !Array.isArray(v) ? { value: v, problem: null } : { value: null, problem: "not_an_object" }; }
 catch { return { value: null, problem: "invalid_json" }; }
}
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
/** Exact decimal display: groups digits without converting to a float. */
export function showMoney(s: string | number | null | undefined, lang: string): string {
 if (s === null || s === undefined) return "—";
 const [int, frac] = String(s).split(".");
 const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, lang === "fr" ? " " : ",");
 return frac && /[1-9]/.test(frac) ? `${grouped}${lang === "fr" ? "," : "."}${frac.replace(/0+$/, "")}` : grouped;
}
