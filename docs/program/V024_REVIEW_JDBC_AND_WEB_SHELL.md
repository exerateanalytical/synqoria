# v0.24.0 implementation and safety review

## Source and ownership

This cumulative checkpoint inherits every verified file from v0.23.0. No canonical module ID, screen ID or ownership has been altered. `P05` remains the patient identity/consent domain and `P02` the cross-platform navigation/design domain. The two authoritative October 2026 documents remain unchanged.

## P05 — five implemented and individually unit-verified source batches

- **P05-B0048**: `ReviewEventJdbcReadV2` reads at most two scoped reviewer events using a parameterized tenant/facility/review query, serializable transaction predicate and V2 digest-chain verification. A JDBC dynamic proxy verifies bound parameters, resource closure, corrupt data rejection and scope mismatch denial. No live DB role, migration or RLS is proven.
- **P05-B0049**: `ReviewEventSqlPlanV2` builds a 14-column immutable parameter plan including scope, actor, candidate revision, previous digest and V2 digest. It **does not execute SQL** or authorize a review decision.
- **P05-B0050**: `ReviewReplayGuardV2` distinguishes a conflicting replay, a possible exact replay requiring durable DB proof, an untrusted new candidate and denial. It never grants registration approval.
- **P05-B0051**: `ReviewOutboxEvidenceV2` produces a scoped event envelope only from verified history, with no patient demographics or clinical payload. It is not an actual transactional outbox or a Kafka integration.
- **P05-B0052**: `ReviewDisclosureReceiptV2` derives a bounded status receipt only after V2 chain verification, excludes patient/candidate identifiers and never authorizes registration writes.

The five P05 tests contain **35 actual assertions**, all passed. Package-private SQL and JDBC implementations remain unconnected to the production HTTP gateway. The reader and SQL plan require DBA review before any runtime use. The unapplied V008/V009 SQL drafts remain quarantined.

## P02 — five implemented and individually Chromium-verified browser batches

- **P02-B0078**: three distinct workforce/patient/platform shells with correct current-shell labels; no ungranted clinical sidebar links.
- **P02-B0079**: working EN/FR language switcher, `lang` and `aria-pressed` updates, long French shell labels.
- **P02-B0080**: four labelled mobile bottom destinations, including More; locked tasks and correct desktop sidebar breakpoint.
- **P02-B0081**: keyboard-first skip link, explicitly disabled clinical action with explanatory hint, Warm Clinical canvas.
- **P02-B0082**: actual Chromium viewport checks at 320/360/390/412/768/1024/1280/1440/1920, plus 320px 200% font and long French label overflow checks. Screenshots: `evidence/batches/v240-locked-shell-{320,390,1440}.png`.

The five P02 browser tests contain **44 actual assertions**, all passed. `ShellFrame.tsx` and CSS have corresponding source changes, but tests exercise an interactive **static specimen**, not a compiled Next.js app. No Android/iOS app was built or device-tested.

## Release and threat review

No backend authorization decision can be inferred from navigation visibility, JWT primitive validation or a Java domain preflight. Untrusted clients cannot use the new source-only V2 SQL plan to commit a reviewer event. Real restricted-role PostgreSQL RLS, serializable commit, row locks, idempotency, audit/outbox custody, IAM and qualified clinical approval are still mandatory. No medication, allergy, dose or other safety-critical workflow was enabled. The release-denial script exited 3 as expected. The 261-screen clinical implementation and acceptance matrices remain **0/261**.

## Verification boundaries

Tests are reproducible using `tests/test_v240_checkpoint.py`, `tests/test_v240_browser.py`, `V240ReviewIntegrationTest.java`, `npm run check`, `npm run check:source`, `tools/check_java_security.sh`, and `tools/verify_release_gate.py`. Individual test logs include stdout and exit codes. Build dependencies were unavailable (`EAI_AGAIN`); no production build, DB integration, external review or GitHub push is claimed.
