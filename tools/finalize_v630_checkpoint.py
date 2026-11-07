#!/usr/bin/env python3
"""Reconcile proven P07 outbox work into cumulative registers; do not assert clinical acceptance."""
import json,hashlib
from pathlib import Path
R=Path(__file__).resolve().parents[1]
summary=json.loads((R/'evidence/v630/individual-batches.json').read_text())
assert summary['passed']==11 and len(summary['batches'])==11
assert all(b['passed'] and b['exit_code']==0 and (R/b['log']).is_file() and hashlib.sha256((R/b['log']).read_bytes()).hexdigest()==b['sha256'] for b in summary['batches'])
reg=json.loads((R/'evidence/v630/regression-matrix.json').read_text())
assert reg['total']==17 and reg['passed']==17 and all(x['passed'] for x in reg['checks'])
ledger=R/'registers/batch-ledger.json';data=json.loads(ledger.read_text());existing={x['id'] for x in data['batches']}
assert len(existing)==len(data['batches'])
for b in summary['batches']:
 assert b['id'] not in existing
 data['batches'].append({'id':b['id'],'module':b['module'],
  'scope':b['implemented'],'status':'implemented_and_individually_tested_synthetic_protocol_NOT_deployed',
  'evidence':[b['log'],'evidence/v630/individual-batches.json'],
  'integration_tested':'synthetic_JDBC_and_domain_only','real_postgresql_RLS':False,
  'production_broker_connected':False,'external_certification':False,'clinical_screen_accepted':False})
assert len(data['batches'])==len(existing)+11
data['version']='0.63.0';ledger.write_text(json.dumps(data,indent=2)+'\n')
phases=R/'registers/phases.json';p=json.loads(phases.read_text());p['version']='0.63.0';p['checkpoint']='v0.63.0_P07_outbox_draft_unmerged'
p07=next(x for x in p['phases'] if x['id']=='P07');assert p07['batches_completed']==93
p07['batches_completed']=104;p07['status']='clinical_domain_HTTP_and_outbox_synthetic_integration_NOT_production_qualified'
p07['evidence'].extend(['evidence/v630/individual-batches.json','evidence/v630/regression-matrix.json','tests/test_v630_outbox.py'])
assert len(p['phases'])==20
phases.write_text(json.dumps(p,indent=2)+'\n')
for file in ['registers/module-status-matrix.json','registers/screen-status-matrix.json']:
 path=R/file;o=json.loads(path.read_text());o['version']='0.63.0'
 assert len(o['items'])==(40 if 'module' in file else 261)
 assert all(not x['accepted'] for x in o['items'])
 path.write_text(json.dumps(o,indent=2)+'\n')
for file in ['package.json','apps/web/package.json','apps/staff-mobile/package.json','apps/patient-mobile/package.json','packages/ux-core/package.json']:
 path=R/file;o=json.loads(path.read_text());assert o.get('version')=='0.62.0',file;o['version']='0.63.0'
 path.write_text(json.dumps(o,indent=2)+'\n')
(R/'registers/v630-implementation-batches.json').write_text(json.dumps({'version':'0.63.0','phase':'P07',
 'previous_verified_latest':'P07-B0093','last_verified':'P07-B0104','implemented':11,
 'target_remaining_before':77,'target_remaining_after':66,'new_batches':summary['batches'],
 'clinical_certification':'DEFERRED','deployed_runtime':'NOT_VERIFIED'},indent=2)+'\n')
