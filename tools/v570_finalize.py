#!/usr/bin/env python3
"""Update v0.57 engineering checkpoint, not a clinical certification pipeline."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
def read(p):return json.loads((ROOT/p).read_text())
def write(p,x):(ROOT/p).write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n')
version='0.57.0'
for p in ['package.json','packages/ux-core/package.json','apps/web/package.json','apps/staff-mobile/package.json','apps/patient-mobile/package.json']:
 path=ROOT/p
 content=path.read_text().replace('0.56.0','0.57.0')
 path.write_text(content)
for p in ['apps/web/next.config.ts','apps/staff-mobile/app.json','apps/patient-mobile/app.json']:
 path=ROOT/p
 if '0.56.0' in path.read_text():path.write_text(path.read_text().replace('0.56.0','0.57.0'))
readme=ROOT/'README.md';s=readme.read_text();s=s.replace('v0.56.0','v0.57.0',2)
s=s.replace('with **0/261 clinically implemented or accepted**','with **0/261 clinically accepted screens** (engineering source work is distinct from acceptance)')
s='> **Current internally verified source checkpoint: v0.57.0**. Certification work is deferred at user direction; security/clinical release gates stay closed.\n\n'+s[s.find('# SYNQORIA'):]
s+='\n## v0.57.0 — Patient-scoped triage read/workspace integration\nSix focused implementation batches P06-B0363–B0368 provide strict summary and paged audit GET transport, volatile case context, scoped worklists, bounded audit pagination, and OpenAPI parity. A staff React component was authored and syntax-reviewed but is not counted as application-build verified. See docs/engineering/M03_TRIAGE_v0.57.0_INTERNAL.md and evidence/v570/.\n'
readme.write_text(s)
phases=read('registers/phases.json');phases['version']=version;phases['checkpoint']='v0.57.0';phase=next(p for p in phases['phases'] if p['id']=='P06');phase['batches_completed']=368;phase['status']='in_progress__M03_scoped_read_workflow_implemented__live_integrations_not_tested';phase['certification']='deferred_pending_separate_future_stage__not_waived';phase['evidence'].append('evidence/v570/batch-execution.json');write('registers/phases.json',phases)
work=[
 ('P06-B0363','Authenticated patient/case summary GET transport and exact privacy-minimal decoder; reject malformed responses/denied scope','packages/ux-core/src/triage-client.ts','packages/ux-core/tests/triage-v57-read-session.test.cjs'),
 ('P06-B0364','Bounded audit-history GET transport and strict revision cursor/event decoding','packages/ux-core/src/triage-client.ts','packages/ux-core/tests/triage-v57-read-session.test.cjs'),
 ('P06-B0365','Volatile, abortable patient-scoped triage case session with optimistic revisions, replay guard and authoritative refresh','packages/ux-core/src/triage-case-session.ts','packages/ux-core/tests/triage-v57-read-session.test.cjs'),
 ('P06-B0366','Explicit-case scoped staff worklist selection with switching and stale request invalidation','packages/ux-core/src/triage-worklist-session.ts','packages/ux-core/tests/triage-v57-workspace.test.cjs'),
 ('P06-B0367','Bounded read-only audit pager with patient-switch invalidation and no offline storage','packages/ux-core/src/triage-audit-pager.ts','packages/ux-core/tests/triage-v57-workspace.test.cjs'),
 ('P06-B0368','Versioned OpenAPI parity for M03 authorized case and bounded history reads','contracts/openapi/synqoria-m03-triage-restricted-v1.yaml','tests/test_triage_v570_read_contract.py')]
ledger=read('registers/batch-ledger.json');old_ids={b['id'] for b in ledger['batches']};assert all(b[0] not in old_ids for b in work)
for id,desc,source,test in work:
 ledger['batches'].append({'id':id,'status':'implemented_and_tested_internal','scope':desc,'code':[source], 'evidence':[test,'evidence/v570/batch-execution.json'],'external_certification':False,'clinical_acceptance':False})
ledger['version']=version;write('registers/batch-ledger.json',ledger)
write('registers/v570-implementation-batches.json',{'checkpoint':'v0.57.0','parent':'v0.56.0','requested_target':60,'verified_implementation_batches':6,'not_completed':54,'batch_range':[work[0][0],work[-1][0]], 'certification_work':'deferred_not_waived', 'batches':[{'id':id,'scope':desc,'source':src,'test':test,'status':'tested_not_certified'} for id,desc,src,test in work], 'partial_unverified_ui':['apps/web/components/TriageCaseWorkspace.tsx','apps/web/app/globals.css']})
blockers=read('registers/blockers.json');blockers['checkpoint']='v0.57.0';write('registers/blockers.json',blockers)
(ROOT/'docs/engineering/M03_TRIAGE_v0.57.0_INTERNAL.md').write_text('''# SYNQORIA M03 v0.57.0 — Staff triage scoped read and workflow integration

**Status:** six internally tested implementation batches; release and formal certification are deferred, not bypassed.

## Implemented

- P06-B0363: HTTPS authenticated case summary GET, server-enforced patient+case route, exact summary decoder, no-store cache checks and response budget.
- P06-B0364: bounded revision-based audit history reader with exact fields, monotonic revisions, strict hash and operation validation, no notes.
- P06-B0365: volatile case session that aborts stale requests on patient switches, never caches identity or tokens, prevents concurrent writes and refreshes authoritative revisions.
- P06-B0366: explicit-patient worklist selection, at most 50 IDs, membership validation, cancellation and clean isolation on patient switches.
- P06-B0367: audit history pager for up to 1,000 entries held only in memory, bounded server pages and context invalidation.
- P06-B0368: OpenAPI GET case/history contract matching Java HTTP response shape and TS decoders.

Additional **source-only, NOT build-verified** M03 staff React case workspace built in `apps/web/components/TriageCaseWorkspace.tsx` with EN/FR text, accessible buttons, quick actions before history, and approved Warm Clinical semantic colors. It is opt-in via an authenticated session provider; the unauthenticated shell remains locked. This source-only UI component does not count as a seventh verified batch.

## Safety invariants preserved

The server remains the authority for tenant, facility, patient, actor permissions and action admissibility. The client cannot infer patient status or clinical priority, cannot queue offline clinical writes and cannot expose a record without a verified session. After any successful mutation, the client must reread the server case before allowing another action. Both transport and state layers deny/hide incorrect context or revision.

## Known engineering limitations

No installed Next.js or Expo dependency graph or device binary for this execution; no provider-backed production OIDC session; no live PostgreSQL restricted-role test; no runtime end-to-end browser access to authenticated PHI. No external certification, pilot qualification or clinical approval attempted at user direction. Keep deployment and clinical release denied until later separate review.

## Evidence

See `evidence/v570/batch-execution.json`, six individual focused logs, `check-ux.log`, `check-registers.log`, `check-source.log`, `check-openapi.log`, `javac-full.log`, `java-read-http.log`, source integrity manifests and cumulative ZIP SHA256.
''')
# Authoritative documentation is never overwritten; only execution cadence changes.
phase_doc=ROOT/'docs/program/PHASES.md';s=phase_doc.read_text()
s=s.replace('Target up to twenty meaningful, independently verified batches per run', 'Target up to sixty meaningful, independently verified implementation batches per run')
phase_doc.write_text(s)
print('Checkpoint metadata and six implementation batch entries updated')
