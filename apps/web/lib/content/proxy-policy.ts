// Allow-list for the M30 browser-to-service proxy. Pure functions (no Next imports) so they are unit-tested.
const uuid = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const re = (p: string) => new RegExp("^" + p + "$");
const GET_ROUTES = [
 "code-systems", `code-systems/${uuid}/versions`, "concepts", `code-system-versions/${uuid}/roots`,
 `code-system-versions/${uuid}/concepts/[A-Za-z0-9._-]{1,64}`, "mappings",
 "formulary", `formulary/${uuid}`, "packages", `packages/${uuid}`,
 "activations/active", "activations", "activations/impact"
].map(re);
const POST_ROUTES = [
 "code-systems", `code-systems/${uuid}/imports`, "mappings", `mappings/${uuid}/decision`,
 "formulary", `formulary/${uuid}/(?:update|submit|withdraw|decision)`,
 "packages", `packages/${uuid}/(?:validate|submit|review|evaluate)`, `approvals/${uuid}/revoke`,
 "activations", `activations/${uuid}/rollback`
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
const QUERY_KEYS = new Set(["systemId", "versionId", "q", "status", "includeInactive", "page", "size", "key", "subjectKey", "subjectType", "subjectId"]);
export function safeQuery(search: URLSearchParams): URLSearchParams | null {
 const out = new URLSearchParams();
 for (const [k, v] of search.entries()) {
  if (!QUERY_KEYS.has(k) || v.length > 100 || /[\u0000-\u001f]/.test(v)) return null;
  out.append(k, v);
 }
 return out;
}
