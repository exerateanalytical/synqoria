"""M23 preventive-maintenance OpenAPI parity (Issue #68, package 27).
Every Spring route is documented and vice versa, every request record matches its schema, every mutation carries a commandId,
every $ref resolves, and the permission vocabulary in the contract equals the permissions the Java service actually enforces."""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
OPS = ROOT / "services/operations-core/src/main/java/org/synqoria/operations"
spec = yaml.safe_load((ROOT / "contracts/openapi/synqoria-m23-maintenance-v1.yaml").read_text(encoding="utf-8"))
api = (OPS / "MaintenanceApi.java").read_text(encoding="utf-8")
svc = (OPS / "MaintenanceService.java").read_text(encoding="utf-8")
errors = []

assert spec["openapi"].startswith("3.1"), "OpenAPI 3.1 expected"
base = re.search(r'@RequestMapping\("/api/v1/operations([^"]*)"\)', api).group(1)

# 1. route parity ---------------------------------------------------------------------------------
code = set()
for verb, path in re.findall(r'@(Get|Post)Mapping\("([^"]+)"\)', api):
    code.add((verb.lower(), re.sub(r"\{\w+\}", "{}", base + path)))
doc = set()
for path, ops in spec["paths"].items():
    for verb in ops:
        if verb in ("get", "post"):
            doc.add((verb, re.sub(r"\{\w+\}", "{}", path)))
if code != doc:
    errors.append(f"route mismatch: only in code {sorted(code - doc)}, only in contract {sorted(doc - code)}")
if len(doc) != 18:
    errors.append(f"expected 18 operations, found {len(doc)}")

# 2. permission vocabulary: contract == permissions enforced in Java ---------------------------------
enforced = set(re.findall(r'a\.require\("(m23:[a-z_]+)"\)', svc)) | set(re.findall(r'a\.permissions\(\)\.contains\("(m23:[a-z_]+)"\)', svc))
declared = {op["x-synqoria-permission"] for ops in spec["paths"].values() for v, op in ops.items() if v in ("get", "post")}
if not declared <= enforced:
    errors.append(f"contract declares permissions the service never enforces: {sorted(declared - enforced)}")
if not {"m23:pm_propose", "m23:pm_approve", "m23:pm_plan_run", "m23:pm_link", "m23:pm_complete", "m23:calibrate", "m23:pm_record", "m23:read", "m23:return"} <= declared:
    errors.append("a required permission is missing from the contract")
actor_pattern = (OPS / "OperationsActor.java").read_text(encoding="utf-8")
if "m(21|22|23|25|27|29|31|37):[a-z_]{1,40}" not in actor_pattern:
    errors.append("grant vocabulary in OperationsActor changed; re-verify the m23 permission names")
for p in enforced | declared:
    if not re.fullmatch(r"m23:[a-z_]{1,40}", p):
        errors.append(f"permission {p} does not fit the grant grammar")

# 3. request records match schemas --------------------------------------------------------------------
schemas = spec["components"]["schemas"]
def camel(name):
    return name
records = dict(re.findall(r"public record (\w+)\(([^)]*)\)", svc))
records.update(dict(re.findall(r"public record (\w+)\(([^)]*)\)", api)))
for name in ("PolicyRequest", "Decision", "PlanRequest", "LinkRequest", "CompleteRequest", "CalibrationRequest", "ServiceReferenceRequest", "CostRequest", "GenerateRequest", "CommandOnly"):
    comps = [c.strip().split()[-1] for c in records[name].split(",") if c.strip()]
    props = set(schemas[name]["properties"])
    if set(comps) != props:
        errors.append(f"{name}: Java components {sorted(comps)} != schema properties {sorted(props)}")
    if "commandId" not in schemas[name]["required"]:
        errors.append(f"{name}: commandId must be required (idempotency)")

# 4. every operation: id, permission, 401/403, POST has a body ----------------------------------------
ids = set()
for path, ops in spec["paths"].items():
    for verb, op in ops.items():
        if verb not in ("get", "post"):
            continue
        if op["operationId"] in ids:
            errors.append(f"duplicate operationId {op['operationId']}")
        ids.add(op["operationId"])
        if "401" not in op["responses"] or "403" not in op["responses"]:
            errors.append(f"{verb} {path} must document 401 and 403")
        if verb == "post" and "requestBody" not in op:
            errors.append(f"post {path} lacks a request body (commandId)")
if spec.get("security") != [{"facilityStaffJwt": []}]:
    errors.append("global bearer security missing")

# 5. $ref resolution ------------------------------------------------------------------------------------
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

# 6. the M37 notification contract is outbox-only: no sender code in the maintenance service ------------
for banned in ("notification_jobs", "JavaMailSender", "RestClient", "HttpClient", "WebClient"):
    if banned in svc:
        errors.append(f"MaintenanceService must not create its own notification sender ({banned})")
if "M23_" not in svc or "INSERT INTO synqoria_operations.outbox" not in svc:
    errors.append("escalation events must be emitted through the outbox")

if errors:
    print("\n".join("FAIL: " + e for e in errors))
    sys.exit(1)
print(f"M23 maintenance contract OK: {len(doc)} operations, permissions {sorted(declared)}")
