// Live rig: the REAL M38 Spring service (built jar) + a REAL PostgreSQL 18 + a local OIDC issuer served over HTTPS (self-signed, trusted only by the service JVM).
// Tokens are genuine RS256 JWTs validated by Spring Security against this issuer's JWKS, with the real claim contract (synqoria_cell, synqoria_facility,
// synqoria_permissions, synqoria_grant_expires). Everything is synthetic. Opt-in: SYNQORIA_LIVE_RIG=1 (see packages/offline-device-sdk/README in the handoff).
import {spawn, execFileSync} from "node:child_process";
import {createServer} from "node:https";
import {generateKeyPairSync, createSign, randomUUID} from "node:crypto";
import {mkdtempSync, readFileSync, readdirSync, writeFileSync} from "node:fs";
import {tmpdir} from "node:os";
import {join, dirname} from "node:path";
import {fileURLToPath} from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "..", "..", "..", "..");
const b64u = b => Buffer.from(b).toString("base64url");
export const ISSUER_PORT = Number(process.env.RIG_ISSUER_PORT ?? 9443), SERVICE_PORT = Number(process.env.RIG_SERVICE_PORT ?? 8099);
export const ISSUER = `https://localhost:${ISSUER_PORT}`, AUDIENCE = "synqoria-offline", BASE = `http://127.0.0.1:${SERVICE_PORT}/api/v1/offline`;

export class Rig {
 constructor() { this.dir = mkdtempSync(join(tmpdir(), "m38-rig-")); this.kid = "rig-key-1"; this.clockSkew = 0; }
 psql(sql) {
  const c = process.env.RIG_PG_CONTAINER;
  return execFileSync("docker", ["exec", "-i", c, "psql", "-U", process.env.RIG_PG_USER ?? "postgres", "-d", process.env.RIG_PG_DB, "-v", "ON_ERROR_STOP=1", "-X", "-q", "-At"], {input: sql, encoding: "utf8"});
 }
 async start() {
  const {privateKey, publicKey} = generateKeyPairSync("rsa", {modulusLength: 2048});
  this.privateKey = privateKey;
  const jwk = {...publicKey.export({format: "jwk"}), kid: this.kid, alg: "RS256", use: "sig"};
  const key = join(this.dir, "k.pem"), cert = join(this.dir, "c.pem"), trust = join(this.dir, "trust.p12");
  execFileSync("openssl", ["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", key, "-out", cert, "-days", "1", "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1"], {stdio: "ignore"});
  const keytool = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "keytool") : "keytool";
  execFileSync(keytool, ["-importcert", "-alias", "rig", "-file", cert, "-keystore", trust, "-storetype", "PKCS12", "-storepass", "changeit", "-noprompt"], {stdio: "ignore"});
  this.http = createServer({key: readFileSync(key), cert: readFileSync(cert)}, (req, res) => {
   res.setHeader("Content-Type", "application/json");
   if (req.url === "/.well-known/openid-configuration") res.end(JSON.stringify({issuer: ISSUER, jwks_uri: `${ISSUER}/jwks.json`, authorization_endpoint: `${ISSUER}/auth`, token_endpoint: `${ISSUER}/token`, subject_types_supported: ["public"], id_token_signing_alg_values_supported: ["RS256"], response_types_supported: ["code"]}));
   else if (req.url === "/jwks.json") res.end(JSON.stringify({keys: [jwk]}));
   else { res.statusCode = 404; res.end("{}"); }
  });
  await new Promise(r => this.http.listen(ISSUER_PORT, "127.0.0.1", r));

  // database: apply the real migration with an admin login, create the restricted runtime role
  const migration = readFileSync(join(repo, "services", "offline-sync-core", "db", "migration", "V001__m38_offline_sync.sql"), "utf8");
  this.psql(`DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_offline_runtime') THEN CREATE ROLE synqoria_offline_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD 'rig-runtime'; END IF; END $$; ALTER ROLE synqoria_offline_runtime PASSWORD 'rig-runtime'; DROP SCHEMA IF EXISTS synqoria_offline CASCADE;`);
  this.psql(migration);

  const target = join(repo, "services", "offline-sync-core", "target");
  const jar = readdirSync(target).find(f => /^offline-sync-core-.*\.jar$/.test(f) && !f.endsWith(".original"));
  if (!jar) throw new Error("build the service first: mvn -DskipTests package in services/offline-sync-core");
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", "java") : "java";
  this.svc = spawn(java, [`-Djavax.net.ssl.trustStore=${trust}`, "-Djavax.net.ssl.trustStorePassword=changeit", "-jar", join(target, jar)], {
   env: {...process.env, SERVER_PORT: String(SERVICE_PORT), SYNQORIA_OFFLINE_JDBC_URL: process.env.RIG_JDBC_URL, SYNQORIA_OFFLINE_DB_USERNAME: "synqoria_offline_runtime", SYNQORIA_OFFLINE_DB_PASSWORD: "rig-runtime",
    SYNQORIA_OFFLINE_OIDC_ISSUER: ISSUER, SYNQORIA_OFFLINE_OIDC_AUDIENCE: AUDIENCE}, stdio: ["ignore", "pipe", "pipe"]});
  this.log = "";
  this.svc.stdout.on("data", d => { this.log += d; }); this.svc.stderr.on("data", d => { this.log += d; });
  for (let i = 0; i < 90; i++) {
   try { const r = await fetch(`${BASE}/status`); if (r.status === 401) return; } catch { /* not up yet */ }
   if (this.svc.exitCode !== null) throw new Error("service exited: " + this.log.slice(-1500));
   await new Promise(r => setTimeout(r, 1000));
  }
  throw new Error("service did not start: " + this.log.slice(-1500));
 }
 /** A genuine RS256 JWT. grantTtl <= 900 s (the service refuses longer grants). */
 token({sub = randomUUID(), cell, facility, perms, grantTtl = 600, ttl = 600, aud = AUDIENCE, iss = ISSUER}) {
  const now = Math.floor(Date.now() / 1000) + this.clockSkew;
  const head = b64u(JSON.stringify({alg: "RS256", typ: "JWT", kid: this.kid}));
  const body = b64u(JSON.stringify({iss, sub, aud, iat: now, nbf: now - 5, exp: now + ttl, synqoria_cell: cell, synqoria_facility: facility, synqoria_permissions: perms, synqoria_grant_expires: now + grantTtl}));
  const sig = createSign("RSA-SHA256").update(head + "." + body).sign(this.privateKey);
  return head + "." + body + "." + b64u(sig);
 }
 /** fetch adapter for the SDK transport: adds the bearer token the HttpOnly cookie would carry in the browser. */
 fetchAs(getToken) {
  return (url, init) => fetch(url, {method: init.method, headers: {...init.headers, Authorization: "Bearer " + getToken()}, body: init.body, signal: init.signal});
 }
 async api(token, method, path, body, key) {
  const headers = {Authorization: "Bearer " + token, Accept: "application/json"};
  if (method === "POST") { headers["Content-Type"] = "application/json"; headers["Idempotency-Key"] = key ?? randomUUID(); }
  const r = await fetch(BASE + path, {method, headers, body: method === "POST" ? JSON.stringify(body ?? {}) : undefined});
  let json = null; try { json = await r.json(); } catch { /* empty */ }
  return {status: r.status, json};
 }
 async stop() {
  if (process.env.RIG_LOG && this.log) writeFileSync(process.env.RIG_LOG, this.log);
  if (this.svc && this.svc.exitCode === null) { this.svc.kill(); await new Promise(r => this.svc.once("exit", r)); }
  if (this.http) await new Promise(r => this.http.close(r));
 }
}
