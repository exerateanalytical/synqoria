# ADR — limited runtime integration without dependency installation

Decision: make `ClinicalHttpGateway` a test-only integration seam using the Java standard HTTP server. Do not add a public server bootstrapping command. Dependencies `BearerAdmission`, `CellFacilityGate` and `AuditedPatientAccess` must be injected from a real signed identity verifier, authoritative hospital-cell store, policy proof resolver and durable audit writer. The source includes **no production implementations** of these ports. Operational readiness is always reported unavailable.

Why: network access blocks Next.js/Expo installation; Java localhost request testing is still possible and is meaningfully stronger than isolated predicates. The TypeScript clients are independently tested and not yet bound into a compiled app.

Constraints: deny unknown paths/methods, reject forged tenant headers as authority, reject unknown bearers, suppress patient existence (404), no caches, sanitize errors; no clinical write route. Production authorization, OIDC signing keys, TLS edge, access reviews and PostgreSQL are blockers.

Known limitations: no on-disk audit, no actual clinical record persistence, no request rate limits, no revocation feed, no signed production configuration, no operational identity failover, no app build/device checks. Java `HttpServer` itself is not approved for deployment.
