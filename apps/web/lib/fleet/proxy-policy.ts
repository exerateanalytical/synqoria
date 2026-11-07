// Allow-list for the M39 browser-to-service proxy. Pure functions (no Next imports) so they are unit-tested.
// Health reports and routing lookups are machine-to-machine (monitoring and gateways) and are NOT reachable from a browser.
const uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const re = (p: string) => new RegExp("^" + p + "$");
const GET_ROUTES = ["tenants", "cells", `cells/${uuid}`, `cells/${uuid}/qualifications`, "provisioning-requests", "routes", "releases", "rollouts", `rollouts/${uuid}`, "migrations", `migrations/${uuid}`].map(re);
const POST_ROUTES = [
 "provisioning-requests", `provisioning-requests/${uuid}/decision`, `provisioning-requests/${uuid}/application`, `provisioning-requests/${uuid}/rollback`,
 `cells/${uuid}/transition`, `cells/${uuid}/qualifications`, `qualifications/${uuid}/checks`, `qualifications/${uuid}/completion`,
 "releases", `releases/${uuid}/decision`, "rollouts", `rollouts/${uuid}/advance`, `rollouts/${uuid}/stop`, `rollouts/${uuid}/cells/${uuid}/evidence`,
 "routes", `routes/${uuid}/switch`, "migrations", `migrations/${uuid}/steps`, `migrations/${uuid}/rollback`
].map(re);
export function isAllowed(method: string, path: string): boolean {
 if (method === "GET") return GET_ROUTES.some(r => r.test(path));
 if (method === "POST") return POST_ROUTES.some(r => r.test(path));
 return false;
}
export function isSafeSegments(parts: unknown): parts is string[] {
 return Array.isArray(parts) && parts.length >= 1 && parts.length <= 4 &&
  parts.every(p => typeof p === "string" && p.length > 0 && p.length <= 80 && !p.includes("%") && !p.includes(".."));
}
const QUERY_KEYS = new Set(["state", "tenantId", "status", "page", "size"]);
export function safeQuery(search: URLSearchParams): URLSearchParams | null {
 const out = new URLSearchParams();
 for (const [k, v] of search.entries()) {
  if (!QUERY_KEYS.has(k) || v.length > 80 || /[\u0000-\u001f]/.test(v)) return null;
  out.append(k, v);
 }
 return out;
}
