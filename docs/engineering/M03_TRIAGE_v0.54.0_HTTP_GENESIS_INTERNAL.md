# M03 triage v0.54.0 — internal engineering review, NOT deployable

## New code boundaries

- `TriageCommandCoordinator.arrive` writes a genesis event and outbox alongside the encrypted aggregate and receipt inside one transaction. Synthetic injected failures confirm atomic rollback.
- `V018__triage_secure_genesis_quarantine.sql` permits event revision 1, links receipt to scoped event and protects audit events with an append-only trigger. Static adversarial tests only; no PostgreSQL execution or application grants.
- `TriageCommandHttpHandler` is an actual Java HttpServer `HttpHandler` for existing-case mutations, but it has NO production wiring. It requires injected server-side verified bearer admission, clinical access evidence, transactional gateway and server clock.
- `TriageStrictJson` accepts only bounded flat UTF-8 JSON with exact allowlisted fields, rejects duplicate/unknown fields, nested values, malformed UTF-8 and Unicode surrogate errors. Observation payloads are NOT supported.
- Client cannot supply tenant/facility/actor/access proof. Scope comes from verified session and canonical patient/case path selectors.
- Domain revision conflicts map to 412, business conflicts to 409, denied resources to 404 and infrastructure failures to 503, with no raw exception or PHI in responses.

## Tested

60 distinct acceptance scenarios were executed separately: 12 genesis/rollback, 28 HTTP admission/JSON, 10 static V018 adversarial/lock, and 10 further HTTP integration/failure cases. The 16 cumulative regression groups also passed. See `evidence/v540/execution.json`.

## Open architectural risks

1. V016/V017/V018 schema relationship requires independent migration review and live restricted-role PostgreSQL tests.
2. The persisted genesis event digest is not yet the previous digest of the first revision-2 aggregate mutation. This is an **audit continuity gap**, not a completed chain. Next batch P06-B0289.
3. HTTP tests use synthetic identity/evidence and atomic storage. There is no deployed endpoint, real OIDC provider, live consent source, production KMS, load test or clinical acceptance.
4. Clinical priority is always clinician entered; do not infer automatic risk classification from these tests.

**Release denied:** 0/261 clinical screens accepted; 0/40 modules clinically certified.
