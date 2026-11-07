// Starts the proxy end-to-end test in a child process that trusts the rig's self-signed certificate (NODE_EXTRA_CA_CERTS must be set before Node starts).
// Usage: SYNQORIA_LIVE_RIG=1 RIG_PG_CONTAINER=... RIG_PG_DB=... RIG_JDBC_URL=... node packages/offline-device-sdk/tests/e2e/launch.mjs
import {spawnSync, execFileSync} from "node:child_process";
import {mkdtempSync} from "node:fs";
import {tmpdir} from "node:os";
import {join, dirname} from "node:path";
import {fileURLToPath} from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const dir = mkdtempSync(join(tmpdir(), "m38-e2e-"));
execFileSync("openssl", ["req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", join(dir, "k.pem"), "-out", join(dir, "c.pem"), "-days", "1", "-subj", "/CN=localhost",
 "-addext", "subjectAltName=DNS:localhost,IP:127.0.0.1"], {stdio: "ignore"});
const r = spawnSync(process.execPath, ["--test", join(here, "proxy.test.mjs")], {stdio: "inherit",
 env: {...process.env, NODE_EXTRA_CA_CERTS: join(dir, "c.pem"), RIG_DIR: dir, RIG_TLS: "1", SYNQORIA_LIVE_RIG: "1"}});
process.exit(r.status ?? 1);
