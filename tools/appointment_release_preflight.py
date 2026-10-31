"""Fail-closed source preflight for appointment persistence migrations.
Not a substitute for executing migrations and RLS policies against PostgreSQL.
"""
import argparse,json,sys
from pathlib import Path
from migration_inventory_gate import audit
from migration_checksum_gate import verify
from appointment_migration_gate import audit as appointment_audit

def preflight(migrations, lock):
    inventory=audit(migrations)
    integrity=verify(migrations,lock)
    # The appointment security gate must pass as a separate independent audit.
    appointment_audit(migrations)
    if integrity['locked']!=inventory['count']:
        raise ValueError('inventory and immutable lock counts disagree')
    return {'status':'PASS','migration_count':inventory['count'],'latest_version':inventory['latest'],
            'immutable_lock_count':integrity['locked'],'appointment_security_audit':'PASS',
            'database_runtime_verified':False,'clinical_release_approved':False}

def main(argv=None):
    parser=argparse.ArgumentParser()
    parser.add_argument('--migrations',required=True)
    parser.add_argument('--lock',required=True)
    args=parser.parse_args(argv)
    try:
        result=preflight(Path(args.migrations),Path(args.lock))
    except (OSError,ValueError,KeyError,TypeError) as exc:
        print(json.dumps({'status':'FAIL','reason':str(exc),'database_runtime_verified':False}),file=sys.stderr)
        return 1
    print(json.dumps(result,sort_keys=True))
    return 0
if __name__=='__main__': sys.exit(main())
