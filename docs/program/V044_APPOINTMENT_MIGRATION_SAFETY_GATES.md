# v0.44.0 — Quarantined appointment migration source safety gates

Five independently negative-tested static SQL checks. These do not run SQL, prove RLS enforcement, or authorize deployment.

- P06-B0065: Quarantine and privilege escalation drift guard
- P06-B0066: FORCE RLS and PUBLIC revocation source guard
- P06-B0067: Tenant/facility/patient scope schema drift guard
- P06-B0068: Resource overlap exclusion integrity guard
- P06-B0069: Durable idempotency key/replay proof schema guard

Run `python -m unittest discover -s tests -p appointment_migration_gate_test.py -v` and `python tools/appointment_migration_gate.py services/clinical-core/db/migration`.

Next: P06-B0070; live restricted-role PostgreSQL proof remains blocked.
