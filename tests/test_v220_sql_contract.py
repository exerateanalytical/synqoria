from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'services/clinical-core/db/migration/V009__review_event_digest_contract_quarantine.sql').read_text()
assert 'ADD COLUMN previous_digest_sha256' in s and 'ADD COLUMN event_digest_sha256' in s
assert 'REVOKE ALL ON patient_registration_review_events FROM PUBLIC' in s
assert 'CREATE POLICY' not in s and 'GRANT ' not in s.replace('no grants','')
java=(r/'services/clinical-core/src/main/java/org/synqoria/registration/ReviewEventJdbcRead.java').read_text()
assert 'final class ReviewEventJdbcRead' in java and 'public class ReviewEventJdbcRead' not in java
assert 'ReviewTransactionFence.eligible(c)' in java and 'LIMIT 3' in java
assert 'tenant_id=? AND facility_id=? AND review_id=?' in java
assert 'ReviewHistoryRebuilder.rebuild' in java
assert 'ps.setObject(1,tenant)' in java and 'ps.setObject(2,facility)' in java and 'ps.setObject(3,review)' in java
assert 'ReviewEventJdbcRead' not in (r/'services/clinical-core/src/main/java/org/synqoria/registration/RegistrationProductionBoundary.java').read_text()
print('PASS v0.22.0: restricted SQL/JDBC source contract; NOT PostgreSQL execution')
