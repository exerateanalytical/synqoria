#!/usr/bin/env python3
"""P01-B0032. Release gate must refuse any request without external evidence and native builds."""
import argparse,json,sys
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--check-locked',action='store_true');p.add_argument('--request-release',action='store_true');args=p.parse_args()
ledger=json.loads((r/'registers/batch-ledger.json').read_text())
screens=json.loads((r/'registers/screen-status-matrix.json').read_text())['items']
modules=json.loads((r/'registers/module-status-matrix.json').read_text())['items']
blockers=json.loads((r/'registers/blockers.json').read_text())['blockers']
assert len(screens)==261 and len(modules)==40
assert ledger['release_status']=='NOT_CERTIFIED'
assert any(not x['accepted'] for x in screens)
assert any(b.get('status')=='open' for b in blockers)
if args.request_release:
 print('DENIED: clinical signoff, native builds, PostgreSQL RLS and 261 screen acceptance incomplete',file=sys.stderr)
 sys.exit(3)
print('PASS: fail-closed release lock verified against 40 modules / 261 screens; NO certification')
