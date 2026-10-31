from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
sql=(root/'services/clinical-core/db/migration/V010__review_audit_outbox_quarantine.sql').read_text()
code=re.sub(r'--[^\n]*','',sql)
checks={
 'review_audit_table':r'CREATE\s+TABLE\s+patient_registration_review_audit',
 'event_fk':r'FOREIGN\s+KEY\s*\(tenant_id,facility_id,review_event_id\)\s*REFERENCES\s+patient_registration_review_events',
 'outbox_fk':r'FOREIGN\s+KEY\s*\(tenant_id,facility_id,outbox_event_id\)\s*REFERENCES\s+clinical_outbox',
 'force_rls':r'ALTER\s+TABLE\s+patient_registration_review_audit\s+FORCE\s+ROW\s+LEVEL\s+SECURITY',
 'enable_rls':r'ALTER\s+TABLE\s+patient_registration_review_audit\s+ENABLE\s+ROW\s+LEVEL\s+SECURITY',
 'revoke_public':r'REVOKE\s+ALL\s+ON\s+patient_registration_review_audit\s+FROM\s+PUBLIC',
 'unique_event':r'UNIQUE\s*\(tenant_id,facility_id,review_event_id\)',
 'unique_outbox':r'UNIQUE\s*\(tenant_id,facility_id,outbox_event_id\)',
}
for name,pattern in checks.items():assert re.search(pattern,code,re.I),name
assert not re.search(r'\b(GRANT|CREATE\s+POLICY|DROP\s+TABLE)\b',code,re.I)
print('PASS P05-B0053',len(checks)+1,'DDL static checks; migration UNAPPLIED')
