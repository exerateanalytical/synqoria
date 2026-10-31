# SYNQORIA v0.9.0 — Ten-batch integration hardening

Parent: v0.8.0. Exactly ten individually scoped, regression-tested foundation increments across P01/P02/P03/P04. This release is **NOT approved for clinical use**.

## Security and backend
- Real localhost Java clinical gateway now rejects ambiguous body/framing, foreign Origin, cookie-auth attempts, non-JSON Accept and mutating verbs. Each JSON result carries a server-generated opaque request ID. No CORS authorization.
- Clinical read access remains controlled by an externally provided trust verifier. The new revocation/epoch fence is a source-level hook; **no OIDC verification or live policy store exists**. The four-argument compatibility admission constructor must never be relied on in production without an enforced authoritative fence.
- A bounded local `FileChannel.force(true)` audit chain is an **engineering prototype**, not a durable, immutable cross-node regulatory audit. It fails closed if corruption is detected. It needs key custody, external anchoring, rotating segmented custody and outage tests.
- Two read-only JDBC adapters query `patient_master_index` and `hospital_facilities` with prepared tenant/facility/cell parameters aligned to migrations V001/V003. Resource closure, one-row constraints and mismatches are covered with controlled JDBC mock/proxy tests, **not PostgreSQL/RLS execution**. No production datasource or HTTP integration uses these ports.

## Web and native boundary
- Streaming TypeScript clinical reader enforces 4KiB actual response limit, no-store and strict result fields. HTTP mocks, not browser/native integration.
- Explicit sensitive-cache/draft-index/telemetry teardown denies context switch on incomplete cleanup. Actual query cache and encrypted draft provider still require wiring.
- Mobile task gate rejects stale session, missing capabilities and offline escalation. Staff and patient Expo shells still have disabled clinical tabs until a verified backend lease is wired; native builds have not been run.
- Sidebar filter uses exact approved navigation IDs and paths, excluding detail flows and denied/unlicensed workspaces. All 261 canonical clinical screens remain unimplemented.

## Verification and remaining safety gates
See `evidence/batches/P01-P04-v090-checks.json` for complete commands, stdout, stderr and exit status. npm registry returned `EAI_AGAIN` during attempted npm ping; no Next.js, Expo or Android/iOS builds. PostgreSQL, real OIDC, distributed audit, hospital pilot, screen-reader and real clinical approvals are NOT verified.

**Next:** install dependencies in network-enabled CI, implement real PostgreSQL testcontainers/RLS and OIDC signed-JWKS verification, integrate secure app sessions with web/mobile shells, then implement actual prioritized clinical screen workflows.
