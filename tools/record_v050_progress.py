"""Update truthful batch, phase, blocker and clinical-scope registers from verified evidence."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
def load(p):return json.loads((root/p).read_text())
def save(p,v):(root/p).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n')
report=load('evidence/batches/P01-P04-v050-ten-batches.json')
assert report['batches_passed']==10 and all(x['verified'] and x['exit_code']==0 for x in report['results'])
batches=[
 ('P01-B0004','Static-tested PostgreSQL V002 transactional outbox/inbox schema: composite tenant/facility FK, event uniqueness, pending index, forced default-deny RLS. SQL NOT executed.', ['services/clinical-core/db/migration/V002__outbox_inbox_foundation.sql','tests/test_sql_outbox.py']),
 ('P01-B0005','Static-tested PostgreSQL V003 master patient index, keyed identifier tokens, consent records, composite scope and forced default-deny RLS. SQL NOT executed.', ['services/clinical-core/db/migration/V003__patient_identity_consent_foundation.sql','tests/test_sql_identity.py']),
 ('P01-B0006','In-memory concurrent idempotency guard with tenant/facility/actor/operation/request scoping, fingerprint conflict and replay receipts. NOT durable.', ['services/clinical-core/src/main/java/org/synqoria/events/IdempotencyGuard.java','services/clinical-core/src/test/java/org/synqoria/events/IdempotencyGuardTest.java']),
 ('P01-B0007','In-memory outbox dispatch fencing/lease reference and JSON Schema event metadata contract; no durable dispatcher or broker.', ['services/clinical-core/src/main/java/org/synqoria/events/OutboxClaimLedger.java','services/clinical-core/src/test/java/org/synqoria/events/OutboxClaimLedgerTest.java','contracts/events/clinical-outbox-envelope-v1.schema.json','tests/test_event_envelope.py']),
 ('P02-B0005','Strict safe-route classifier for stable authorized roots and unimplemented detail/edit/review children; rejects malformed/sensitive paths. Presentation-only.', ['packages/ux-core/src/route-boundary.ts','packages/ux-core/tests/route-boundary.test.cjs']),
 ('P02-B0006','Async patient-switch coordinator requiring cache erasure before backend authorization, guarded unsaved drafts, denied/failure reset and late-result invalidation. Ports not wired to real UI.', ['packages/ux-core/src/patient-switch-coordinator.ts','packages/ux-core/tests/patient-switch-coordinator.test.cjs']),
 ('P03-B0004','Trusted-session admission boundary with pinned issuer/audience, time, MFA, revocation and authoritative grant port; verifier is a stub in tests, NO OIDC signature verification.', ['services/clinical-core/src/main/java/org/synqoria/security/TrustedSessionAdmission.java','services/clinical-core/src/test/java/org/synqoria/security/TrustedSessionAdmissionTest.java']),
 ('P03-B0005','Explicit consent scope predicate with tenant/facility/patient/purpose/operation/time and fresh revocation check; NOT legal or clinical approval.', ['services/clinical-core/src/main/java/org/synqoria/consent/ConsentScopePolicy.java','services/clinical-core/src/test/java/org/synqoria/consent/ConsentScopePolicyTest.java']),
 ('P04-B0002','In-memory facility lifecycle compare-and-swap revisions, approved transition guard and terminal retirement. No durable audit or DB integration.', ['services/clinical-core/src/main/java/org/synqoria/facility/FacilityLifecycle.java','services/clinical-core/src/test/java/org/synqoria/facility/FacilityLifecycleTest.java']),
 ('P04-B0003','Immutable trusted cell routing directory with tenant/facility/cell checks, active status, pinned HTTPS endpoints and deny-on-mismatch. No network/TLS or discovery.', ['services/clinical-core/src/main/java/org/synqoria/facility/CellRouteDirectory.java','services/clinical-core/src/test/java/org/synqoria/facility/CellRouteDirectoryTest.java']),
]
ledger=load('registers/batch-ledger.json');prior={x['id'] for x in ledger['batches']}
for bid,scope,paths in batches:
 assert bid not in prior and any(x['batch']==bid and x['verified'] for x in report['results'])
 for path in paths:assert (root/path).is_file(),path
 ledger['batches'].append({'id':bid,'status':'scope_limited_source_or_unit_verified',
  'scope':scope,'evidence':paths+['evidence/batches/P01-P04-v050-ten-batches.json','evidence/batches/P01-P04-v050-checks.json'],
  'clinical_screen_implementation':False,'integration_tested':False,'independently_reviewed':False,
  'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.5.0'
ledger['next_batch']='P01-B0002 remains BLOCKED (npm EAI_AGAIN); next independently unblocked P03-B0006 audit chain, P04-B0004 staff grant revocation; PostgreSQL V001–V003 must be executed and security reviewed before P01/P03/P04 phase gates. No canonical clinical screen implemented.'
ledger['release_status']='NOT_CERTIFIED'
save('registers/batch-ledger.json',ledger)
phases=load('registers/phases.json');phases['version']='0.5.0';phases['checkpoint']='10 scope-limited verified batches P01-B0004–B0007, P02-B0005–B0006, P03-B0004–B0005, P04-B0002–B0003; no phase certified'
for phase in phases['phases']:
 inc={'P01':4,'P02':2,'P03':2,'P04':2}.get(phase['id'],0)
 if inc:
  phase['batches_completed']+=inc
  phase['evidence'].append('evidence/batches/P01-P04-v050-ten-batches.json')
  phase['certification']='not_certified';phase['status']='in_progress'
save('registers/phases.json',phases)
blockers=load('registers/blockers.json');blockers['checkpoint']='v0.5.0'
for b in blockers['blockers']:
 if b['id']=='BLK-001':b['issue']='npm registry DNS EAI_AGAIN reconfirmed in v0.5.0; no Next/Expo dependency install, lockfile or app builds. See P01-B0002-npm-ping-v050.json.'
 if b['id']=='BLK-002':b['issue']='Connected GitHub installation repository search for synqoria returned zero repositories during v0.5.0; no push/CI verified.'
 if b['id']=='BLK-005':b['issue']='No PostgreSQL, OIDC verifier, patient repository or hospital-cell infrastructure provisioned; V001–V003 migrations static-tested only; Java admission uses verifier stub.'
 if b['id']=='BLK-008':b['issue']='Patient-switch async coordinator, trusted admission, consent, idempotency and facility/cell domain code pass unit tests but are NOT integrated with Next/Expo, HTTP, OIDC or PostgreSQL.'
blockers['blockers'].append({'id':'BLK-009','severity':'clinical_data_integrity','affects':['P01','P03','P04','P05','P19'],
 'issue':'In-memory idempotency, outbox fencing and facility lifecycle cannot survive restart/multi-node concurrency. No authoritative transaction, durable audit or exactly-once effect has been demonstrated.',
 'mitigation':'Implement transactional PostgreSQL repository with revision/idempotency constraints, outbox/inbox transaction coupling, crash/retry tests, security review and failure injection.',
 'status':'open'})
blockers['blockers'].append({'id':'BLK-010','severity':'identity_security','affects':['P03','P05','P19'],
 'issue':'TrustedSessionAdmission test uses stub identity verifier. Real JWT/OIDC signature, JWKS key rotation, session revocation, MFA binding and policy provenance remain unimplemented.',
 'mitigation':'Select version-pinned supported OIDC library, integrate trusted provider and test forged/expired/wrong-audience/wrong-key/revoked sessions against actual HTTP endpoints.',
 'status':'open'})
save('registers/blockers.json',blockers)
assert len([x for x in ledger['batches'] if x['id'] in {b[0] for b in batches}])==10
print('PASS: 10 scope-limited verified batches appended; phases and blockers updated; release fail-closed')
