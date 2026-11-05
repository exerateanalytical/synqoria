from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
read = lambda name: json.loads((root / name).read_text(encoding='utf-8'))
screens = read('registers/canonical-screens.json')['screen_register']
modules = read('registers/canonical-modules.json')['module_register']
routes = read('registers/screen-routes.json')
gaps = read('registers/operational-completion-gaps-v1.json')['capabilities']

assert len(screens) == len(routes['screens']) == len(gaps) == 261
assert len({x['id'] for x in screens}) == 261
assert len({x['screen_id'] for x in routes['screens']}) == 261
assert len({x['screenId'] for x in gaps}) == 261
assert {x['screen_id'] for x in routes['screens']} == {x['id'] for x in screens}
assert {x['screenId'] for x in gaps} == {x['id'] for x in screens}

states = {
    'domain_locked': 'route_registered_domain_adapter_missing',
    'scoped_read_only_partial': 'api_read_subset_not_clinically_complete',
    'functional_nonclinical_partial': 'nonclinical_functional',
}
by_id = {x['screenId']: x for x in gaps}
route_paths = set()
counts = {key: 0 for key in states}
for row in routes['screens']:
    entry = by_id[row['screen_id']]
    counts[entry['implementationState']] += 1
    assert row['module'] == entry['module']
    assert row['implementation_status'] == states[entry['implementationState']]
    assert row['clinical_accepted'] is False
    assert row['clinical_commands_enabled'] is False
    assert entry['domainWorkflowAccepted'] is False
    assert entry['clinicalCertification'] is False
    assert row['route'] == '/workforce/clinical/' + row['screen_id']
    assert row['route'] not in route_paths
    route_paths.add(row['route'])

assert counts == {
    'domain_locked': 240,
    'scoped_read_only_partial': 16,
    'functional_nonclinical_partial': 5,
}
assert routes['route_summary']['registered'] == 261
assert routes['route_summary']['domainLocked'] == 240
assert routes['route_summary']['apiReadSubsets'] == 16
assert routes['route_summary']['nonclinicalFunctional'] == 5
assert routes['route_summary']['clinicallyAccepted'] == 0
assert routes['route_summary']['clinicallyCertifiedModules'] == 0
assert len(routes['shell_roots']) == 3
assert {x['shell'] for x in routes['shell_roots']} == {'workforce', 'portal', 'platform'}
assert all(x['path'].startswith('/') and x['status'] == 'shell_source_only' for x in routes['shell_roots'])
assert len({x['path'] for x in routes['shell_roots']}) == 3
assert len({x['id'] for x in modules}) == 40
print('PASS: 261 unique navigable routes; 240 locked, 16 scoped reads, 5 nonclinical; no clinical completion claimed')
