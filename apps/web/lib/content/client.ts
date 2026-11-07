// Typed browser client for the M30 proxy. Never throws for expected failures: it classifies them so
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
 return "/api/content/proxy/" + path + (s ? "?" + s : "");
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
  const code = typeof json?.error === "string" ? json.error : "content_unavailable";
  const kind = code === "content_not_configured" ? "notconfigured" : classify(res.status);
  return { ok: false, kind, status: res.status, code, details: Array.isArray(json?.details) ? json.details.map(String) : [] };
 } catch (e) {
  if ((e as Error)?.name === "AbortError") return { ok: false, kind: "unavailable", status: 0, code: "aborted", details: [] };
  return { ok: false, kind: typeof navigator !== "undefined" && navigator.onLine === false ? "offline" : "unavailable",
   status: 0, code: "content_unavailable", details: [] };
 }
}


export type Page<T> = { items: T[]; page: number; size: number; total: number };
export function parseCodes(text: string): string[] {
 return [...new Set(text.split(/[\s,;]+/).map(s => s.trim().toUpperCase()).filter(Boolean))];
}
export function validCodes(codes: string[], max: number): boolean {
 return codes.length <= max && codes.every(c => /^[A-Z0-9_]{2,40}$/.test(c));
}
export async function sha256Hex(text: string): Promise<string> {
 const bytes = new TextEncoder().encode(text);
 const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes);
 return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, "0")).join("");
}
export type ImportConcept = { code: string; status: string; displayEn: string; displayFr?: string | null; parent?: string | null; synonyms?: string[] };
/** Same canonical checksum as the server: concepts sorted by code, one pipe-delimited line each, joined by LF. */
export async function contentChecksum(concepts: ImportConcept[]): Promise<string> {
 const sorted = [...concepts].sort((a, b) => (a.code < b.code ? -1 : a.code > b.code ? 1 : 0));
 return sha256Hex(sorted.map(c => `${c.code}|${c.status}|${c.displayEn}|${c.displayFr ?? ""}|${c.parent ?? ""}`).join("\n"));
}
/** Parses a CSV (code,status,displayEn,displayFr,parent,synonyms separated by ;) with RFC-4180 quoting. Returns problems, never throws. */
export function parseConceptCsv(text: string): { concepts: ImportConcept[]; problems: string[] } {
 const rows: string[][] = [];
 let row: string[] = [], cell = "", quoted = false;
 const NL = String.fromCharCode(10), CR = String.fromCharCode(13);
 for (let i = 0; i < text.length; i++) {
  const ch = text[i];
  if (quoted) { if (ch === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else quoted = false; } else cell += ch; }
  else if (ch === '"') quoted = true;
  else if (ch === ",") { row.push(cell); cell = ""; }
  else if (ch === NL || ch === CR) { if (ch === CR && text[i + 1] === NL) i++; row.push(cell); cell = ""; if (row.some(x => x.length)) rows.push(row); row = []; }
  else cell += ch;
 }
 row.push(cell); if (row.some(x => x.length)) rows.push(row);
 const problems: string[] = [];
 if (quoted) problems.push("UNTERMINATED_QUOTE");
 const body = rows[0]?.[0]?.toLowerCase() === "code" ? rows.slice(1) : rows;
 if (body.length > 50000) problems.push("IMPORT_TOO_LARGE");
 const concepts = body.map((r, i) => {
  if (r.length < 3 || r.length > 6) problems.push(`BAD_COLUMNS:line ${i + 1}`);
  return { code: (r[0] ?? "").trim(), status: (r[1] ?? "").trim().toUpperCase(), displayEn: (r[2] ?? "").trim(),
   displayFr: r[3]?.trim() || null, parent: r[4]?.trim() || null, synonyms: r[5] ? r[5].split(";").map(s => s.trim()).filter(Boolean) : [] };
 });
 return { concepts, problems };
}
/** Pretty JSON with a hard size ceiling and a safe parse. */
export function parseJsonObject(text: string, maxBytes = 400_000): { value: Record<string, unknown> | null; problem: string | null } {
 if (text.length > maxBytes) return { value: null, problem: "definition_too_large" };
 try { const v = JSON.parse(text); return v && typeof v === "object" && !Array.isArray(v) ? { value: v, problem: null } : { value: null, problem: "not_an_object" }; }
 catch { return { value: null, problem: "invalid_json" }; }
}
