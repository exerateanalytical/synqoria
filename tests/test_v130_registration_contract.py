"""P01-B0029: component-only OpenAPI contract and safety separation, no endpoint."""
from pathlib import Path
import yaml
r=Path(__file__).resolve().parents[1]
contract=yaml.safe_load((r/'contracts/openapi/synqoria-registration-draft.yaml').read_text())
assert contract['openapi']=='3.1.0' and contract['paths']=={}
s=contract['components']['schemas']['RegistrationIntakeDraft']
assert s['additionalProperties'] is False
assert set(s['required'])=={'birthInformation','language'}
assert set(s['properties'])=={'legalName','birthInformation','dateOfBirth','estimatedAgeYears','language'}
assert s['properties']['estimatedAgeYears']['maximum']==130
assert 'patientId' not in s['properties'] and 'duplicateReviewCleared' not in s['properties']
assert set(contract['components']['schemas']['RegistrationUnavailable']['properties'])=={'code','correlationId'}
print('PASS P01-B0029: draft schema forbids client trusted fields and exposes zero paths')
