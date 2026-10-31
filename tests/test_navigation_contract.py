from pathlib import Path
import json, re
root=Path(__file__).resolve().parents[1]
screens=json.loads((root/'registers/canonical-screens.json').read_text())['screen_register']
modules=json.loads((root/'registers/canonical-modules.json').read_text())['module_register']
route=json.loads((root/'registers/screen-routes.json').read_text())
assert len(route['screens'])==261
assert {x['screen_id'] for x in route['screens']}=={x['id'] for x in screens}
assert all(x['implementation_status']=='not_implemented' and x['route'] is None for x in route['screens'])
assert len(route['shell_roots'])==3
assert {x['shell'] for x in route['shell_roots']}=={'workforce','portal','platform'}
assert all(x['path'].startswith('/') and x['status']=='shell_source_only' for x in route['shell_roots'])
assert len({x['path'] for x in route['shell_roots']})==3
assert len({x['id'] for x in modules})==40
print('PASS: 261 honest screen route records, 3 shell roots, 40 module IDs; no false canonical screen implementation claims')
