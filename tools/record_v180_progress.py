from pathlib import Path
import json,datetime
r=Path(__file__).resolve().parents[1]
def read(path):return json.loads((r/path).read_text())
def write(path,obj):(r/path).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n')
e=read('evidence/batches/P02-P03-v180-ten-batches.json');assert e['batches_passed']==10
ledger=read('registers/batch-ledger.json');phases=read('registers/phases.json'); blockers=read('registers/blockers.json')
existing={x['id'] for x in ledger['batches']}
for row in e['results']:
 assert row['batch'] not in existing and row['verified'] and row['exit_code']==0
 assert (r/row['evidence_log']).is_file() and 'exit=0' in (r/row['evidence_log']).read_text()
 ledger['batches'].append({'id':row['batch'],'status':'implemented_unit_or_static_tested_NOT_integrated','scope':row['scope'],
  'evidence':[row['source'],row['evidence_log']],'verification_level':row['verification_level'],
  'clinical_screen_implementation':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False,'external_certification':False})
ledger['version']='0.18.0'
ledger['next_batch']='P05-B0027 blocked: restricted-role PostgreSQL reviewer approval/revocation/outbox/audit with actual isolation/concurrency tests; next independently unblocked P03-B0029 vetted JOSE JSON parser + trusted IdP/JWKS integration (pending dependencies), or P02-B0053 UI accessibility safeguards.'
ledger['release_status']='NOT_CERTIFIED'
write('registers/batch-ledger.json',ledger)
phases['version']='0.18.0';phases['checkpoint']='v0.18.0: 5 independently unit-tested OIDC boundary primitives + 5 mobile UX/privacy components; no production JWT parser, clinical API, database or device builds'
for phase in phases['phases']:
 phase['batches_completed']=sum(x['id'].startswith(phase['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 if phase['id'] in ['P02','P03']:
  phase['status']='in_progress';phase['certification']='not_certified'
  phase['evidence'].append('evidence/batches/P02-P03-v180-ten-batches.json')
write('registers/phases.json',phases)
for filename,expected in [('module-status-matrix.json',40),('screen-status-matrix.json',261)]:
 data=read('registers/'+filename);assert len(data['items'])==expected
 assert all(not row['implemented'] and not row['clinically_reviewed'] and not row['accepted'] for row in data['items'])
 data['version']='0.18.0';write('registers/'+filename,data)
blockers['checkpoint']='v0.18.0'
blockers['note']='v0.18.0: ten local security/UX foundation batches pass. No live PostgreSQL, OIDC/JWKS JSON parser, Next/Expo builds, or clinical approval.'
blockers['blockers'].append({'id':'BLK-V180-OIDC-BINDING','severity':'security','affects':['P03-B0029','P05','P19'],
 'issue':'v0.18.0 tests operator-pinned RSA key construction, protected-header policy, cryptographic JWS payload bytes and separately trusted claims, but DOES NOT have a vetted duplicate-rejecting JSON parser, signed-byte-to-claims binding, real IdP/JWKS, MFA, grant authority or revocation backend. No production JWT admission.',
 'mitigation':'Install and pin supported JOSE/JWT library and IdP in network-enabled build; bind claims strictly to verified payload bytes, enforce tenant/facility grants and independently test rotation, revocation and token tampering.',
 'status':'open'})
write('registers/blockers.json',blockers)
pkg=read('package.json');pkg['version']='0.18.0';write('package.json',pkg)
(r/'README.md').write_text((r/'README.md').read_text().replace('v0.17.0','v0.18.0').replace('0.17.0','0.18.0'))
(r/'docs/program/STATUS.md').write_text('''# SYNQORIA — Engineering checkpoint v0.18.0

**Base:** SHA-256 verified v0.17.0 cumulative ZIP `6dd49db507e2483d0abcb52bda7b08c8a131122e045bf7d0c33fd7c21d3b9113`, 450/450 cumulative manifest entries matched and 19/19 immutable baseline seed entries matched.

**Implemented and individually verified this run:** 10/10 foundation batches. `P03-B0024`–`P03-B0028` (five Java security components), `P02-B0048`–`P02-B0052` (five TypeScript navigation, privacy and native-source presentation components). Each batch has its own executed test log and exit code. See `evidence/batches/P02-P03-v180-ten-batches.json`.

## Actual verification

- **New Java:** 38 passing assertions across five separately invoked Java test programs, JDK 21. The first Java compilation attempt failed with a generic type mismatch; fixed and recompiled successfully. See run log.
- **New TypeScript:** five individually invoked Node tests with independently recorded exit codes. Full TypeScript regression and source checks documented in `evidence/batches/v180-*.log`.
- **Native app:** new readonly registration journey in staff preview and accessible disabled tabs in both native source shells; **source/TSX parse only**, no Expo/native build.
- **Database:** PostgreSQL executable/service unavailable; P05-B0027 is BLOCKED and not counted. No real RLS or reviewer transactions.
- **Security:** signed payload bytes are verified against a pinned RSA key; no vetted JSON/JWT parser or claims-to-signed-bytes binding, production IdP, JWKS rotation service or grant authority. No production OIDC admission.
- **Clinical:** 40/40 canonical module IDs and 261/261 canonical screen IDs preserved; **0/261 clinical screens implemented, independently reviewed, clinically approved or accepted.** No phase certified. Registration writes remain locked.
- **GitHub:** no verified SYNQORIA repository or push; Library ZIP is authoritative.

## Exact next batch

`P05-B0027`: restricted-role PostgreSQL atomic reviewer approval/revocation + audit/outbox, RLS tenant isolation and concurrency tests, **blocked** until PostgreSQL. Next independently unblocked work `P03-B0029` trusted JOSE JSON parser/IdP binding when dependencies are available, or `P02-B0053` further safe native accessibility work.

**Release status: NOT_CERTIFIED. No clinical use.**
''')
(r/'docs/program/V018_SECURITY_AND_MOBILE_REVIEW.md').write_text('''# v0.18.0 — Security and Mobile Implementation Review

## Implemented

1. **P03-B0024:** operator-pinned RFC 7638 RSA JWK thumbprint validation; no trust of token-provided keys.
2. **P03-B0025:** exact three-property protected-header policy for RS256 `at+jwt`, trusted key ID; rejects algorithm confusion, `jku`, `crit` and injected key material.
3. **P03-B0026:** byte-level JWS signature verification with active/revoked key metadata and exact externally parsed header byte match. Returns signed **bytes only**, not authenticated claims or access grants.
4. **P03-B0027:** structurally validates separately trusted claims (issuer, audience, azp, subject, session, token ID, time). Not connected to verified bytes and MUST NOT be used for production authentication.
5. **P03-B0028:** PHI-free security-denial audit metadata with keyed HMAC session pseudonym. Not a durable, hospital-approved audit custody service.
6. **P02-B0048:** safe staff root navigation intent allowlist; never authorizes clinical deep links.
7. **P02-B0049:** foreground generation reauthorization; denies stale/offline/revoked views.
8. **P02-B0050:** bilingual generic device notification content, empty data payload.
9. **P02-B0051:** bilingual readonly four-stage registration journey in native staff preview.
10. **P02-B0052:** four labelled, disabled and accessible mobile destinations in staff and patient native source shells.

## Review limitations

- Five Java unit programs and five TypeScript Node tests passed individually; detailed logs in `evidence/batches/v180-*.log`. No independent security audit.
- **Never** treat P03-B0024–B0027 as a JWT verifier: a vetted duplicate-key-rejecting JSON parser, claims-to-signed-byte binding, trusted IdP/JWKS metadata, session revocation and live grants are missing. A malicious caller could supply unrelated parsed claims if these components were miswired.
- React Native/Expo and Next.js compilation, devices, screen readers and PostgreSQL were unavailable; native wiring was checked by TSX syntax and static source tests only.
- All clinical actions, reviewer approval and registration POST remain fail-closed. No approved protocols or medication logic generated.
- Source IDs and 40/261 inventory unchanged; no clinical screen marked implemented. All phases remain uncertified.

## Exit gates and next

Clinical and production release require real PostgreSQL restricted-role RLS and serializable transaction tests, supported JOSE library with trusted provider, compiled web/mobile binaries, device/accessibility checks, human clinical/security sign-off and pilot qualification. Resume P05-B0027 only with those database prerequisites; otherwise continue independent safety and UX work.
''')
print('Updated version/status registers and reviews; batches:',len(ledger['batches']))
