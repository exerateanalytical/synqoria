"""Exact Java annotation <-> OpenAPI path parity for shared operations.

This is source-contract verification, not runtime or clinical acceptance.
The M27 mortuary API retains its own dedicated schema and safety contract.
"""
import json
import re
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"services/operations-core/src/main/java/org/synqoria/operations"
CONTRACT=ROOT/"contracts/openapi/synqoria-operations-shared-v1.json"
CONTROLLERS=("OperationsApi","OperationsIntegrationApi","OperationsConnectorApi")
FIXED={
 "GET /api/v1/operations/integrations/M21/procurement-reconciliation":"m21:read",
 "POST /api/v1/operations/integrations/M22/stock-counts/{itemId}":"m22:count",
 "POST /api/v1/operations/integrations/M22/stock-counts/{countId}/review":"m22:adjust",
 "GET /api/v1/operations/integrations/M25/available-vehicles":"m25:read",
 "GET /api/v1/operations/integrations/M29/publication-feed":"m29:read",
 "GET /api/v1/operations/integrations/M37/due-deliveries":"m37:deliver",
 "POST /api/v1/operations/connectors/M21/procurement-exports/{itemId}":"m21:close",
 "GET /api/v1/operations/connectors/M21/procurement-exports":"m21:read",
 "GET /api/v1/operations/connectors/M22/barcode-stock":"m22:read",
 "POST /api/v1/operations/connectors/M25/vehicle-reservations":"m25:assign",
 "POST /api/v1/operations/connectors/M25/vehicle-reservations/{reservationId}/cancel":"m25:cancel",
 "GET /api/v1/operations/connectors/M29/publications":"m29:read",
 "POST /api/v1/operations/connectors/M37/delivery-leases":"m37:deliver",
 "GET /api/v1/operations/M22/items/{itemId}/balances":"m22:read",
 "POST /api/v1/operations/M22/items/{itemId}/movements":"movement-dependent m22:receive|reserve|issue|release|return"
}
def source_routes():
    found=[]
    for name in CONTROLLERS:
        source=(JAVA/(name+".java")).read_text(encoding="utf-8")
        parent=re.search(r'@RequestMapping\("([^"]+)"\)',source)
        if not parent:
            raise AssertionError(name+" missing class mapping")
        for method,path in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping\("([^"]+)"\)',source):
            found.append((method.upper(),parent.group(1)+path))
    if len(set(found))!=len(found):
        raise AssertionError("duplicate Java controller route")
    return found

def actual_operations(doc):
    return {(verb.upper(),path):op for path,methods in doc["paths"].items()
        for verb,op in methods.items() if verb.lower() in ("get","post","put","patch","delete")}

class OperationsContractParity(unittest.TestCase):
    def setUp(self):
        self.doc=json.loads(CONTRACT.read_text(encoding="utf-8"))
        self.endpoints=actual_operations(self.doc)

    def test_exact_java_route_parity(self):
        self.assertEqual(set(self.endpoints),set(source_routes()),"Java controller/contract path drift")

    def test_nonoptional_signed_identity_scope_and_grants(self):
        for (method,path),op in self.endpoints.items():
            with self.subTest(method=method,path=path):
                self.assertEqual(op.get("security"),[{"facilityStaffJwt":[]}])
                self.assertIs(op.get("x-synqoria-current-workforce-admission"),True)
                self.assertIs(op.get("x-synqoria-cell-and-facility-rls"),True)
                self.assertEqual(op.get("x-synqoria-deployment-status"),"source-integrated-not-hospital-accepted")
                self.assertTrue(op.get("x-synqoria-required-grant"))
                self.assertIn("401",op["responses"])
                self.assertIn("403",op["responses"])
                self.assertIn("503",op["responses"])
                self.assertFalse(any(q in path for q in ("/M38/","/M39/","/M40/")))
        self.assertEqual(self.doc["components"]["securitySchemes"]["facilityStaffJwt"]["scheme"],"bearer")

    def test_grant_map_is_precise_for_fixed_routes(self):
        for key,required in FIXED.items():
            method,path=key.split(" ",1)
            with self.subTest(key=key):
                self.assertEqual(self.endpoints[(method,path)]["x-synqoria-required-grant"],required)
        self.assertIn("m22:receive", (JAVA/"OperationsStore.java").read_text(encoding="utf-8"))
        self.assertIn('actor.require("m25:assign")',(JAVA/"OperationsConnectorService.java").read_text(encoding="utf-8"))

    def test_no_duplicate_m27_mortuary_contract(self):
        self.assertTrue((ROOT/"contracts/openapi/synqoria-m27-mortuary-v1.yaml").exists())
        self.assertFalse(any("/M27/cases" in path for method,path in self.endpoints))
        self.assertFalse(any("M38" in path for method,path in self.endpoints))

    def test_all_typed_path_parameters_are_bound(self):
        for (method,path),op in self.endpoints.items():
            declared={p["name"] for p in op.get("parameters",[]) if p.get("in")=="path" and p.get("required")}
            needed=set(re.findall(r"\{(\w+)\}",path))
            self.assertEqual(declared,needed,(method,path))
            for p in op.get("parameters",[]):
                if p["in"]=="path" and p["name"]!="module":
                    self.assertEqual(p["schema"],{"type":"string","format":"uuid"})

    def test_post_body_exists_except_explicit_no_body_actions(self):
        no_body={"/api/v1/operations/connectors/M21/procurement-exports/{itemId}",
                 "/api/v1/operations/connectors/M25/vehicle-reservations/{reservationId}/cancel",
                 "/api/v1/operations/connectors/M37/delivery-leases"}
        for (method,path),op in self.endpoints.items():
            if method=="POST":
                self.assertEqual("requestBody" in op,path not in no_body,path)
                if "requestBody" in op:
                    self.assertTrue(op["requestBody"]["required"])

    def test_dynamic_module_scope_never_accepts_arbitrary_module_identifiers(self):
        dyn=[op for (verb,path),op in self.endpoints.items() if "{module}" in path]
        self.assertGreaterEqual(len(dyn),4)
        for op in dyn:
            enum=next(p["schema"]["enum"] for p in op["parameters"] if p["name"]=="module")
            self.assertIn("M23",enum)
            self.assertIn("M37",enum)
            self.assertNotIn("M40",enum)

    def test_mismatch_detection_rejects_forged_absent_route(self):
        altered=dict(self.endpoints)
        altered.pop(next(iter(altered)))
        self.assertNotEqual(set(altered),set(source_routes()))

if __name__=="__main__":
    unittest.main()
