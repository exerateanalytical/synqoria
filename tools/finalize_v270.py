#!/usr/bin/env python3
"""Advance registers only for actual passed v0.27.0 batch evidence."""
import pathlib,json,datetime,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1];R=ROOT/'registers';E=ROOT/'evidence';V='0.27.0'
def read(p):return json.loads(p.read_text())
def write(p,x):p.write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n')
result=read(E/'batches/P01-P02-P05-v270-ten-batches.json')
assert result['completed']==10 and len(result['batches'])==10 and all(x['tested'] and x['exit_code']==0 for x in result['batches'])
ledger=read(R/'batch-ledger.json');known={b['id'] for b in ledger['batches']}
for batch in result['batches']:
 assert batch['id'] not in known,batch['id']
 ledger['batches'].append({'id':batch['id'],'status':'implemented_and_unit_or_static_verified_NOT_production_integrated','scope':batch['scope'],'evidence':['evidence/batches/P01-P02-P05-v270-ten-batches.json',f'evidence/batches/{batch["id"]}-v270.log'],'external_certification':False,'specified':True,'designed':True,'implemented':True,'tested':True,'integration_tested':False,'independently_reviewed':False,'clinically_reviewed':False,'accepted':False})
ledger['version']=V;ledger['release_status']='NOT_CERTIFIED';ledger['next_batch']='P05-B0027 BLOCKED pending real PostgreSQL restricted-role/RLS. Next independent: P01-B0043 verified npm lock creation and actual Next/Expo builds; P02-B0093 live app navigation and accessibility.'
write(R/'batch-ledger.json',ledger)
ph=read(R/'phases.json');ph['version']=V;ph['checkpoint']='v'+V
for p in ph['phases']:
 p['batches_completed']=sum(x['id'].startswith(p['id']+'-') and not x['status'].startswith('blocked') for x in ledger['batches'])
 if p['id']!='P00':p['certification']='not_certified'
write(R/'phases.json',ph)
for name in ('module-status-matrix.json','screen-status-matrix.json'):
 d=read(R/name);d['version']=V;assert len(d['items'])==(40 if name.startswith('module') else 261)
 assert all(not x['implemented'] and not x['tested'] and not x['accepted'] for x in d['items']);write(R/name,d)
block=read(R/'blockers.json');block['checkpoint']='v'+V;block['last_reviewed_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
block['blockers']+=[
 {'id':'BLK-V270-BUILDS','severity':'engineering','affects':['P01-B0043','P02-B0093','P19'],'issue':'Exact npm manifest pins now audited but registry EAI_AGAIN; no actual root package-lock, installed Next/Expo packages or compiled apps. Build runner exited 4 BLOCKED, zero builds attempted. Expo dependency compatibility unverified.','mitigation':'In network-enabled trusted CI generate and review npm lockfile, run lock integrity audit, npm ci, Next build and both Expo exports; capture real logs and signed artifact provenance.','status':'open'},
 {'id':'BLK-V270-POSTGRES','severity':'safety','affects':['P05-B0027','P05-B0062','P05-B0063','P19'],'issue':'Reviewer event reconciliation and replay probes tested only with synthetic JDBC proxy; no live PostgreSQL, restricted role, RLS, schema deployment or race/rollback verification.','mitigation':'DBA review V008–V011 quarantined migrations, provision isolated PostgreSQL restricted role, test tenant and patient isolation, unique command replay and atomic audit/outbox under concurrency before enabling any write.','status':'open'},
 {'id':'BLK-V270-VISUAL','severity':'engineering','affects':['P02-B0089','P02-B0090','P02-B0091','P02-B0092','P19'],'issue':'Chromium passed static specimen tests at nine widths and 200% text; real compiled Next application, device screen reader, native More and Inter font asset acceptance remain unverified.','mitigation':'Run compiled Next/Expo application and Android/iOS accessibility/zoom/keyboard screenshots, compare with UX01–UX10 before phase certification.','status':'open'}]
write(R/'blockers.json',block)
original=read(E/'CUMULATIVE_FILE_SHA256.json')['files']
changes=[]
for f in ROOT.rglob('*'):
 if not f.is_file() or any(x in {'.git','node_modules','.next','.expo','__pycache__'} for x in f.parts) or f.suffix in ('.class','.pyc'):continue
 rel=f.relative_to(ROOT).as_posix()
 if rel=='evidence/CUMULATIVE_FILE_SHA256.json':continue
 digest=hashlib.sha256(f.read_bytes()).hexdigest()
 if original.get(rel)!=digest:changes.append({'path':rel,'change':'modified' if rel in original else 'added','sha256':digest})
write(E/'v270-changed-files.json',{'source':'v0.26.0 verified 970-entry manifest','version':V,'changed':changes,'changed_count':len(changes),'removed':sorted(set(original)-{p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file()})})
print('PASS v0.27.0 registers; 10 tested batches; 40 modules, 261 screens, no clinical release; changed files',len(changes))
