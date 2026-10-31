from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[1]
p=yaml.safe_load((root/'contracts/openapi/synqoria-loopback-read-boundary.yaml').read_text())
assert p['openapi']=='3.1.0'
assert set(p['paths'])=={'/health/live','/health/ready','/api/v1/facility/context','/api/v1/patients/{patientId}'}
assert '401' in p['paths']['/api/v1/patients/{patientId}']['get']['responses']
assert '404' in p['paths']['/api/v1/patients/{patientId}']['get']['responses']
assert '503' in p['paths']['/health/ready']['get']['responses']
assert 'test verifier' in p['components']['securitySchemes']['testBearer']['description'].lower()
print('PASS: loopback adapter contract routes and fail-closed status definition; no OIDC claim')
