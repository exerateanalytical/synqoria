from pathlib import Path
from collections import Counter
import json
root=Path(__file__).resolve().parents[1]
mods=json.loads((root/'registers/canonical-modules.json').read_text())['module_register']
screens=json.loads((root/'registers/canonical-screens.json').read_text())['screen_register']
phases=json.loads((root/'registers/phases.json').read_text())['phases']
assert [m['id'] for m in mods]==[f'M{i:02d}' for i in range(1,41)]
assert len(screens)==261 and len({s['id'] for s in screens})==261
ct=Counter(s['module'] for s in screens)
assert ct['SHARED']==12
assert set(ct)-{'SHARED'}=={m['id'] for m in mods}
assert sum(ct[m['id']] for m in mods)==249
assert len(phases)==20 and [p['id'] for p in phases]==[f'P{i:02d}' for i in range(20)]
coverage=[m for p in phases for m in p['modules']]
assert len(coverage)==40 and set(coverage)=={m['id'] for m in mods}
assert len(set(coverage))==40
assert all(s['code_status']=='not_implemented' for s in screens)
print('PASS: planning integrity, 40 unique modules, 261 unique canonical screens, 20 phases, non-fabricated code statuses')
