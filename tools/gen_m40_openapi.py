"""Generates contracts/openapi/synqoria-m40-reliability-v1.yaml from the Spring controller and the service request records.

Routes, path/query parameters, request bodies (record components) and the required permission (first Perm.* check in the
service method) are all read from source, so the contract cannot silently drift. Run: python tools/gen_m40_openapi.py
"""
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "services/reliability-core/src/main/java/org/synqoria/reliability"
api = (SRC / "Api.java").read_text(encoding="utf-8")
services = {n: (SRC / f"{n}.java").read_text(encoding="utf-8") for n in ("ObservabilityService", "IncidentService", "BackupService", "RecoveryService")}
perm_src = (SRC / "domain/Domain.java").read_text(encoding="utf-8")

PERM_CONST = dict(re.findall(r'\b([A-Z_]+)="(m40:[a-z_]+)"', perm_src))
JAVA_SCALAR = {"String": {"type": "string"}, "UUID": {"type": "string", "format": "uuid"}, "LocalDate": {"type": "string", "format": "date"},
               "Instant": {"type": "string", "format": "date-time"}, "int": {"type": "integer"}, "long": {"type": "integer"}, "Long": {"type": "integer"}, "double": {"type": "number"}, "Boolean": {"type": "boolean"}, "boolean": {"type": "boolean"}}


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
FIELD_TO_SERVICE = {"obs": "ObservabilityService", "incidents": "IncidentService", "backups": "BackupService", "recoveries": "RecoveryService"}

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
        "title": "SYNQORIA M40 Offline Synchronization and Ownership Reconciliation API", "version": "1.0.0",
        "description": (
            "Generated by tools/gen_m40_openapi.py from services/reliability-core. Engineering contract; NOT operational acceptance, and no recovery guarantee is made or implied. "
            "SCOPE: cell and facility come only from the verified claims synqoria_cell / synqoria_facility; PostgreSQL forced row-level security repeats the check. This service holds no clinical data and no key material (key REFERENCES only). "
            "HEALTH: derived from fresh signals; a service with no fresh READINESS signal is UNKNOWN, never healthy; a HARD dependency that is DOWN or UNKNOWN caps the service at DEGRADED. "
            "TELEMETRY: events are redacted before storage (identifiers, secrets, long numbers, free text) and every read of telemetry is itself recorded. "
            "BACKUPS: a backup is RECORDED when created and VERIFIED only after five independent passing checks by a different person; the INTEGRITY check passes only if the digest recomputed from the artifact equals the recorded digest. "
            "RECOVERY: planned against a VERIFIED base backup and continuous WAL; independent authorization; positive fencing confirmation; restore must reach the target position; objects and external effects must be fully reconciled; audit chain recovered; six validations must pass; "
            "reopening is scoped to named services and approved by someone who neither planned nor authorized the recovery. RPO/RTO rows exist only for recoveries that actually reopened and are labelled EXERCISE or REAL. "
            "IDEMPOTENCY: every POST needs Idempotency-Key (replays return the stored response); transitions carry expectedVersion (mismatch is 409 stale_version)."),
    },
    "servers": [{"url": "/api/v1/reliability"}], "security": [{"oidc": []}],
    "paths": paths,
    "components": {
        "securitySchemes": {"oidc": {"type": "openIdConnect", "openIdConnectUrl": "https://idp.invalid/.well-known/openid-configuration",
                                     "description": "Placeholder; issuer configured per hospital cell. Claims synqoria_cell, synqoria_facility, synqoria_permissions, synqoria_grant_expires."}},
        "parameters": {"IdempotencyKey": {"name": "Idempotency-Key", "in": "header", "required": True, "schema": {"type": "string", "format": "uuid"}}},
        "responses": {
            "Result": {"description": "Resource, page or result object (see operation description)", "content": {"application/json": {"schema": {"type": "object", "additionalProperties": True}}}},
            "Unauthorized": {"description": "identity_denied or grant_expired", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Forbidden": {"description": "permission_denied, grant_denied or an independence rule (*_must_be_independent)", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "NotFound": {"description": "Absent or outside the caller's cell/facility", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Conflict": {"description": "stale_version, idempotency_collision, invalid_transition, backup_not_verified, wal_gap, restore_target_mismatch, objects_unreconciled, effects_unreconciled, fencing_not_confirmed, validation_incomplete, target_out_of_range, duplicate_record", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
            "Invalid": {"description": "Semantic validation failure", "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Error"}}}},
        },
        "schemas": {"Error": {"type": "object", "required": ["error", "details"], "properties": {"error": {"type": "string"}, "details": {"type": "array", "items": {"type": "string"}}}}, **schemas},
    },
    "x-permissions": {"vocabulary": perms, "checker": sorted(PERM_CONST[c] for c in ("BACKUP_VERIFY", "INCIDENT_VERIFY", "RECOVERY_AUTHORIZE", "RECOVERY_VALIDATE", "REOPEN_APPROVE"))},
}
out = ROOT / "contracts/openapi/synqoria-m40-reliability-v1.yaml"
out.write_text(yaml.safe_dump(doc, sort_keys=False, allow_unicode=True, width=140), encoding="utf-8")
print(f"wrote {out.relative_to(ROOT)}: {sum(len(v) for v in paths.values())} operations, {len(schemas)} request schemas, {len(perms)} permissions")
