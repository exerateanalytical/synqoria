// Server-side verification of the operations access token for the M38 device session adapter. FAIL CLOSED by design.
// STATUS: engineering adapter pending the integration lead's P03 identity contract and dependency approval (a vetted JOSE library or the shared verifier
// replaces verifyAccessToken without changing callers). It is OFF unless SYNQORIA_OFFLINE_SESSION_VERIFIER=jwks is configured, and every error, missing key,
// unreachable issuer, unexpected algorithm or odd claim results in "no session". No token is ever returned to the browser.
import {createPublicKey, createVerify} from "node:crypto";

export type VerifiedSession = {userId: string; scope: string; expiresAt: number};
export type Jwks = {keys: Array<Record<string, unknown>>};
export type VerifyConfig = {issuer: string; audience: string; jwks: () => Promise<Jwks>; nowMs: () => number; skewSec?: number};
export type VerifyResult = {ok: true; session: VerifiedSession} | {ok: false; code: string};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_GRANT_SEC = 900;
const fail = (code: string): VerifyResult => ({ok: false, code});

export async function verifyAccessToken(token: string, cfg: VerifyConfig): Promise<VerifyResult> {
 if (typeof token !== "string" || token.length < 80 || token.length > 16000) return fail("bad_token");
 const parts = token.split(".");
 if (parts.length !== 3 || parts.some(p => !/^[A-Za-z0-9_-]+$/.test(p))) return fail("bad_token");
 let header: Record<string, unknown>, claims: Record<string, unknown>;
 try {
  header = JSON.parse(Buffer.from(parts[0]!, "base64url").toString("utf8"));
  claims = JSON.parse(Buffer.from(parts[1]!, "base64url").toString("utf8"));
 } catch { return fail("bad_token"); }
 if (header.alg !== "RS256") return fail("alg_not_allowed");                         // allow-list: never "none", never HS*, never a key-confusion algorithm
 if (typeof header.kid !== "string" || header.kid.length > 128) return fail("kid_required");
 let set: Jwks;
 try { set = await cfg.jwks(); } catch { return fail("jwks_unavailable"); }
 const jwk = (set.keys ?? []).find(k => k.kid === header.kid && k.kty === "RSA" && (k.use === undefined || k.use === "sig") && (k.alg === undefined || k.alg === "RS256"));
 if (!jwk) return fail("unknown_key");
 try {
  const pub = createPublicKey({key: jwk as never, format: "jwk"});
  if (!createVerify("RSA-SHA256").update(parts[0] + "." + parts[1]).verify(pub, new Uint8Array(Buffer.from(parts[2]!, "base64url")))) return fail("bad_signature");
 } catch { return fail("bad_signature"); }

 const now = Math.floor(cfg.nowMs() / 1000), skew = cfg.skewSec ?? 30;
 if (claims.iss !== cfg.issuer) return fail("bad_issuer");
 const aud = claims.aud;
 if (!(aud === cfg.audience || (Array.isArray(aud) && aud.includes(cfg.audience)))) return fail("bad_audience");
 if (typeof claims.exp !== "number" || claims.exp <= now - skew) return fail("token_expired");
 if (claims.nbf !== undefined && (typeof claims.nbf !== "number" || claims.nbf > now + skew)) return fail("token_not_yet_valid");
 if (typeof claims.sub !== "string" || !UUID.test(claims.sub)) return fail("bad_subject");
 if (typeof claims.synqoria_cell !== "string" || !UUID.test(claims.synqoria_cell) || typeof claims.synqoria_facility !== "string" || !UUID.test(claims.synqoria_facility)) return fail("bad_scope");
 const perms = claims.synqoria_permissions;
 if (!Array.isArray(perms) || perms.length === 0 || perms.length > 64 || perms.some(p => typeof p !== "string")) return fail("bad_grants");
 const grant = claims.synqoria_grant_expires;
 if (typeof grant !== "number" || grant <= now || grant > now + MAX_GRANT_SEC) return fail("grant_expired");
 return {ok: true, session: {userId: claims.sub, scope: `${claims.synqoria_cell}/${claims.synqoria_facility}`, expiresAt: Math.min(claims.exp, grant) * 1000}};
}

/** JWKS discovery with strict bounds: https only, same origin as the issuer, short timeout, size cap, 5-minute cache. */
export function makeJwksLoader(issuer: string, fetchImpl: typeof fetch = fetch, nowMs: () => number = Date.now) {
 let cache: {at: number; jwks: Jwks} | null = null;
 return async (): Promise<Jwks> => {
  if (cache && nowMs() - cache.at < 300_000) return cache.jwks;
  const base = new URL(issuer);
  if (base.protocol !== "https:") throw new Error("issuer_not_https");
  const get = async (url: string) => {
   const u = new URL(url);
   if (u.origin !== base.origin) throw new Error("cross_origin_discovery");
   const r = await fetchImpl(u, {redirect: "error", cache: "no-store", signal: AbortSignal.timeout(4000)});
   const text = await r.text();
   if (!r.ok || text.length > 262144) throw new Error("discovery_failed");
   return JSON.parse(text) as Record<string, unknown>;
  };
  const doc = await get(base.origin + "/.well-known/openid-configuration");
  if (doc.issuer !== issuer || typeof doc.jwks_uri !== "string") throw new Error("bad_discovery");
  const jwks = await get(doc.jwks_uri) as Jwks;
  if (!Array.isArray(jwks.keys)) throw new Error("bad_jwks");
  cache = {at: nowMs(), jwks};
  return jwks;
 };
}
/** The only mode that can ever return a session. Anything else (unset, misspelled, partially configured) is disabled. */
export function verifierMode(env: Record<string, string | undefined>): "jwks" | "disabled" {
 return env.SYNQORIA_OFFLINE_SESSION_VERIFIER === "jwks" && !!env.SYNQORIA_OFFLINE_OIDC_AUDIENCE ? "jwks" : "disabled";
}
