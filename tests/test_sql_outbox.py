"""P01-B0004 static assertions; NOT a PostgreSQL migration test."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'services/clinical-core/db/migration/V002__outbox_inbox_foundation.sql').read_text()
checks=['CREATE TABLE clinical_outbox (','CREATE TABLE clinical_inbox (',
 'FOREIGN KEY (tenant_id, facility_id) REFERENCES hospital_facilities(tenant_id, facility_id)',
 'UNIQUE (tenant_id, facility_id, aggregate_type, aggregate_id, aggregate_revision, event_type)',
 'PRIMARY KEY (tenant_id, facility_id, consumer_id, event_id)',
 'WHERE delivered_at IS NULL','ALTER TABLE clinical_outbox ENABLE ROW LEVEL SECURITY;',
 'ALTER TABLE clinical_outbox FORCE ROW LEVEL SECURITY;',
 'ALTER TABLE clinical_inbox ENABLE ROW LEVEL SECURITY;',
 'ALTER TABLE clinical_inbox FORCE ROW LEVEL SECURITY;',
 'REVOKE ALL ON clinical_outbox, clinical_inbox FROM PUBLIC;']
for fragment in checks:assert fragment in s,fragment
assert 'CREATE POLICY' not in s
print(f'PASS: {len(checks)+1} outbox/inbox static assertions; PostgreSQL NOT executed')
