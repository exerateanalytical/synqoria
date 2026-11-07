"""Contract parity between the M38 offline device SDK and the canonical M38 OpenAPI document.
Fails CI on drift: every SDK route exists in the contract with the same verb, the request field names and the contract version match exactly,
the browser proxy allow-list admits every SDK route and nothing the contract does not define."""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "contracts/openapi/synqoria-m38-offline-v1.yaml").read_text(encoding="utf-8"))
types_ts = (ROOT / "packages/offline-device-sdk/src/types.ts").read_text(encoding="utf-8")
policy_ts = (ROOT / "apps/web/lib/offline/proxy-policy.ts").read_text(encoding="utf-8")
errors = []

# 1. contract version pinned
m = re.search(r'CONTRACT_VERSION = "([^"]+)"', types_ts)
if not m or m.group(1) != spec["info"]["version"]:
    errors.append(f"contract version drift: sdk={m.group(1) if m else None} openapi={spec['info']['version']}")

# 2. routes
routes = re.findall(r'(\w+): \{ method: "(GET|POST)", path: "([^"]+)" \}', types_ts)
if len(routes) < 9:
    errors.append(f"expected the SDK route table, found {len(routes)} routes")
for name, verb, path in routes:
    node = spec["paths"].get(path)
    if node is None or verb.lower() not in node:
        errors.append(f"SDK route {name} {verb} {path} is not in the OpenAPI contract")
        continue
    op = node[verb.lower()]
    if verb == "POST" and "#/components/parameters/IdempotencyKey" not in [p.get("$ref") for p in op.get("parameters", [])]:
        errors.append(f"{verb} {path} must require Idempotency-Key")

# 3. request field names
def const_list(name):
    mm = re.search(rf"export const {name} = \[(.*?)\] as const", types_ts, re.S)
    return re.findall(r'"([^"]+)"', mm.group(1)) if mm else []
for const, schema in (("COMMAND_FIELDS", "CommandIn"), ("BATCH_FIELDS", "BatchRequest"), ("RESOLUTION_FIELDS", "Resolution")):
    sdk = const_list(const)
    api = list(spec["components"]["schemas"][schema]["properties"])
    if sdk != api:
        errors.append(f"{schema}: SDK fields {sdk} != contract fields {api}")

# 4. limits
if f"MAX_BATCH = 50" not in types_ts:
    errors.append("MAX_BATCH must be 50")
cmd_props = spec["components"]["schemas"]["CommandIn"]["properties"]
if "fields" not in cmd_props or "payload" not in cmd_props:
    errors.append("contract lost fields/payload")

# 5. proxy allow-list admits each SDK route, and only contract routes
def to_regex(path):
    return re.compile("^" + re.sub(r"\\\{id\\\}", "[^/]+", re.escape(path.lstrip("/"))) + "$")
allowed_get = re.search(r"const GET_ROUTES = \[(.*?)\]\.map", policy_ts, re.S).group(1)
allowed_post = re.search(r"const POST_ROUTES = \[(.*?)\]\.map", policy_ts, re.S).group(1)
def lits(block):
    return [re.sub(r"\$\{uuid\}", "{id}", x) for x in re.findall(r'[`"]([^`"]+)[`"]', block)]
get_paths, post_paths = lits(allowed_get), lits(allowed_post)
for name, verb, path in routes:
    p = path.lstrip("/")
    pool = get_paths if verb == "GET" else post_paths
    if p not in pool:
        errors.append(f"proxy policy does not admit SDK route {verb} {path}")
contract_paths = {k.lstrip("/") for k in spec["paths"]}
for p in get_paths + post_paths:
    if p not in contract_paths:
        errors.append(f"proxy policy admits {p}, which the contract does not define")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"M38 device SDK contract OK: {len(routes)} routes, contract {spec['info']['version']}")