(R/'docs/engineering/P07_LONGITUDINAL_OUTBOX_v0.63.0.md').write_text('''# SYNQORIA — P07 M04–M06 clinical outbox delivery v0.63.0

**Engineering build, NOT production authorization or clinical certification.**

## Implemented in this branch

- Typed, bounded, metadata-only event reference with scope, revision, command ID, event hash and event type. Patient UUIDs remain sensitive metadata even without clinical free text.
- A host-verifiable dispatcher permit requirement with facility/patient/encounter scoping and expiry. **Permit creation is not an authentication system**; production workload identity and scoped database permissions are missing.
- One-encounter-at-a-time ordered event claims, single-worker fencing with a random lease token, clock-derived 30-second claims, and optimistic acknowledgement with digest and scope checks.
- At-least-once semantics: success followed by a lost ACK triggers re-delivery; private Kafka consumers must deduplicate by event hash + command ID. No exactly-once guarantee.
- Bounded retry delays, permanent parking after attempt 12 and a blocked subsequent revision until an authorized reconciliation process is implemented. Expired final leases become parked.
- JDBC transaction-local scope echo, short timeouts, bounded seed/projection, ordered `FOR UPDATE SKIP LOCKED` claim, CAS ACK and retry; broker publish is outside the DB transaction.
- V021 quarantined schema has enforced RLS, no runtime policies, grants, broker configuration, KMS or SQL execution evidence. It cannot be deployed with operational permissions yet.

## Executed evidence

- 11 individually executed Java/JDBC/unittest scenarios, `evidence/v630/P07-B0094.log` through `P07-B0104.log`.
- 17/17 source, security, historical clinical, OpenAPI, schema, migration and UX regression groups.
- 65 synthetic delivery assertions and 175 synthetic JDBC assertions. A fake transactional JDBC protocol does NOT demonstrate PostgreSQL row locking, isolation or row-level security.
- Historical M04/M05/M06 domain and HTTP tests re-passed; canonical module 40, screen 261, clinical acceptance 0.

## Open integration gates (do not silently bypass)

1. Provision a real PostgreSQL database, restricted roles and evidence-backed RLS for V020/V021 under separate reviewed migration.
2. Verify row-level tenant/facility/patient/encounter isolation and concurrent workers under PostgreSQL, not a proxy.
3. Bind dispatcher permits to mTLS/OIDC workload identity with revocation; require a private broker with no clinical free text in logs.
4. Implement consumer-side inbox deduplication, DLQ review/maker-checker redrive, observability with privacy filtering and alarm policies.
5. Perform deployment/mobile builds and required clinical and independent security certification later.

**Next candidate:** P07-B0105, check fresh GitHub/main/PR stack and other-chat reservations first. Parallel P06 starts B0392 if still unreserved.
''')
(R/'SYNQORIA_STATUS_v0.63.0.md').write_text('''# SYNQORIA v0.63.0 — 11 verified P07 event-delivery integration batches

**Base:** v0.62.0 stacked draft PR #5; next new branch should stack on that source, not replace `main`.

**This run:** P07-B0094–B0104 **11/11 implemented and individually executed checks passed**. Earlier 100-batch request had 77 pending; **66 still pending** (cumulative 34/100, excluding unverified work). Next P07 candidate B0105.

**Functions:** private outbox metadata; scoped and expiring dispatch permits; ordered per-encounter delivery, single claims and row fencing; acknowledgement CAS; bounded retries and terminal parking; uncertain broker ACK recovery; PostgreSQL-scope SQL adapter with transaction boundaries; V021 forced-RLS no-grant quarantine. No consumer inbox or actual Kafka/RLS deployment implemented.

**Tests:** 17/17 engineering regression groups (including 296/296 previous UX tests) and 11/11 focused batch tests; 65 synthetic dispatcher assertions; 175 synthetic JDBC assertions; Java 21 compilation. All evidence under `evidence/v630`.

**Authority:** 40 modules, 261 screens unchanged, 0 clinically accepted or certified. Formal certification deferred per owner; clinical release DENIED.

**Pending:** live PostgreSQL with authenticated RLS roles, production Keycloak/OIDC/KMS, Kafka broker/consumer inbox, recovery redrive governance, Next/Expo production builds, clinician approvals.

**Cumulative ZIP must retain all v0.62.0 archive paths**, and must not overwrite the shared Library current checkpoint or GitHub main ahead of parallel reconciliation. GitHub push status is verified separately, not by this status document.
''')
print('Finalized',len(data['batches']),'total ledger records; P07',p07['batches_completed'])
