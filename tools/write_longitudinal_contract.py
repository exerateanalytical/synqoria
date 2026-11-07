#!/usr/bin/env python3
"""Generate the locked, reviewed M04–M06 HTTP surface from explicit paths, never from clinical input."""
import json
from pathlib import Path
root=Path(__file__).resolve().parent.parent
path=root/'contracts/openapi/synqoria-m040506-restricted-v1.json'
root_route='/api/v1/clinical/patients/{patientId}/encounters/{encounterId}'
params=[{'name':name,'in':'path','required':True,'schema':{'type':'string','format':'uuid'}} for name in ('patientId','encounterId')]
security=[{'bearerAuth':[]}]
errors={'400':{'description':'Invalid request envelope'},'401':{'description':'Authentication required'},'404':{'description':'Unavailable or not disclosed'},'409':{'description':'Stale revision or invalid clinical transition'},'422':{'description':'Malformed or unsupported command'},'503':{'description':'Service unavailable'}}
ids={'type':'string','format':'uuid'}
command={
 'type':'object','additionalProperties':False,
 'required':['commandId','expectedRevision','operation','inputs'],
 'properties':{'commandId':ids,'expectedRevision':{'type':'integer','minimum':0},
   'operation':{'type':'string','pattern':'^(encounter|note|observation)\\.[a-z_]{2,60}$'},
   'inputs':{'type':'object','additionalProperties':{'type':'string','maxLength':8192},'maxProperties':32}}
}
views=[('summary','get','Authorized PHI-minimized encounter summary'),('audit','get','Bounded audit page without clinical note text'),('notes/{noteId}','get','Authorized clinical note content'),('observations','get','Authorized bounded observation timeline')]
paths={}
paths[root_route+'/commands']={'post':{'summary':'Authenticated clinical command; no client actor/tenant/facility grants','security':security,
  'parameters':params,'requestBody':{'required':True,'content':{'application/json':{'schema':command}}},
  'responses':{'200':{'description':'Idempotent command revision and audit digest'},**errors}}}
for suffix,method,summary in views:
 p=[*params]
 if suffix.startswith('notes/'):
  p.append({'name':'noteId','in':'path','required':True,'schema':ids})
 if suffix=='audit':
  p.extend([{'name':'afterRevision','in':'query','schema':{'type':'integer','minimum':0,'maximum':2147483647}},
   {'name':'limit','in':'query','schema':{'type':'integer','minimum':1,'maximum':100}}])
 if suffix=='observations':
  p.extend([{'name':'code','in':'query','required':True,'schema':{'type':'string','maxLength':128}},
   {'name':'limit','in':'query','schema':{'type':'integer','minimum':1,'maximum':100}}])
 paths[root_route+'/'+suffix]={method:{'summary':summary,'security':security,'parameters':p,
   'responses':{'200':{'description':summary},**errors}}}
contract={'openapi':'3.0.3','info':{'title':'SYNQORIA M04–M06 restricted clinical HTTP','version':'0.61.0',
 'description':'Implementation contract only; no live OIDC/PostgreSQL qualification or clinical certification'},
 'servers':[{'url':'https://hospital.example.invalid'}],
 'paths':paths,'components':{'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'opaque'}}}}
path.write_text(json.dumps(contract,indent=2)+'\n')
print(path.relative_to(root))
