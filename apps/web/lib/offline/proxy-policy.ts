// Allow-list for the M38 browser-to-service proxy. Pure functions (no Next imports) so they are unit-tested.
// Device uploads (sync/batches), authoritative head publication, contract authoring and device registration are
// service-to-service or administrative and are NOT reachable from a browser.
const uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const re = (p: string) => new RegExp("^" + p + "$");
const GET_ROUTES = ["status", "devices", "heads", "contracts", "commands", `commands/${uuid}`, "conflicts", `conflicts/${uuid}`, "transfers", "reconciliations"].map(re);
const POST_ROUTES = [
 `devices/${uuid}/revocation`, `conflicts/${uuid}/resolution`, "transfers", `transfers/${uuid}/freeze`, `transfers/${uuid}/fence`,
 `transfers/${uuid}/decision`, `transfers/${uuid}/completion`, `transfers/${uuid}/abort`
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
const QUERY_KEYS = new Set(["status", "deviceId", "outcome", "page", "size"]);
export function safeQuery(search: URLSearchParams): URLSearchParams | null {
 const out = new URLSearchParams();
 for (const [k, v] of search.entries()) {
  if (!QUERY_KEYS.has(k) || v.length > 80 || /[\u0000-\u001f]/.test(v)) return null;
  out.append(k, v);
 }
 return out;
}
