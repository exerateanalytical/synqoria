// Builds the SDK to plain JS (TypeScript compiler, no bundler) and serves the browser checks on http://localhost:PORT (a secure context, so WebCrypto,
// IndexedDB and Web Locks behave as they do in production). Usage: node browser-check/serve.mjs [port]
import {execFileSync} from "node:child_process";
import {createServer} from "node:http";
import {createRequire} from "node:module";
import {readFileSync, existsSync, mkdtempSync} from "node:fs";
import {tmpdir} from "node:os";
import {join, dirname, extname} from "node:path";
import {fileURLToPath} from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const pkg = join(here, "..");
const out = mkdtempSync(join(tmpdir(), "sdk-browser-"));
const tsc = createRequire(join(pkg, "package.json")).resolve("typescript/bin/tsc");
execFileSync(process.execPath, [tsc, "-p", join(pkg, "tsconfig.json"), "--noEmit", "false", "--outDir", join(out, "sdk"), "--rewriteRelativeImportExtensions", "--allowImportingTsExtensions", "false", "--erasableSyntaxOnly", "false"], {stdio: "inherit"});
const types = {".js": "text/javascript", ".html": "text/html", ".json": "application/json"};
const port = Number(process.argv[2] ?? 8321);
createServer((req, res) => {
 const url = new URL(req.url, "http://x"); const p = url.pathname === "/" ? "/index.html" : url.pathname;
 const file = p.startsWith("/sdk/") ? join(out, p) : join(here, p.replace(/^\//, ""));
 if (p.includes("..") || !existsSync(file)) { res.statusCode = 404; return res.end("not found"); }
 res.setHeader("Content-Type", types[extname(file)] ?? "text/plain"); res.setHeader("Cache-Control", "no-store");
 res.end(readFileSync(file));
}).listen(port, "127.0.0.1", () => console.log("browser checks on http://localhost:" + port + "/"));
