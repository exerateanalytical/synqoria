// Allow-list for the M28 browser-to-service proxy. Pure functions (no Next imports) so they are unit-tested.
// Ingestion, source management, rebuild and quarantine are service-to-service concerns and are NOT reachable from a browser.
const uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const re = (p: string) => new RegExp("^" + p + "$");
const GET_ROUTES = [
 "freshness", "metrics", "metric-values", "dashboard", "cell-aggregates", "reports", "runs", `runs/${uuid}`, "exports"
].map(re);
const POST_ROUTES = [
 `reports/${uuid}/runs`, `runs/${uuid}/exports`, `exports/${uuid}/decision`, `exports/${uuid}/download`, "runs/process"
].map(re);
export function isAllowed(method: string, path: string): boolean {
 if (method === "GET") return GET_ROUTES.some(r => r.test(path));
 if (method === "POST") return POST_ROUTES.some(r => r.test(path));
 return false;
}
export function isSafeSegments(parts: unknown): parts is string[] {
 return Array.isArray(parts) && parts.length >= 1 && parts.length <= 3 &&
  parts.every(p => typeof p === "string" && p.length > 0 && p.length <= 80 && !p.includes("%") && !p.includes(".."));
}
const QUERY_KEYS = new Set(["domain", "metricKey", "from", "to", "status", "page", "size", "limit"]);
export function safeQuery(search: URLSearchParams): URLSearchParams | null {
 const out = new URLSearchParams();
 for (const [k, v] of search.entries()) {
  if (!QUERY_KEYS.has(k) || v.length > 80 || /[\u0000-\u001f]/.test(v)) return null;
  out.append(k, v);
 }
 return out;
}
