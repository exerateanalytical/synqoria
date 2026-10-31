"""Fail-closed source audit for quarantined appointment migrations. NOT a DB/RLS proof."""
from pathlib import Path
import re

TABLES = {
    "V012__appointment_reservation_quarantine.sql": "synqoria_appointment_reservations_quarantine",
    "V013__appointment_restricted_audit_quarantine.sql": None,
    "V014__appointment_idempotency_quarantine.sql": "synqoria_appointment_idempotency_quarantine",
}

def strip_comments(sql):
    return "\n".join(line.split("--",1)[0] for line in sql.splitlines())

def table_names(sql):
    return re.findall(r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z_0-9]*)", strip_comments(sql), re.I)

def check_quarantine(sql):
    clean=strip_comments(sql)
    names=table_names(sql)
    assert names, "migration must create a table"
    for table in names:
        assert table.endswith("_quarantine"), "non-quarantine table: "+table
    assert not re.search(r"\b(?:GRANT|CREATE\s+POLICY|ALTER\s+ROLE|SET\s+ROLE)\b",clean,re.I), "unexpected role/policy grant"
    assert not re.search(r"\bBYPASSRLS\b",clean,re.I), "RLS bypass"
    return names

def check_rls(sql):
    clean=strip_comments(sql)
    for table in check_quarantine(sql):
        for clause in ("ENABLE ROW LEVEL SECURITY", "FORCE ROW LEVEL SECURITY"):
            assert re.search(r"\bALTER\s+TABLE\s+"+re.escape(table)+r"\s+"+clause+r"\b",clean,re.I), "missing "+clause+" on "+table
        assert re.search(r"\bREVOKE\s+ALL\s+ON\s+"+re.escape(table)+r"\s+FROM\s+PUBLIC\b",clean,re.I), "missing PUBLIC revoke: "+table
    return True

def check_scope(sql):
    clean=strip_comments(sql)
    for table in check_quarantine(sql):
        definition=re.search(r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?"+re.escape(table)+r"\s*\((.*?)\)\s*;",clean,re.I|re.S)
        assert definition, "missing table definition: "+table
        for column in (("tenant_id", "facility_id", "patient_id") if "reservations" in table or "idempotency" in table else ("tenant_id", "facility_id", "appointment_id")):
            assert re.search(r"\b"+column+r"\s+uuid\s+NOT\s+NULL\b",definition.group(1),re.I), "missing scoped column: "+table+"."+column
    return True

def check_exclusion(sql):
    clean=strip_comments(sql)
    assert re.search(r"EXCLUDE\s+USING\s+gist",clean,re.I), "missing exclusion constraint"
    for clause in (r"tenant_id\s+WITH\s*=",r"facility_id\s+WITH\s*=",r"resource_id\s+WITH\s*=",r"tstzrange\s*\(\s*starts_at\s*,\s*ends_at\s*,\s*'\[\)'\s*\)\s+WITH\s*&&",r"WHERE\s*\(\s*lifecycle\s*=\s*'CONFIRMED'\s*\)"):
        assert re.search(clause,clean,re.I), "missing overlap constraint component: "+clause
    return True

def check_idempotency(sql):
    clean=strip_comments(sql)
    assert re.search(r"UNIQUE\s*\(\s*tenant_id\s*,\s*facility_id\s*,\s*actor_id\s*,\s*key_digest\s*\)",clean,re.I), "scope-bound unique key missing"
    for col in ("key_digest","command_digest"):
        assert re.search(r"\b"+col+r"\s+text\s+NOT\s+NULL\s+CHECK\s*\(\s*"+col+r"\s*~\s*'\^\[0-9a-f\]\{64\}\$'",clean,re.I), "invalid digest constraint: "+col
    assert "result_revision IS NOT NULL" in clean and "event_id IS NOT NULL" in clean, "committed replay proof missing"
    return True

def audit(directory):
    reports={}
    for filename in TABLES:
        sql=(Path(directory)/filename).read_text()
        reports[filename]={"quarantine":check_quarantine(sql),"rls":check_rls(sql),"scope":check_scope(sql)}
    reports["exclusion"]=check_exclusion((Path(directory)/"V012__appointment_reservation_quarantine.sql").read_text())
    reports["idempotency"]=check_idempotency((Path(directory)/"V014__appointment_idempotency_quarantine.sql").read_text())
    return reports

if __name__ == "__main__":
    import json,sys
    print(json.dumps(audit(sys.argv[1]),indent=2))
