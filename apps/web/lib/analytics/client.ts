// Typed browser client for the M28 proxy. Never throws for expected failures: it classifies them so
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
 return "/api/analytics/proxy/" + path + (s ? "?" + s : "");
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
  const code = typeof json?.error === "string" ? json.error : "analytics_unavailable";
  const kind = code === "analytics_not_configured" ? "notconfigured" : classify(res.status);
  return { ok: false, kind, status: res.status, code, details: Array.isArray(json?.details) ? json.details.map(String) : [] };
 } catch (e) {
  if ((e as Error)?.name === "AbortError") return { ok: false, kind: "unavailable", status: 0, code: "aborted", details: [] };
  return { ok: false, kind: typeof navigator !== "undefined" && navigator.onLine === false ? "offline" : "unavailable",
   status: 0, code: "analytics_unavailable", details: [] };
 }
}


export type Page<T> = { items: T[]; page: number; size: number; total: number };
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export type ValueRow = {
 id: string; metricKey: string; metricVersion: number; nameEn?: string; nameFr?: string; domain: string; unit: string; periodStart: string; periodEnd: string;
 dimensionKey: string; currency: string | null; state: string; numerator: string | number | null; denominator: string | number | null; value: string | number | null;
 cohortSize: number; unknownCount: number; sourceWatermark: string | null; stale: boolean; computedAt: string;
};
/** Decimal text -> scaled BigInt (6 places) without ever touching a binary float. */
function scaled(text: string): bigint | null {
 const m = /^(-?)(\d{1,18})(?:\.(\d{1,12}))?$/.exec(text.trim());
 if (!m) return null;
 const frac = (m[3] ?? "").padEnd(6, "0").slice(0, 6);
 const v = BigInt(m[2]) * 1000000n + BigInt(frac || "0");
 return m[1] === "-" ? -v : v;
}
/** 0.700000 -> "70.0 %" using integer arithmetic only. */
export function percentText(value: string | number | null | undefined, lang: string): string {
 if (value === null || value === undefined) return "—";
 const s = scaled(String(value));
 if (s === null) return "—";
 const tenths = (s + 500n) / 1000n;                           // value x 100 in tenths of a percent, half-up for non-negative values
 const whole = tenths / 10n, dec = (tenths < 0n ? -tenths : tenths) % 10n;
 return `${whole}${lang === "fr" ? "," : "."}${dec}${lang === "fr" ? " %" : "%"}`;
}
export function plainText(value: string | number | null | undefined, lang: string): string {
 if (value === null || value === undefined) return "—";
 const [int, frac] = String(value).split(".");
 const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, lang === "fr" ? " " : ",");
 return frac && /[1-9]/.test(frac) ? `${grouped}${lang === "fr" ? "," : "."}${frac.replace(/0+$/, "")}` : grouped;
}
/** Shows a value according to its unit; suppressed / empty rows never show a number. */
export function showValue(row: { state: string; unit: string; value: string | number | null; numerator: string | number | null }, lang: string): string {
 if (row.state === "SUPPRESSED" || row.state === "NO_DATA") return "—";
 const v = row.unit === "COUNT" || row.unit === "AMOUNT" || row.unit === "MINUTES" ? (row.value ?? row.numerator) : row.value;
 if (v === null || v === undefined) return "—";
 return row.unit === "PERCENT" || row.unit === "RATIO" ? percentText(v, lang) : plainText(v, lang);
}
/** Series for the chart: the ALL dimension per period, numeric positions only (a chart position is not money). */
export function seriesPoints(rows: ValueRow[], currency: string | null): { label: string; y: number | null; state: string }[] {
 return rows.filter(r => r.dimensionKey === "ALL" && (currency === null || (r.currency ?? "").trim() === currency))
  .sort((a, b) => (a.periodStart < b.periodStart ? -1 : 1))
  .map(r => ({ label: r.periodStart, y: r.state === "OK" || r.state === "INCOMPLETE" ? Number(r.value ?? r.numerator) : null, state: r.state }));
}
