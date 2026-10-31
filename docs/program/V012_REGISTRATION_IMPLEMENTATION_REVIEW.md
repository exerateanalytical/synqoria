# SYNQORIA v0.12.0 — first provisional registration slice

## Source and test outcomes
Ten distinct source/unit/static verification batches: P01-B0028, P03-B0017, P05-B0001 through B0005, and P02-B0027 through B0029. This is NOT a deployable M01 workflow, nor a clinical sign-off.

Registration model permits genuinely unknown name and birth information, differentiates known date from estimated age, requires accountable duplicate-identity review, rejects auto-merge, and prevents stale/inadequately authorized commands. A Java JDBC source adapter writes patient, demographics, local audit, minimal PHI-free outbox event and idempotency claim in one transaction. Failure injection covers every insert and commit. No registered patient is reported on rollback.

The registration command, review and active-facility booleans are INTERNAL trusted seams; **they must not be bound to request JSON or mutable client flags**. There is no public create endpoint. Patient ID/command ID generation, keyed fingerprint issuer, duplicate reviewer, shift/grant and audit policy require independently verified server implementations before this operation can be exposed. Unique command claim conflicts currently fail closed as UNAVAILABLE, not a safe replayed success; retry semantics must be designed with accepted business authority and real database tests. The SQL table owners and production restricted-role RLS grants do not exist, and this SQL has not been executed against PostgreSQL.

## UI scope
The Next.js M01-S02 route is a bilingual, responsive form **source only** with a permanently disabled submission. It shows safe provisional-identity fields and a visible access/review warning. The separate standalone HTML specimen uses the same global CSS and passed static browser checks. Its layout does not demonstrate a compiled React route, working mobile build, backend connectivity, keyboard/screen-reader readiness or clinical usability. Staff and patient Expo apps are unchanged in this run and remain locked.

## Remaining safety gates
- Supported Java/Next/Expo dependencies and reproducible CI
- Real PostgreSQL migrations with restricted DB role and RLS probes (tenant/facility adversarial tests)
- Cryptographic OIDC, strong current authz claims, patient/identity-review workflow and duplicate screen M01-S05
- Server-generated opaque patient ID, HMAC fingerprint secret handling, idempotency replay/expiry and audit custody
- Safe backend POST contract, no PHI in URL/logs, cross-cell routing and native staff intake
- Accessibility review, clinical safety owner review, hospital sign-off and genuine end-to-end registration

Do not upgrade the screen register from not_implemented based on these unit/static specimen tests.
