// The session adapter must be cryptographically verifying and FAIL CLOSED. Exercises lib/offline/identity.ts with real RSA keys.
import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync, createSign, randomUUID} from "node:crypto";
import {readFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const id = await import(pathToFileURL(join(root, "apps/web/lib/offline/identity.ts")).href);
const ISS = "https://idp.test", AUD = "synqoria-offline", NOW = Date.parse("2026-11-07T12:00:00Z");
const {privateKey, publicKey} = generateKeyPairSync("rsa", {modulusLength: 2048});
const other = generateKeyPairSync("rsa", {modulusLength: 2048});
const jwk = {...publicKey.export({format: "jwk"}), kid: "k1", alg: "RS256", use: "sig"};
const cfg = {issuer: ISS, audience: AUD, jwks: async () => ({keys: [jwk]}), nowMs: () => NOW};
const b64 = o => Buffer.from(typeof o === "string" ? o : JSON.stringify(o)).toString("base64url");
const claims = (o = {}) => { const n = Math.floor(NOW / 1000); return {iss: ISS, sub: randomUUID(), aud: AUD, exp: n + 600, nbf: n - 5, synqoria_cell: randomUUID(), synqoria_facility: randomUUID(), synqoria_permissions: ["m38:sync_submit"], synqoria_grant_expires: n + 600, ...o}; };
function mint(c, o = {}) {
 const head = b64({alg: "RS256", typ: "JWT", kid: "k1", ...(o.head ?? {})}), body = b64(c);
 const sig = createSign("RSA-SHA256").update(head + "." + body).sign(o.key ?? privateKey).toString("base64url");
 return head + "." + body + "." + sig;
}
const code = async t => { const r = await id.verifyAccessToken(t, cfg); return r.ok ? "OK" : r.code; };

test("a valid token yields exactly user, scope and the earlier of token/grant expiry", async () => {
 const c = claims();
 const r = await id.verifyAccessToken(mint(c), cfg);
 assert.ok(r.ok);
 assert.deepEqual(r.session, {userId: c.sub, scope: `${c.synqoria_cell}/${c.synqoria_facility}`, expiresAt: Math.min(c.exp, c.synqoria_grant_expires) * 1000});
 assert.equal((await id.verifyAccessToken(mint(claims({aud: ["x", AUD]})), cfg)).ok, true);
});

test("forged, unsigned, re-algorithmed, wrong-issuer, wrong-audience, expired and malformed tokens are all refused", async () => {
 assert.equal(await code(mint(claims(), {key: other.privateKey})), "bad_signature");
 const good = mint(claims()), [h, p] = good.split(".");
 assert.equal(await code(h + "." + b64(claims({sub: randomUUID()})) + "." + good.split(".")[2]), "bad_signature", "payload swapped under an old signature");
 assert.equal(await code(b64({alg: "none", kid: "k1"}) + "." + p + "." + "AAAA".repeat(30)), "alg_not_allowed");
 assert.equal(await code(mint(claims(), {head: {alg: "HS256"}})), "alg_not_allowed");
 assert.equal(await code(mint(claims(), {head: {kid: "other"}})), "unknown_key");
 assert.equal(await code(mint(claims({iss: "https://evil.test"}))), "bad_issuer");
 assert.equal(await code(mint(claims({aud: "someone-else"}))), "bad_audience");
 assert.equal(await code(mint(claims({exp: Math.floor(NOW / 1000) - 120}))), "token_expired");
 assert.equal(await code(mint(claims({nbf: Math.floor(NOW / 1000) + 600}))), "token_not_yet_valid");
 assert.equal(await code(mint(claims({sub: "not-a-uuid"}))), "bad_subject");
 assert.equal(await code(mint(claims({synqoria_cell: "x"}))), "bad_scope");
 assert.equal(await code(mint(claims({synqoria_permissions: []}))), "bad_grants");
 assert.equal(await code(mint(claims({synqoria_grant_expires: Math.floor(NOW / 1000) + 3600}))), "grant_expired", "grants longer than 15 minutes");
 assert.equal(await code(mint(claims({synqoria_grant_expires: Math.floor(NOW / 1000) - 1}))), "grant_expired");
 for (const junk of ["", "a.b", "a.b.c.d", "x".repeat(20000), "a.b.c"]) assert.equal(await code(junk), "bad_token");
});

test("an unreachable or hostile key source fails closed", async () => {
 const t = mint(claims());
 assert.equal((await id.verifyAccessToken(t, {...cfg, jwks: async () => { throw new Error("down"); }})).code, "jwks_unavailable");
 assert.equal((await id.verifyAccessToken(t, {...cfg, jwks: async () => ({keys: []})})).code, "unknown_key");
 const loader = id.makeJwksLoader("http://insecure.test", async () => { throw new Error("must not be called"); });
 await assert.rejects(loader, /issuer_not_https/);
 const responses = {"https://idp.test/.well-known/openid-configuration": {issuer: ISS, jwks_uri: "https://evil.test/jwks"}};
 const cross = id.makeJwksLoader(ISS, async u => ({ok: true, text: async () => JSON.stringify(responses[String(u)])}));
 await assert.rejects(cross, /cross_origin_discovery/);
 const mism = id.makeJwksLoader(ISS, async u => ({ok: true, text: async () => JSON.stringify({issuer: "https://other.test", jwks_uri: "https://idp.test/j"})}));
 await assert.rejects(mism, /bad_discovery/);
});

test("the verifier is off unless explicitly and fully configured; the route never decodes claims itself", () => {
 assert.equal(id.verifierMode({}), "disabled");
 assert.equal(id.verifierMode({SYNQORIA_OFFLINE_SESSION_VERIFIER: "jwks"}), "disabled", "no audience configured");
 assert.equal(id.verifierMode({SYNQORIA_OFFLINE_SESSION_VERIFIER: "yes", SYNQORIA_OFFLINE_OIDC_AUDIENCE: "a"}), "disabled");
 assert.equal(id.verifierMode({SYNQORIA_OFFLINE_SESSION_VERIFIER: "jwks", SYNQORIA_OFFLINE_OIDC_AUDIENCE: "a"}), "jwks");
 const route = readFileSync(join(root, "apps/web/app/api/offline/session/route.ts"), "utf8");
 assert.ok(!/base64url|JSON\.parse|split\("\."\)/.test(route), "the route must not decode tokens itself");
 assert.ok(/identity_verification_not_integrated/.test(route) && route.indexOf("verifierMode") < route.indexOf("cookies.get"), "disabled check happens before the cookie is even read");
});
