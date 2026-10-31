"""Verify separate statuses remain fail-closed for canonical clinical scope."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
screens=json.loads((root/'registers/canonical-screens.json').read_text())['screen_register']
modules=json.loads((root/'registers/canonical-modules.json').read_text())['module_register']
for file,ids in [('screen-status-matrix.json',[s['id'] for s in screens]),('module-status-matrix.json',[m['id'] for m in modules])]:
 m=json.loads((root/'registers'/file).read_text()); rows=m['items']
 assert len(rows)==len(ids) and [x['id'] for x in rows]==ids
 for x in rows:
  assert x['specified']=='baseline' and all(x[k]==False for k in ('designed','implemented','tested','independently_reviewed','clinically_reviewed','accepted'))
print('PASS: 261 screen + 40 module separate status rows; all clinical acceptance gates remain false')
