# v0.27.1 Recovery scheduling branch — provenance and safety review

## Why this is not called v0.30.0

The hourly task created a reported **v0.28.0** with 10 tested batches (P01-B0043–0044, P02-B0093–0094, P06-B0001–0006), but its cumulative ZIP upload was rejected with `library_storage_limit_exceeded`. Later **v0.29.0** reported three further local scheduling tests (P06-B0007–0009), but neither source archive is present in the persistent Library. The latest **independently recovered source** is the complete v0.27.0 archive, whose SHA-256 matches its published checksum. This branch does **not** claim to contain v0.28/v0.29 code; do not merge based on reports alone.

## The ten new batches

P06-B0010 through B0019 add resource- and facility-scoped guard rails, DST-safe appointment periods, overlap and capacity preflight, credential/shift checks, conservative lifecycle transitions, replay and hold fences, parameterized read-only JDBC lookup, audit/outbox parity verification. The scheduling table `V012__appointment_read_quarantine.sql` is **quarantined draft DDL**: RLS is forced with no permissive policy, and no production DB execution has occurred.

## Evidence and non-claims

All 10 batches compile under Java 21 and run independently with assertions. Java JDBC uses a mock; capacity/overlap guards are advisory and not concurrency-safe absent database exclusion constraints and a trusted transactional writer. No appointment POST, no booking confirmation, and no clinical action was enabled. Canonical 40-module, 261-screen registers stay unchanged in implementation/acceptance status. Existing Next/Expo, OIDC, live PostgreSQL, mobile device and clinical approvals remain blocked. Any v0.28/v0.29 merger must compare original bytes and rerun tests. The Library quota may prevent persisting this archive; report upload result, not intent.
