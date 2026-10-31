"""Finalize locally verified v0.52.0 source; no live DB or release certification."""
from pathlib import Path
import json,hashlib,datetime,shutil
ROOT=Path(__file__).resolve().parents[1]
E=ROOT/'evidence/v520'; R=ROOT/'registers'
base='services/clinical-core/src/main/java/org/synqoria/triage/'
items=[
 ('Deterministic bounded aggregate snapshot codec','TriageSnapshotCodec.java',['codec-roundtrip','codec-audit','codec-tamper']),
 ('Snapshot version, truncation, size and canonical ordering guards','TriageSnapshotCodec.java',['codec-determinism','codec-bad-magic','codec-truncation','codec-trailing','codec-oversize']),
 ('AES-256-GCM snapshot envelope and authenticated ciphertext','TriageEnvelopeCrypto.java',['crypto-roundtrip','crypto-random-nonce','crypto-tamper','crypto-invalid-key']),
 ('Cryptographic patient/revision/key binding and immutable envelope','TriageEnvelopeCrypto.java',['crypto-scope-binding','crypto-revision-binding','crypto-key-binding','crypto-defensive-copy']),
 ('Transaction-local server-derived tenant/facility/patient/actor scope echo','TriageJdbcScope.java',['jdbc-scoped-lock','jdbc-scope-echo']),
 ('Restricted PostgreSQL role, RLS and SERIALIZABLE connection fence','TriageJdbcFence.java',['jdbc-no-autocommit','jdbc-serializable','jdbc-readonly','jdbc-role','jdbc-superuser','jdbc-bypassrls','jdbc-rls']),
 ('Transactional SQL statement/lock/idle timeout budget','TriageJdbcFence.java',['jdbc-budget']),
 ('Patient-scoped FOR UPDATE encrypted hydration and row/snapshot consistency','TriageJdbcStore.java',['jdbc-happy-path','jdbc-missing-row','jdbc-snapshot-tamper','jdbc-row-stage-mismatch']),
 ('Persisted audit tip and outbox link integrity precondition','TriageJdbcStore.java',['jdbc-missing-tip','jdbc-outbox-tip-mismatch','jdbc-duplicate-command']),
 ('Optimistic revision compare-and-swap guarded state update','TriageJdbcStore.java',['jdbc-cas','jdbc-stale-revision','jdbc-cas-race']),
 ('Transactional append of immutable hash-linked audit event','TriageJdbcStore.java',['jdbc-event','jdbc-event-failure']),
 ('Transactional outbox insertion linked to command/event digest','TriageJdbcStore.java',['jdbc-outbox','jdbc-outbox-failure']),
 ('Atomic commit/rollback failure handling with encrypted state writes','TriageJdbcStore.java',['jdbc-happy-path','jdbc-ciphertext','jdbc-invalid-mutation']),
 ('Trusted identity, patient isolation, and explicit write authorization fence','TriageJdbcStore.java',['jdbc-cross-tenant','jdbc-revoked-evidence','jdbc-no-manage-capability']),
 ('Quarantined encrypted snapshot schema, append-only audit triggers and static adversarial gate','TriageJdbcStore.java',['jdbc-happy-path']),
]
assert len(items)==15
results=[]
for n,(scope,src,scenarios) in enumerate(items,154):
 bid=f'P06-B{n:04d}'
 paths=[E/f'scenario-{s}.log' for s in scenarios]
 if n==168: paths+=[E/'v016-triage-sql.log',E/'migration-lock.log',E/'migration-checksum-tests.log']
 assert all(p.is_file() for p in paths),bid
 assert all('PASS' in p.read_text() or 'OK' in p.read_text() or '"locked": 16' in p.read_text() for p in paths),bid
 row={'batch_id':bid,'module':'M03','phase':'P06','scope':scope,
      'implementation':[base+src], 'focused_tests':[str(p.relative_to(ROOT)) for p in paths],
      'verified':True,'verification_scope':'synthetic_java_and_static_source_only',
      'live_postgresql_tested':False,'clinical_accepted':False}
 if n==168:row['implementation']+=['services/clinical-core/db/migration/V016__triage_encrypted_snapshot_quarantine.sql','tools/triage_encrypted_migration_gate.py','tests/test_triage_encrypted_migration_gate.py','registers/migration-sha256-lock.json']
 results.append(row)
 (E/f'{bid}.json').write_text(json.dumps(row,indent=2)+'\n')
ledger=json.loads((R/'batch-ledger.json').read_text());assert not any(x['id'] in {b['batch_id'] for b in results} for x in ledger['batches'])
for row in results:
 ledger['batches'].append({'id':row['batch_id'],'status':'internally_verified_synthetic_JDBC_NOT_live_postgres','scope':row['scope'],'module':'M03','phase':'P06','implementation':row['implementation'],'evidence':row['focused_tests']+[f'evidence/v520/{row["batch_id"]}.json'],'specified':True,'designed':True,'implemented':True,'tested':True,'integration_tested':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.52.0';ledger['next_batch']='P06-B0169';(R/'batch-ledger.json').write_text(json.dumps(ledger,indent=2)+'\n')
ph=json.loads((R/'phases.json').read_text());assert [p['batches_completed'] for p in ph['phases'] if p['id']=='P06']==[153]
for p in ph['phases']:
 if p['id']=='P06':p['batches_completed']=168;p['status']='triage_restricted_JDBC_adapter_synthetic_tested_NOT_live_postgresql';p['evidence'].append('evidence/v520/verified-batches.json')
ph['version']='0.52.0';ph['checkpoint']='v0.52.0';(R/'phases.json').write_text(json.dumps(ph,indent=2)+'\n')
(E/'verified-batches.json').write_text(json.dumps({'requested':60,'verified':15,'remaining':45,'percent_completed':25,'percent_remaining':75,'batches':results,'live_postgresql_tested':False,'clinical_release':'DENIED'},indent=2)+'\n')
blockers={'checkpoint':'v0.52.0','next_batch':'P06-B0169','release_gate':'DENIED','blockers':[
 {'id':'M03-GENESIS','status':'OPEN','detail':'No authorized encrypted triage intake genesis INSERT/event/outbox adapter; current JDBC mutation requires a pre-existing encrypted row. Do not deploy.'},
 {'id':'M03-DB-RLS','status':'OPEN','detail':'No live PostgreSQL restricted-role RLS/serializable transaction test. V016 remains deny-all quarantine; no application grants/policies.'},
 {'id':'M03-KMS','status':'OPEN','detail':'No production KMS, key rotation, key retention/erasure, backup encryption or keyId lookup attestation.'},
 {'id':'M03-OIDC','status':'OPEN','detail':'No production OIDC session verification, clinical privilege/consent engine integration, or authenticated HTTP endpoints.'},
 {'id':'M03-UX','status':'OPEN','detail':'No deployed web/native mobile triage screens, native Expo builds, EN/FR 200% visual acceptance, or device testing.'},
 {'id':'M03-CLINICAL','status':'OPEN','detail':'0/261 screens clinically accepted; 0/40 modules certified. No clinical hazard review or security approval.'}
 ]}
(R/'v520-blockers.json').write_text(json.dumps(blockers,indent=2)+'\n')
print('Verified batch register:',len(results),'next',ledger['next_batch'],'P06',168)
