"""P01-B0007 event metadata contract only; no Kafka, Debezium or outbox DB execution."""
from pathlib import Path
import json,uuid
from jsonschema import Draft202012Validator,FormatChecker
root=Path(__file__).resolve().parents[1]
schema=json.loads((root/'contracts/events/clinical-outbox-envelope-v1.schema.json').read_text())
Draft202012Validator.check_schema(schema)
v=Draft202012Validator(schema,format_checker=FormatChecker())
u=lambda:str(uuid.uuid4())
valid=dict(eventId=u(),tenantId=u(),facilityId=u(),aggregateType='patient',aggregateId=u(),aggregateRevision=1,eventType='patient.updated',schemaVersion=1,correlationId=u(),occurredAt='2026-10-29T20:00:00Z')
assert v.is_valid(valid)
for mutation in ({'aggregateRevision':0},{'schemaVersion':0},{'eventId':'bad'},{'tenantId':'other'}, {'eventType':'<script>'},{'patientName':'synthetic'},{'occurredAt':'yesterday'}):
 invalid={**valid,**mutation};assert not v.is_valid(invalid),mutation
print('PASS: 8 event metadata schema validation cases; no broker integration')
