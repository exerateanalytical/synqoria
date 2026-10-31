from pathlib import Path
p=Path(__file__).resolve().parents[1]/'contracts/openapi/synqoria-appointments-quarantine.yaml'
s=p.read_text()
assert 'openapi: 3.1.0' in s
assert 'quarantine' in s.lower()
assert 'oidc: [appointment:manage]' in s
assert "'403':" in s and "'409':" in s and "'503':" in s
assert 'additionalProperties: false' in s
assert 'expectedRevision' in s
assert 'identity.invalid' in s
assert 'Production integration disabled' in s
assert '/internal/v1/appointments/preflight:' in s
assert 'operationId: appointmentPreflightQuarantined' in s
print('PASS 10 static contract assertions; no endpoint deployed')
