"""Verify canonical routes against current source state and historical screen-gaps checkpoint."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
read=lambda f:json.loads((root/f).read_text(encoding='utf-8'))
screens=read('registers/canonical-screens.json')['screen_register']
modules=read('registers/canonical-modules.json')['module_register']
routes=read('registers/screen-routes.json')
gaps=read('registers/operational-completion-gaps-v1.json')
status=read('registers/screen-status-matrix.json')
assert len(screens)==len(routes['screens'])==len(gaps['capabilities'])==len(status['items'])==261
assert len(modules)==40
canon={x['id']:x for x in screens}
history={x['screenId']:x for x in gaps['capabilities']}
statuses={x['id']:x for x in status['items']}
assert len(canon)==len(history)==len(statuses)==261
paths=set()
counts={}
historical={}
integrated={'source_api_workflow_ci_verified_unaccepted','existing_module_source_integrated_unaccepted'}
for row in routes['screens']:
    key=row['screen_id']
    assert row['module']==canon[key]['module']==history[key]['module']
    assert row['route']=='/workforce/clinical/'+key and row['route'] not in paths
    paths.add(row['route'])
    assert row['clinical_accepted'] is False and row['clinical_commands_enabled'] is False
    assert history[key]['domainWorkflowAccepted'] is False and history[key]['clinicalCertification'] is False
    assert statuses[key]['accepted'] is False and statuses[key]['clinically_reviewed'] is False
    if row['implementation_status'] in integrated:
        assert statuses[key]['implemented'] is True
    counts[row['implementation_status']]=counts.get(row['implementation_status'],0)+1
    older=history[key]['implementationState']
    historical[older]=historical.get(older,0)+1
assert historical=={'domain_locked':240,'scoped_read_only_partial':16,'functional_nonclinical_partial':5}
expected={
 'route_registered_domain_adapter_missing':156,
 'api_read_subset_not_clinically_complete':16,
 'nonclinical_functional':3,
 'source_api_workflow_ci_verified_unaccepted':16,
 'existing_module_source_integrated_unaccepted':70
}
assert counts==expected,counts
assert routes['route_summary']=={
 'registered':261,'apiReadSubsets':16,'nonclinicalFunctional':3,
 'domainLocked':156,'sourceApiCiVerified':16,'existingModuleSourceIntegrated':70,
 'clinicallyAccepted':0,'clinicallyCertifiedModules':0
}
assert gaps['date']=='2026-11-05' and gaps['missingDomainIntegration']==240
shells=routes['shell_roots']
assert len(shells)==3 and {x['shell'] for x in shells}=={'workforce','portal','platform'}
assert all(x['path'].startswith('/') and x['status']=='shell_source_only' for x in shells)
assert len({x['path'] for x in shells})==3
print('PASS: 261 unique canonical URLs; 86 source-integrated, 156 locked, historical checkpoint preserved; zero clinical acceptance')
