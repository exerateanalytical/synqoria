"""P01-B0007 metadata schema only; no Kafka/Debezium/PostgreSQL execution."""
from pathlib import Path
import json, uuid
from jsonschema import Draft202012Validator, FormatChecker

root = Path(__file__).resolve().parents[1]
schema = json.loads((root / 'contracts/events/clinical-outbox-envelope-v1.schema.json').read_text())
Draft202012Validator.check_schema(schema)
with_format = Draft202012Validator(schema, format_checker=FormatChecker())
without_format = Draft202012Validator(schema)
new_uuid = lambda: str(uuid.uuid4())
valid = dict(
    eventId=new_uuid(), tenantId=new_uuid(), facilityId=new_uuid(), aggregateType='patient',
    aggregateId=new_uuid(), aggregateRevision=1, eventType='patient.updated',
    schemaVersion=1, correlationId=new_uuid(), occurredAt='2026-10-29T20:00:00Z'
)
assert with_format.is_valid(valid)
assert without_format.is_valid(valid)
negative = (
    {'aggregateRevision': 0}, {'schemaVersion': 0}, {'eventId': 'bad'},
    {'tenantId': 'other'}, {'eventType': '<script>'},
    {'patientName': 'synthetic'}, {'occurredAt': 'yesterday'},
    {'occurredAt': '2026-10-29'}, {'occurredAt': '2026-13-29T20:00:00Z'},
    {'occurredAt': '2026-00-29T20:00:00Z'}, {'occurredAt': '2026-10-00T20:00:00Z'},
    {'occurredAt': '2026-10-29T25:00:00Z'},
    {'occurredAt': '2026-10-29T20:60:00Z'},
    {'occurredAt': '2026-10-29T20:00:60Z'},
    {'occurredAt': '2026-10-29T20:00:00'},  # missing timezone
    {'occurredAt': '2026-10-29 20:00:00Z'},  # require UTC-designated T separator
    {'occurredAt': '2026-10-29T20:00:00+14:01'},
    {'occurredAt': '2026-10-29T20:00:00.1234567890Z'},
)
for mutation in negative:
    invalid = {**valid, **mutation}
    assert not with_format.is_valid(invalid), mutation
# The RFC3339 lexical structure cannot depend on an optional format-checker installation.
for timestamp in ('yesterday', '2026-10-29', '2026-10-29T25:00:00Z',
                  '2026-10-29T20:00:00', '2026-13-29T20:00:00Z'):
    assert not without_format.is_valid({**valid, 'occurredAt': timestamp}), timestamp
for timestamp in ('2026-10-29T20:00:00Z', '2026-10-29T20:00:00.123456789Z',
                  '2026-10-29T20:00:00+01:00', '2026-10-29T20:00:00-05:00'):
    assert without_format.is_valid({**valid, 'occurredAt': timestamp}), timestamp
print('PASS: outbox envelope metadata rejects malformed RFC3339 without optional format plugins; no broker/DB integration')
