# v0.27.0 design, implementation, test and risk review

## Engineering changes

- P01-B0039: Exact external package pins replace `~`/`^` ranges. Staff and patient Expo manifests now agree. Versions have **not** been validated against an installed Expo compatibility matrix or lockfile; no claim of buildability is made.
- P01-B0040: Lock integrity verifier requires npm v3 lock, matching workspace manifests, SHA-512 integrity, and registry-host-only resolved URLs; it fails closed without a lock. Fixture tests cover legitimate and malicious URLs. It is not an npm lockfile generator.
- P01-B0041: Build attempt runner records actual subprocess stdout/stderr/exit codes only when lock, pins and installed packages pass. Current run correctly refuses all three app builds; 0/3 builds executed. CI delegates to this runner and preserves evidence.
- P01-B0042: ZIP verifier detects unsafe names, duplicates, symlinks, unexpected members and digest changes; the inherited v0.26.0 cumulative ZIP passed.
- P02-B0089/B0090: Mobile web More is an accessible disclosure (not a clinical menu). Opening focuses its first language control; Escape returns focus, outside pointer closes. Panel has fixed safe-area position, bounded scrolling and accessible button sizes. No patient data is rendered or persisted.
- P02-B0091: Non-sensitive EN/FR language is carried via URL query and applied to the document language; no local/session storage of patient data. Three distinct web shell routes are retained.
- P02-B0092: Chromium ran a **static HTML/CSS specimen** with 320, 360, 390, 412, 768, 1024, 1280, 1440 and 1920 widths, French labels, 200% font simulation, locked action and four mobile destinations. Screenshots are engineering specimens, not application acceptance.
- P05-B0062: Scoped, bounded JDBC reconciliation cross-checks reviewer event, audit receipt and outbox record under tenant/facility/review/event IDs and digest, with serializable transaction precondition. Mock JDBC tests cover missing, duplicate, mismatched, wrong isolation and SQL failure.
- P05-B0063: Scoped reviewer-command replay preflight checks prior event existence. **Advisory only**: the unapplied database uniqueness constraint and transaction isolation are mandatory to close races. Mock JDBC tests cover replay, new command, isolation and SQL failure.

## Safety and evidence limits

No clinical write or reviewer approval endpoint was enabled. Quarantined PostgreSQL migrations remain unapplied. The JDBC tests do not prove RLS, privileges, serializability or durable outbox correctness. Native app sources were not compiled. A static Chromium specimen is not a Next.js build. A unit green check does not grant clinical safety or regulatory acceptance. Independent security, clinical, legal, DBA and pilot gates remain open.

## Independent verification

See `evidence/batches/v270-regression-summary.json` and per-batch logs. Release request returned exit 3 DENIED; missing lock, PostgreSQL and app build prerequisites returned exit 4 BLOCKED. No result was reclassified as a successful production test.
