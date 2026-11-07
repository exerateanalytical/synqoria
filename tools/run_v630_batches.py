#!/usr/bin/env python3
"""Individually execute 11 meaningful outbox engineering feature checks; not clinical certification."""
import json,subprocess,hashlib,datetime,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];O=R/'evidence/v630';O.mkdir(parents=True,exist_ok=True)
C='org.synqoria.longitudinal.'
J=lambda cls,case:['java','-ea','-cp',str(R/'.v630-classes'),C+cls,case]
SUITES={
  94:('M04/M05/M06','Privacy-minimal validated outbox event reference',J('LongitudinalOutboxDeliveryTest','metadata')),
  95:('M04/M05/M06','Worker permit scope, expiry and fail-closed authorization gate',J('LongitudinalOutboxDeliveryTest','permit-scope')),
  96:('M04/M05/M06','Per-encounter revision-ordered publication across multiple events',J('LongitudinalOutboxDeliveryTest','ordered')),
  97:('M04/M05/M06','Concurrent worker single-lease ownership without later-event overtaking',J('LongitudinalOutboxDeliveryTest','concurrent')),
  98:('M04/M05/M06','Token-fenced acknowledgement rejecting expired or mismatched evidence',J('LongitudinalOutboxDeliveryTest','ack-fence')),
  99:('M04/M05/M06','Broker failure release with bounded retry and delayed redelivery',J('LongitudinalOutboxDeliveryTest','broker-failure')),
 100:('M04/M05/M06','Uncertain acknowledgement at-least-once redelivery and consumer dedupe contract',J('LongitudinalOutboxDeliveryTest','ack-uncertain')),
 101:('M04/M05/M06','Terminal parking after bounded attempts, poisoning fence preserving event order',J('LongitudinalOutboxDeliveryTest','park')),
 102:('M04/M05/M06','Scoped PostgreSQL JDBC lease and bounded progressive delivery projection',J('LongitudinalOutboxJdbcContractTest','claim')),
 103:('M04/M05/M06','JDBC CAS acknowledgement, failed-delivery deferral, scope/RLS echo contract',J('LongitudinalOutboxJdbcContractTest','defer')),
 104:('M04/M05/M06','V021 forced-RLS no-grant delivery schema with immutable migration checksum',['python','-m','unittest','tests.test_v630_outbox','-v']),
}
rows=[]
for n,(module,description,cmd) in SUITES.items():
 try:
  p=subprocess.run(cmd,cwd=R,capture_output=True,text=True,timeout=50)
  rc=p.returncode;output=p.stdout+'\n'+p.stderr
 except subprocess.TimeoutExpired as e:
  rc=124;output='TIMEOUT '+str(e)
 path=O/f'P07-B{n:04d}.log';path.write_text('command='+json.dumps(cmd)+'\nexit_code='+str(rc)+'\n'+output)
 good=rc==0 and ('PASS' in output or 'OK' in output)
 rows.append(dict(id=f'P07-B{n:04d}',module=module,implemented=description,cmd=cmd,exit_code=rc,
  passed=good,log=str(path.relative_to(R)),sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
 print(rows[-1]['id'],'PASS' if good else 'FAIL',flush=True)
summary={'version':'0.63.0','phase':'P07','previous_latest':'P07-B0093','new_batches':len(rows),
 'passed':sum(x['passed'] for x in rows),'requested_remaining_from_previous_100':77,
 'remaining_after_this_execution':77-sum(x['passed'] for x in rows),
 'run_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'real_postgresql_ran':False,'broker_integration_ran':False,'clinical_certification':'DEFERRED_NOT_PASSED',
 'batches':rows}
(O/'individual-batches.json').write_text(json.dumps(summary,indent=2)+'\n')
if not all(x['passed'] for x in rows):sys.exit(1)
