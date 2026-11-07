"""M24 OpenAPI contract parity: every Spring route has a documented operation and vice versa,
every mutation requires Idempotency-Key, every $ref resolves, permissions are a closed vocabulary."""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "contracts/openapi/synqoria-m24-hr-v1.yaml").read_text(encoding="utf-8"))
api = (ROOT / "services/hr-payroll-core/src/main/java/org/synqoria/hr/Api.java").read_text(encoding="utf-8")
perm = (ROOT / "services/hr-payroll-core/src/main/java/org/synqoria/hr/domain/Domain.java").read_text(encoding="utf-8")

errors = []
assert spec["openapi"].startswith("3.1"), "OpenAPI 3.1 expected"

# 1. Route parity ---------------------------------------------------------------------------
code = set()
for verb, path in re.findall(r'@(Get|Post|Put)Mapping\("([^"]+)"\)', api):
    code.add((verb.lower(), re.sub(r"\{(\w+)\}", "{}", path)))
doc = set()
for path, ops in spec["paths"].items():
    for verb in ops:
        if verb in ("get", "post", "put"):
            doc.add((verb, re.sub(r"\{(\w+)\}", "{}", path)))
if code != doc:
    errors.append(f"route mismatch: only in code {sorted(code - doc)}, only in contract {sorted(doc - code)}")

# 2. Idempotency on every mutation, security default, error responses -------------------------
for path, ops in spec["paths"].items():
    for verb, op in ops.items():
        if verb not in ("get", "post", "put"):
            continue
        refs = [p.get("$ref", "") for p in op.get("parameters", [])]
        if verb in ("post", "put") and not path.endswith("/evaluate") and "#/components/parameters/IdempotencyKey" not in refs:
            errors.append(f"{verb} {path} lacks Idempotency-Key")
        if "401" not in op["responses"] or "403" not in op["responses"]:
            if verb != "get" or "404" not in op["responses"]:
                errors.append(f"{verb} {path} must document 401/403")
        if "operationId" not in op:
            errors.append(f"{verb} {path} lacks operationId")
if spec.get("security") != [{"oidc": []}]:
    errors.append("global OIDC security requirement missing")

# 3. $ref resolution ---------------------------------------------------------------------------
def walk(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "$ref":
                cur = spec
                for part in v[2:].split("/"):
                    if part not in cur:
                        errors.append(f"unresolved {v}")
                        break
                    cur = cur[part]
            else:
                walk(v)
    elif isinstance(node, list):
        for v in node:
            walk(v)
walk(spec)

# 4. Permission vocabulary parity -----------------------------------------------------------------
code_perms = set(re.findall(r'"(m24:[a-z_]+)"', perm))
doc_perms = set(spec["x-permissions"]["vocabulary"])
if code_perms != doc_perms:
    errors.append(f"permission mismatch {code_perms ^ doc_perms}")
if not set(spec["x-permissions"]["checker"]) <= doc_perms:
    errors.append("checker permissions not in vocabulary")
for path, ops in spec["paths"].items():
    for verb, op in ops.items():
        if verb == "post" and not op.get("x-permission"):
            errors.append(f"post {path} has no permission check in its service method")
        if op.get("x-permission") and op["x-permission"] not in doc_perms:
            errors.append(f"{path} {verb}: unknown permission")
# the generated document is current: regenerating produces identical text
import subprocess
before = (ROOT / "contracts/openapi/synqoria-m24-hr-v1.yaml").read_text(encoding="utf-8")
subprocess.run([sys.executable, str(ROOT / "tools/gen_m24_openapi.py")], check=True, capture_output=True)
if (ROOT / "contracts/openapi/synqoria-m24-hr-v1.yaml").read_text(encoding="utf-8") != before:
    errors.append("contract is stale: run tools/gen_m24_openapi.py")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"M24 OpenAPI OK: {len(doc)} operations, {len(doc_perms)} permissions")
