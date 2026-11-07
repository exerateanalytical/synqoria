"""Generates contracts/openapi/synqoria-m39-fleet-v1.yaml from the Spring controller and the service request records.

Routes, path/query parameters, request bodies (record components) and the required permission (first Perm.* check in the
service method) are all read from source, so the contract cannot silently drift. Run: python tools/gen_m39_openapi.py
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "services/cell-lifecycle-core/src/main/java/org/synqoria/fleet"
api = (SRC / "Api.java").read_text(encoding="utf-8")
services = {n: (SRC / f"{n}.java").read_text(encoding="utf-8") for n in ("FleetService", "RoutingService", "ReleaseService", "MigrationService")}
perm_src = (SRC / "domain/Domain.java").read_text(encoding="utf-8")

PERM_CONST = dict(re.findall(r'\b([A-Z_]+)="(m39:[a-z_]+)"', perm_src))
JAVA_SCALAR = {"String": {"type": "string"}, "UUID": {"type": "string", "format": "uuid"}, "LocalDate": {"type": "string", "format": "date"},
               "Instant": {"type": "string", "format": "date-time"}, "int": {"type": "integer"}, "long": {"type": "integer"}, "Long": {"type": "integer"}, "boolean": {"type": "boolean"}}


def schema_of(t: str):
    t = t.strip()
    m = re.fullmatch(r"List<(.+)>", t)
    if m:
        return {"type": "array", "items": schema_of(m.group(1))}
    if t.startswith("Map<"):
        return {"type": "object", "additionalProperties": True}
    if t in JAVA_SCALAR:
        return dict(JAVA_SCALAR[t])
    return {"$ref": f"#/components/schemas/{t}"}


def split_components(s: str):
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return out


schemas = {}
for body in services.values():
    for name, comps in re.findall(r"public record (\w+)\((.*?)\)\s*\{\}", body, re.S):
        props, required = {}, []
        for c in split_components(comps):
            t, n = c.strip().rsplit(" ", 1)
            props[n] = schema_of(t)
            if t.strip() in ("int", "long", "boolean"):
                required.append(n)
        schemas[name] = {"type": "object", "additionalProperties": False, "properties": props, **({"required": required} if required else {})}

# duplicated record names across services (Decision, Transition) are structurally identical; keep one
perm_by_method = {}
for sname, body in services.items():
    for m in re.finditer(r"public (?:Reply|Map<String,Object>|List<Map<String,Object>>) (\w+)\(([^)]*)\)\{\s*a\.require\(Perm\.([A-Z_]+)\);", body):
        perm_by_method[(sname, m.group(1))] = PERM_CONST[m.group(3)]
perm_by_method[("MigrationService", "step")] = PERM_CONST["MIGRATION_OPERATE"]       # SWITCH needs MIGRATION_APPROVE (checked in the service)
perm_by_method[("MigrationService", "rollback")] = PERM_CONST["MIGRATION_OPERATE"]   # after the switch MIGRATION_APPROVE is needed
FIELD_TO_SERVICE = {"fleet": "FleetService", "routing": "RoutingService", "releases": "ReleaseService", "migrations": "MigrationService"}

paths = {}
op_ids = set()
for m in re.finditer(r'@(Get|Post)Mapping\("([^"]+)"\)\s*public ResponseEntity<(.*?)> (\w+)\((.*?)\)\{(?:return|\s*return)\s*(?:ok|out)\((\w+)\.(\w+)\(', api, re.S):
    verb, path, ret, op, params, field, method = m.groups()
    perm = perm_by_method.get((FIELD_TO_SERVICE[field], method))
    parameters, body_ref = [], None
    for pm in re.finditer(r'@PathVariable (\w+) (\w+)', params):
        parameters.append({"name": pm.group(2), "in": "path", "required": True, "schema": schema_of(pm.group(1))})
    for qm in re.finditer(r'@RequestParam(?:\(([^)]*)\))?\s*(?:@DateTimeFormat\([^)]*\))?\s*(\w+) (\w+)', params):
        opts, typ, name = qm.groups()
        opts = opts or ""
        sch = schema_of(typ)
        if "defaultValue" in opts:
            sch["default"] = int(re.search(r'defaultValue="(\d+)"', opts).group(1))
        if name in ("size", "limit"):
            sch.update(minimum=1, maximum=200)
        required = "required=false" not in opts and "defaultValue" not in opts
        parameters.append({"name": name, "in": "query", "required": required, "schema": sch})
    if verb == "Post":
        parameters.insert(0, {"$ref": "#/components/parameters/IdempotencyKey"})
    rb = re.search(r"@RequestBody ([\w.]+) \w+", params)
    if rb:
        body_ref = rb.group(1).split(".")[-1]
        if body_ref not in schemas:
            sys.exit(f"unknown request record {body_ref} for {op}")
    desc = f"Requires `{perm}`." if perm else "Requires a read permission checked in the service."
    operation = {"operationId": op, "summary": re.sub(r"([a-z])([A-Z])", r"\1 \2", op).capitalize(), "description": desc, "parameters": parameters,
                 "x-permission": perm,
                 "responses": {"401": {"$ref": "#/components/responses/Unauthorized"}, "403": {"$ref": "#/components/responses/Forbidden"}}}
    if not parameters:
        del operation["parameters"]
    if verb == "Post":
        operation["responses"].update({"200": {"$ref": "#/components/responses/Result"}, "201": {"$ref": "#/components/responses/Result"},
                                       "404": {"$ref": "#/components/responses/NotFound"}, "409": {"$ref": "#/components/responses/Conflict"}, "422": {"$ref": "#/components/responses/Invalid"}})
    else:
        operation["responses"].update({"200": {"$ref": "#/components/responses/Result"}, "404": {"$ref": "#/components/responses/NotFound"}, "422": {"$ref": "#/components/responses/Invalid"}})
    if body_ref:
        operation["requestBody"] = {"required": True, "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{body_ref}"}}}}
    if op in op_ids:
        sys.exit(f"duplicate operationId {op}")
    op_ids.add(op)
    paths.setdefault("/" + path.lstrip("/"), {})[verb.lower()] = operation

perms = sorted(set(PERM_CONST.values()))
doc = {
    "openapi": "3.1.0",
    "info": {
        "title": "SYNQORIA M39 Offline Synchronization and Ownership Reconciliation API", "version": "1.0.0",
        "description": (
            "Generated by tools/gen_m39_openapi.py from services/cell-lifecycle-core. Engineering contract; NOT operational acceptance. No real hospital cell is provisioned, migrated or retired by this service: "
            "it records and governs the control-plane state machines and their evidence; infrastructure executors are external. "
            "AUTHORITY: this is the MANAGEMENT PLANE. synqoria_cell / synqoria_facility identify the management plane; hospital cells are rows. It holds no clinical, identity or authorization data. "
            "Tenant-scoped operators (claim synqoria_tenant_scope) see and change only their own tenants; platform-wide operators have no such claim; PostgreSQL forced row-level security repeats the check. "
            "LIFECYCLE: PROVISIONING, QUALIFIED, ACTIVE, DRAINING, FENCED, MIGRATING, RETIRED, FAILED with explicit permitted transitions enforced in code and by trigger; activation requires a passed eight-check qualification completed by a different person. "
            "ROUTING: decisions are HMAC-signed, short-lived, carry the placement epoch and directory version, and FAIL CLOSED (only ACTIVE cells with a recent HEALTHY/DEGRADED observation are routable). Placement epochs only rise. "
            "RELEASES: approved manifests (maker-checker), schema-compatibility gate, canary stages, deployment evidence whose digest must equal the manifest, health gate between stages, automatic stop on failure or unhealthy cells. "
            "MIGRATION: prepare, copy, catch up, freeze, fence, verify, switch, observe, retire; verification must match digests; the switch needs an independent approver and the expected epoch; rollback until retirement, irreversible after. "
            "IDEMPOTENCY: every POST needs Idempotency-Key (replays return the stored response); transitions carry expectedVersion (mismatch is 409 stale_version)."),
    },
    "servers": [{"url": "/api/v1/fleet"}], "security": [{"oidc": []}],
    "paths": paths,
    "components": {
        "securitySchemes": {"oidc": {"type": "openIdConnect", "openIdConnectUrl": "https://idp.invalid/.well-known/openid-configuration",
                                     "description": "Placeholder; issuer configured per hospital cell. Claims synqoria_cell, synqoria_facility, synqoria_permissions, synqoria_grant_expires."}},
        "parameters": {"IdempotencyKey": {"name": "Idempotency-Key", "in": "header", "required": True, "schema": {"type": "string", "format": "uuid"}}},
        "responses": {
            "Result": {"description": "Resource, page or result object (see operation description)", "content": {"application/json": {"schema": {"type": "object", "additionalProperties": True}}}},
            "Unauthorized": {"description": "identity_denied or grant_expired", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Forbidden": {"description": "permission_denied, grant_denied or tenant_scope_denied or an independence rule (*_must_be_independent)", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "NotFound": {"description": "Absent or outside the caller's cell/facility", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Conflict": {"description": "stale_version, idempotency_collision, invalid_transition, stale_placement_epoch, not_qualified, incompatible_release, evidence_mismatch, verification_failed, irreversible, rollout_stopped, stage_not_open, stage_incomplete, health_gate_failed, qualification_incomplete, migration_in_progress, residency_violation, duplicate_record", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Invalid": {"description": "Semantic validation failure", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
        },
        "schemas": {"Error": {"type": "object", "required": ["error", "details"], "properties": {"error": {"type": "string"}, "details": {"type": "array", "items": {"type": "string"}}}}, **schemas},
    },
    "x-permissions": {"vocabulary": perms, "checker": sorted(PERM_CONST[c] for c in ("TENANT_APPROVE", "QUALIFY_COMPLETE", "RELEASE_APPROVE", "MIGRATION_APPROVE"))},
}
out = ROOT / "contracts/openapi/synqoria-m39-fleet-v1.yaml"
out.write_text(yaml.safe_dump(doc, sort_keys=False, allow_unicode=True, width=140), encoding="utf-8")
print(f"wrote {out.relative_to(ROOT)}: {sum(len(v) for v in paths.values())} operations, {len(schemas)} request schemas, {len(perms)} permissions")
