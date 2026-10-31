"""Static negative checks on JDBC's fail-closed event/receipt/outbox proof path.
Not proof of PostgreSQL behavior or a substitute for database integration tests.
"""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'services/clinical-core/src/main/java/org/synqoria/triage/TriageJdbcPersistence.java'

def check(source):
    required=('verifyLedger(db,sc,c)',
      'LEFT JOIN synqoria_triage_secure_receipts_quarantine r',
      'LEFT JOIN synqoria_triage_secure_outbox_quarantine o',
      'AND r.patient_id=e.patient_id',
      'AND r.case_id=e.case_id',
      'AND r.command_id=e.command_id',
      'AND r.revision=e.revision',
      'AND o.patient_id=e.patient_id',
      'AND o.case_id=e.case_id',
      'AND o.command_id=e.command_id',
      'WHERE e.tenant_id=? AND e.facility_id=? AND e.patient_id=? AND e.case_id=?',
      'TriageAuditEvidence.requireFullChain(c,events)',
      'ps.setQueryTimeout(10);scope(ps,sc,1)',
      'if(events.size()>=30000)',
      'if(timestamp==null)')
    return [item for item in required if item not in source]

class JdbcEvidenceSourceTests(unittest.TestCase):
    def test_integrity_guard_present(self):self.assertEqual(check(SOURCE.read_text()),[])
    def test_no_verified_ledger_call_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('verifyLedger(db,sc,c)','/* suppressed */')))
    def test_unscoped_event_query_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('WHERE e.tenant_id=? AND e.facility_id=? AND e.patient_id=? AND e.case_id=?','WHERE e.case_id=?')))
    def test_unscoped_receipt_join_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('AND r.patient_id=e.patient_id','')))
    def test_unscoped_outbox_join_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('AND o.patient_id=e.patient_id','')))
    def test_disconnected_receipt_revision_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('AND r.revision=e.revision','')))
    def test_no_full_chain_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('TriageAuditEvidence.requireFullChain(c,events)','')))
    def test_timeout_missing_denied(self):
        self.assertTrue(check(SOURCE.read_text().replace('ps.setQueryTimeout(10);scope(ps,sc,1)','scope(ps,sc,1)')))

if __name__=='__main__':unittest.main()
