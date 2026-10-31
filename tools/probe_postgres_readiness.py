#!/usr/bin/env python3
"""Run a read-only PostgreSQL restricted-role readiness probe; never prints DSN."""
import os, shutil, subprocess, json, sys
SQL = """SELECT current_setting('server_version_num'), current_user,
 current_setting('row_security'), r.rolsuper, r.rolbypassrls
 FROM pg_roles r WHERE r.rolname=current_user;"""
def assess(raw, expected):
    fields=raw.strip().split('|')
    if len(fields)!=5:return False,'invalid_probe_result'
    version,user,rls,superuser,bypass=fields
    if not version.isdigit() or int(version)<160000:return False,'unsupported_postgresql_version'
    if not expected or user!=expected:return False,'unexpected_database_role'
    if rls!='on' or superuser!='f' or bypass!='f':return False,'privileged_or_rls_disabled'
    return True,'restricted_role_metadata_passed_not_table_rls'
def probe(dsn,expected,runner=subprocess.run,which=shutil.which):
    if not dsn or not expected:return {'ready':False,'reason':'missing_connection_or_pinned_role'}
    binary=which('psql')
    if not binary:return {'ready':False,'reason':'psql_not_installed'}
    try:
        cp=runner([binary,'-X','--no-psqlrc','-A','-t','-F','|','-v','ON_ERROR_STOP=1','--dbname',dsn,'--command',SQL],
                  capture_output=True,text=True,timeout=10,env={**os.environ,'PGCONNECT_TIMEOUT':'5'})
    except (subprocess.TimeoutExpired,OSError):return {'ready':False,'reason':'connection_or_process_failed'}
    if cp.returncode!=0:return {'ready':False,'reason':'postgresql_query_failed'}
    ready,reason=assess(cp.stdout,expected)
    return {'ready':ready,'reason':reason}
if __name__=='__main__':
    result=probe(os.getenv('SYNQORIA_PG_DSN'),os.getenv('SYNQORIA_PG_ROLE'))
    print(json.dumps(result));sys.exit(0 if result['ready'] else 4)
