"""Verify source-integrated screen status separately from institutional acceptance."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
load=lambda x:json.loads((root/'registers'/x).read_text())
s=load('screen-status-matrix.json')['items']
m=load('module-status-matrix.json')['items']
c=load('canonical-screens.json')['screen_register']
cm=load('canonical-modules.json')['module_register']
routes=load('screen-routes.json')['screens']
assert len(s)==len(c)==len(routes)==261
assert len(m)==len(cm)==40
assert [x['id'] for x in s]==[x['id'] for x in c]
assert [x['id'] for x in m]==[x['id'] for x in cm]
state={x['screen_id']:x for x in routes}
integrated={'source_api_workflow_ci_verified_unaccepted','existing_module_source_integrated_unaccepted'}
for item in s:
    route=state[item['id']]
    assert item['specified']=='baseline'
    assert item['designed']==(route['implementation_status'] in integrated)
    assert item['implemented']==(route['implementation_status'] in integrated)
    assert item['tested']==(route['implementation_status']=='source_api_workflow_ci_verified_unaccepted')
    assert not any(item[k] for k in ('independently_reviewed','clinically_reviewed','accepted'))
    assert route['clinical_accepted'] is False and route['clinical_commands_enabled'] is False
for item in m:
    assert item['specified']=='baseline'
    assert not any(item[k] for k in ('designed','implemented','tested','independently_reviewed','clinically_reviewed','accepted'))
assert sum(x['implemented'] for x in s)==86
assert sum(x['tested'] for x in s)==16
print('PASS: source integration and institutional acceptance remain separate; 86 implemented sources, zero clinical approvals')
