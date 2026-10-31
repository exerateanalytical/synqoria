"""M03 read contract: schema and source parity; not proof of live OIDC or PostgreSQL."""
import pathlib,unittest,yaml,re
ROOT=pathlib.Path(__file__).resolve().parents[1]
API=ROOT/'contracts/openapi/synqoria-m03-triage-restricted-v1.yaml'
JAVA=ROOT/'services/clinical-core/src/main/java/org/synqoria/triage/TriageReadHttpHandler.java'
CLIENT=ROOT/'packages/ux-core/src/triage-client.ts'
class TriageReadContractTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.data=yaml.safe_load(API.read_text());cls.src=JAVA.read_text();cls.client=CLIENT.read_text()
 def test_summary_get_requires_bearer_and_no_store(self):
  path='/api/v1/triage/patients/{patientId}/cases/{caseId}'
  op=self.data['paths'][path]['get'];self.assertEqual(op['security'],[{'bearerAuth':[]}]);self.assertIn('no-store, private',op['responses']['200']['headers']['Cache-Control']['schema']['enum'])
  self.assertEqual(self.data['components']['schemas']['TriageCaseSummary']['additionalProperties'],False)
  self.assertNotIn('freeText',self.data['components']['schemas']['TriageCaseSummary']['properties'])
 def test_audit_history_bounded_and_not_disclosing_notes(self):
  path='/api/v1/triage/patients/{patientId}/cases/{caseId}/history'
  op=self.data['paths'][path]['get'];self.assertEqual(op['security'],[{'bearerAuth':[]}]);
  schema=self.data['components']['schemas']['TriageAuditPage'];self.assertEqual(schema['properties']['history']['maxItems'],100)
  event=self.data['components']['schemas']['TriageAuditEvent'];self.assertFalse(event['additionalProperties']);self.assertNotIn('text',event['properties']);self.assertNotIn('patientId',event['properties']);
 def test_revision_cursor_and_backend_limit_align(self):
  self.assertIn('HISTORY_PAGE_SIZE=100',self.src)
  self.assertIn('afterRevision=',self.src)
  self.assertIn("/history?afterRevision=",self.client)
  self.assertEqual(self.data['components']['schemas']['TriageAuditPage']['properties']['nextAfterRevision']['maximum'],2147483647)
 def test_client_backend_summary_fields_align(self):
  required=set(self.data['components']['schemas']['TriageCaseSummary']['required'])
  for x in required:self.assertIn(x,self.client)
  self.assertEqual(len(required),9)
 def test_reject_permissive_api_for_new_read_routes(self):
  for path in ('/api/v1/triage/patients/{patientId}/cases/{caseId}','/api/v1/triage/patients/{patientId}/cases/{caseId}/history'):
   op=self.data['paths'][path]['get'];self.assertNotIn('requestBody',op)
   self.assertIn('404',op['responses']);self.assertNotIn('403',op['responses'])
   self.assertNotIn('200',op.get('requestBody',{}))
if __name__=='__main__':unittest.main()
