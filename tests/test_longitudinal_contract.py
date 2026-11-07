from pathlib import Path
import json,re,unittest
ROOT=Path(__file__).resolve().parent.parent
SPEC=json.loads((ROOT/'contracts/openapi/synqoria-m040506-restricted-v1.json').read_text())
JAVA=(ROOT/'services/clinical-core/src/main/java/org/synqoria/longitudinal/LongitudinalHttpHandler.java').read_text()
class TestM040506Contract(unittest.TestCase):
 def test_route_parity(self):
  p=SPEC['paths'];self.assertEqual(len(p),5)
  for suffix in ('commands','summary','audit','notes/{noteId}','observations'):
   self.assertTrue(any(k.endswith('/'+suffix) for k in p))
   self.assertIn(suffix.split('/')[0],JAVA)
 def test_command_has_no_actor_provenance_or_client_scope(self):
  body=next(v['post']['requestBody']['content']['application/json']['schema'] for k,v in SPEC['paths'].items() if k.endswith('/commands'))
  self.assertEqual(set(body['required']),{'commandId','expectedRevision','operation','inputs'})
  self.assertFalse(body['additionalProperties']);self.assertEqual(body['properties']['inputs']['maxProperties'],32)
  self.assertFalse({'actorId','tenantId','facilityId','evidence','role','timestamp'}.intersection(body['properties']))
 def test_security_every_operation(self):
  for path,methods in SPEC['paths'].items():
   for name,route in methods.items():
    self.assertEqual(route['security'],[{'bearerAuth':[]}]);self.assertIn('404',route['responses']);self.assertIn('503',route['responses'])
    ids=[p['name'] for p in route['parameters'] if p['in']=='path'];self.assertEqual(ids[:2],['patientId','encounterId'])
 def test_bounded_reads(self):
  for suffix in ('audit','observations'):
   p=next(v['get'] for k,v in SPEC['paths'].items() if k.endswith('/'+suffix))
   limit=next(x for x in p['parameters'] if x['name']=='limit')
   self.assertEqual(limit['schema']['maximum'],100)
 def test_no_online_production_guarantee(self):
  self.assertIn('no live OIDC',SPEC['info']['description'])
if __name__=='__main__':unittest.main()
