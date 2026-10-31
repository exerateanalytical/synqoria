from pathlib import Path
import unittest,re,yaml,json
ROOT=Path(__file__).resolve().parents[1]
class V560Contract(unittest.TestCase):
 def test_paths_and_permissions(self):
  d=yaml.safe_load((ROOT/'contracts/openapi/synqoria-m03-triage-restricted-v1.yaml').read_text())
  self.assertEqual(len(d['paths']),3)
  for path,verbs in d['paths'].items():
   self.assertEqual(list(verbs),['post'])
   self.assertEqual(verbs['post']['security'],[{'bearerAuth':[]}])
  self.assertEqual(d['components']['schemas']['TriageIntake']['properties']['expectedRevision']['enum'],[0])
 def test_catalog_parity(self):
  d=yaml.safe_load((ROOT/'contracts/openapi/synqoria-m03-triage-restricted-v1.yaml').read_text())
  wire=set(d['components']['schemas']['TriageCommand']['properties']['action']['enum'])
  source=(ROOT/'services/clinical-core/src/main/java/org/synqoria/triage/TriageAuthenticatedCommandGateway.java').read_text()
  block=source.split('UnaryOperator<TriageCase> action=switch(input.action()) {')[1].split('   default ->')[0]
  java=set(re.findall(r'case "([A-Z_]+)"',block))
  self.assertEqual(wire,java);self.assertEqual(len(wire),49)
 def test_privacy_and_boundaries(self):
  d=yaml.safe_load((ROOT/'contracts/openapi/synqoria-m03-triage-restricted-v1.yaml').read_text())
  for key in ('TriageCommand','TriageIntake','TriageWorklist','ObservationEvidence'):
   self.assertFalse(d['components']['schemas'][key]['additionalProperties'])
   for forbidden in ('actorId','tenantId','facilityId','recordedBy','patientDetails'):
    self.assertNotIn(forbidden,d['components']['schemas'][key]['properties'])
  self.assertEqual(d['components']['schemas']['TriageWorklist']['properties']['caseIds']['maxItems'],50)
if __name__=='__main__':unittest.main()
