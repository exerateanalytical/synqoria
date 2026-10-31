# SYNQORIA v0.13.0 — Registration authority and safe intake engineering review

## Verified source work (ten consecutive independently exercised increments)

| Batch | Concrete implementation | Evidence scope |
| --- | --- | --- |
| P05-B0006 | Framed, tenant/facility-bound HMAC-SHA256 fingerprint; rejects constant or short keys | Java assertions; no key-management service |
| P05-B0007 | Server-side SecureRandom v4 UUID issuance for patient/command/correlation and HMAC fingerprint | Java assertions; no live HTTP adapter |
| P05-B0008 | Internal review permit checks separate reviewer, facility, candidate revision, expiry, fingerprint and registrar | Java assertions; **no authoritative issuer or durable signed proof** |
| P05-B0009 | Scoped idempotency replay decision distinguishes safe match, conflict and uncertain outcome | Java assertions; **no durable claim lookup or concurrency test** |
| P05-B0010 | Parameterized bounded reviewer-only candidate lookup and draft RLS-denied HMAC lookup table | Java + static SQL; **no PostgreSQL execution or matching algorithm** |
| P04-B0012 | Parameterized `set_config(..., true)` transaction-local tenant/facility/actor context | JDBC proxy tests; **no real RLS verification** |
| P02-B0030 | Volatile intake draft clears on actor/facility/session epoch mismatch; ignores client duplicate-review flag | TypeScript/Node tests; not connected to compiled app |
| P02-B0031 | EN/FR privacy-safe error copy, deduplicated issue list and accessible live summary in disabled Next source | TypeScript/Node + static source tests |
| P02-B0032 | Four-destination native staff intake model and separate read-only RN registration preview | TypeScript/Node + TSX syntax/source tests; Expo not compiled |
| P01-B0029 | OpenAPI 3.1 **component-only** provisional intake schema with no exposed path or caller-controlled authority | YAML parse/static tests; no API endpoint |

## Verified execution and scope

- `evidence/batches/P01-P05-v130-ten-batches.json`: ten batch-specific commands, actual stdout, exit codes and paths. All ten returned exit 0. No batch is clinically certified.
- `evidence/batches/P01-P05-v130-checks.json`: inherited and new regression runner **31/31 check groups exit 0**, before final key-strength refinement. `evidence/batches/v130-java-postfix.log` separately confirms the complete Java regression after the refinement (exit 0).
- `npm run check:ux`: **66/66 TypeScript/Node tests passed**. Six new Java focused modes total **39 assertions**; these use unit logic and JDBC proxies.
- `evidence/batches/v130-registration-browser.log`: independent prior HTML registration specimen **19/19 EN/FR viewport checks**; this is NOT the compiled Next registration route. Chromium legacy specimen checks also passed inside the regression runner.
- `registers/canonical-modules.json`: **40 modules**; `registers/canonical-screens.json`: **261 stable screens**. All screen implementation flags remain **not_implemented**, and all acceptance/clinical review flags remain false.

## Security and safety blockers — not bypassed

1. The review permit is an **internal predicate** and could be constructed by untrusted server code. It is NOT an independently authenticated reviewer decision. Do not use it to authorize an external POST until verified server issuer, durable reviewer event, grant revocation and candidate revision checks exist.
2. The replay `Claim.committed` value must originate from an authoritative committed database row. A boolean or client-supplied claim cannot prove successful registration. No replay success is exposed.
3. `patient_identity_lookup` has RLS enabled and forced but **no allow policy or role grants**. It is intentionally quarantined and cannot yet support duplicate matching. No patient search or matching service was tested.
4. `ScopedDbTransaction` must be used only with restricted non-owner roles. No PostgreSQL server, driver, RLS probe or DB migration execution was available. Transaction-local context by itself is not authorization.
5. The new HMAC issuer has no production secret provisioning, rotation, custody, rate limiting or replay-key lifecycle. Deterministic test keys are **synthetic only**.
6. Existing `RegistrationJdbc.create` accepts internal trusted booleans, and the source-only web form still has `disabled` save. **Never expose this method directly through a client endpoint**. A secure command orchestrator, identity reviewer, authoritative grants and audited DB roles are prerequisites.
7. Next.js and Expo were not compiled or deployed. The native preview is not mounted into an authenticated workflow. Browser tests cover static specimens, not a working hospital UI. Clinical owner approval and privacy review remain mandatory.

## Next batch

**P05-B0011** — implement and test a durable, independently verified duplicate-review authority and PostgreSQL restricted-role RLS integration when infrastructure is available. If not, continue an independently verifiable P02-B0033 accessible staff registration navigation/keyboard/semantic source improvement while keeping patient writes locked.
