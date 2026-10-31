from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[1]
contract=yaml.safe_load((root/'contracts/openapi/synqoria-foundation.yaml').read_text())
assert contract['openapi']=='3.1.0'
assert 'security' in contract
assert '/patients/{patientId}' in contract['paths']
assert '404' in contract['paths']['/patients/{patientId}']['get']['responses']
assert 'patientId' in str(contract['paths']['/patients/{patientId}'])
assert contract['servers'][0]['variables']['hospitalCellHost']['default']=='example.invalid'
assert 'clinical use' in contract['info']['description']
print('PASS: draft OpenAPI YAML parsed; secured patient path, 404 nondisclosure and placeholder identity endpoints')
