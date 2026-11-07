// Same-origin transport to the M38 proxy. Never throws for expected failures; classifies them so the sync engine can decide.
import { MAX_BATCH, ROUTES } from "./types.ts";
import type { CommandIn } from "./types.ts";

export type FetchLike = (url: string, init: { method: string; headers: Record<string, string>; body?: string; credentials: "same-origin"; cache: "no-store"; signal?: AbortSignal }) => Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;
export type TransportConfig = { fetch: FetchLike; basePath?: string; csrf: () => string; timeoutMs?: number };
export type Failure = { ok: false; kind: "network" | "unauthorized" | "forbidden" | "notfound" | "conflict" | "invalid" | "unavailable"; status: number; code: string };
export type Success<T> = { ok: true; status: number; data: T };
export type Result<T> = Success<T> | Failure;
export const MAX_REQUEST_BYTES = 1_048_576;

export function classify(status: number): Failure["kind"] {
 if (status === 401) return "unauthorized";
 if (status === 403) return "forbidden";
 if (status === 404) return "notfound";
 if (status === 409) return "conflict";
 if (status === 400 || status === 422) return "invalid";
 return "unavailable";
}

export class Transport {
 private cfg: TransportConfig;
 constructor(cfg: TransportConfig) { this.cfg = cfg; }
 private url(path: string, query?: Record<string, string | number>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) q.set(k, String(v));
  const s = q.toString();
  return (this.cfg.basePath ?? "/api/offline/proxy") + path.replace(/^\/+/, "/") + (s ? "?" + s : "");
 }
 async call<T>(method: "GET" | "POST", path: string, opts: { query?: Record<string, string | number>; body?: unknown; key?: string } = {}): Promise<Result<T>> {
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: string | undefined;
  if (method === "POST") {
   body = JSON.stringify(opts.body ?? {});
   if (body.length > MAX_REQUEST_BYTES) return { ok: false, kind: "invalid", status: 0, code: "request_too_large" };   // payload bound enforced before the wire
   headers["Content-Type"] = "application/json";
   headers["Idempotency-Key"] = opts.key ?? globalThis.crypto.randomUUID();
   headers["x-synq-ops-csrf"] = this.cfg.csrf();
  }
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), this.cfg.timeoutMs ?? 20_000);
  try {
   const res = await this.cfg.fetch(this.url(path, opts.query), { method, headers, body, credentials: "same-origin", cache: "no-store", signal: ctl.signal });
   let json: unknown = null;
   try { json = await res.json(); } catch { /* non-JSON error bodies are treated as unavailable */ }
   if (res.ok) return { ok: true, status: res.status, data: json as T };
   const code = typeof (json as { error?: unknown } | null)?.error === "string" ? (json as { error: string }).error : "unavailable";
   return { ok: false, kind: classify(res.status), status: res.status, code };
  } catch {
   return { ok: false, kind: "network", status: 0, code: "network" };
  } finally { clearTimeout(timer); }
 }
 submitBatch(deviceId: string, commands: CommandIn[], batchKey: string) {
  if (commands.length < 1 || commands.length > MAX_BATCH) return Promise.resolve<Failure>({ ok: false, kind: "invalid", status: 0, code: "invalid_batch" });
  return this.call<unknown>(ROUTES.submitBatch.method, ROUTES.submitBatch.path, { body: { deviceId, commands }, key: batchKey });
 }
 cursor(deviceId: string) { return this.call<{ deviceId: string; cursor: number; status: string }>("GET", ROUTES.cursor.path.replace("{id}", deviceId)); }
}
