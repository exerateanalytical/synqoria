// Allow-list for the M26 browser-to-service proxy. Pure functions (no Next imports) so they are unit-tested.
const uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const re = (p: string) => new RegExp("^" + p + "$");

const GET_ROUTES = [
 "worklist", `worklist/${uuid}`, "orders", `orders/${uuid}`, `orders/${uuid}/history`,
 "batches", `batches/${uuid}`, "trays", "trays/awaiting", `trays/${uuid}`, "incidents"
].map(re);
const POST_ROUTES = [
 "orders", `orders/${uuid}/(?:approve|amend|cancel|revoke)`,
 "batches", `batches/${uuid}/(?:release|prepared|dispatch-approval|cancel)`,
 "substitutions", `substitutions/${uuid}/decision`,
 "trays", `trays/${uuid}/(?:dispatch|deliver|acknowledge|return|exceptions)`,
 `incidents/${uuid}/(?:escalate|resolve)`
].map(re);

/** The browser never reaches the feed endpoints: those belong to the integration principal only. */
export function isAllowed(method: string, path: string): boolean {
 if (method === "GET") return GET_ROUTES.some(r => r.test(path));
 if (method === "POST") return POST_ROUTES.some(r => r.test(path));
 return false;
}
export function isSafeSegments(parts: unknown): parts is string[] {
 return Array.isArray(parts) && parts.length >= 1 && parts.length <= 4 &&
  parts.every(p => typeof p === "string" && p.length > 0 && p.length <= 80 && !p.includes("%") && !p.includes(".."));
}
const QUERY_KEYS = new Set([
 "ward", "mealStatus", "orderStatus", "allergy", "fasting", "exception", "q", "sort", "dir", "page", "size", "patientRef", "status"
]);
export function safeQuery(search: URLSearchParams): URLSearchParams | null {
 const out = new URLSearchParams();
 for (const [k, v] of search.entries()) {
  if (!QUERY_KEYS.has(k) || v.length > 80 || /[\u0000-\u001f]/.test(v)) return null;
  out.append(k, v);
 }
 return out;
}
