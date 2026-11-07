# M39 — live authoritative hospital-cell routing fence and gateway admission

**Implementation status:** Java 25 gateway admission library + authenticated M39 management-plane read API. These are production-oriented source components, but not installed into a running Kong/Envoy gateway or connected to a real hospital.

## Reused authorities (never duplicate them)

- `RoutingService.lookup(actor,routeKey)` and `GET /api/v1/fleet/routing/{routeKey}` sign a **60-second** route decision using M39's existing HMAC key and return exact `routeKey`, `cellId`, `placementEpoch`, `directoryVersion`, epoch-second `issuedAt`/`expiresAt` and `signature`.
- `RoutingService.routingFence(actor,routeKey)` and the **new** `GET /api/v1/fleet/routing/{routeKey}/fence` return `routeKey`, `cellId`, `placementEpoch`, `directoryVersion`, `cellState`, `health` and ISO `observedAt`. One PostgreSQL RLS-scoped statement joins active route, target cell, directory version and fresh health observation. Absent or unhealthy routes fail closed. The endpoint requires an authenticated resource-server JWT with `m39:route_read` and authorized management-plane scope.
- `GatewayRouteAdmission` composes the existing `RoutingDecisionGate.Verifier` with an authenticated `SignedDecisions` source, authenticated current `Directory` source, and independently trusted **UUID → HTTPS destination** registry. A valid signature alone cannot select a backend. Signed cell UUID, placement epoch and directory version **must exactly match** the live authoritative route. The registry supplies the destination, not a Host header, path, unsigned API response or arbitrary URL.

## Required gateway deployment wiring

1. Independently configure control-plane URL(s), pinned expected routing DNS suffix, signing key, P03 OIDC workload identity and cell registry at gateway startup. The key MUST live in a protected management-plane secret store; ordinary browsers and hospital staff never receive it.
2. Obtain the latest signed decision via `/routing/{routeKey}` through an authenticated HTTPS management-plane connection. Convert epoch-second timestamps to `Instant` without changing the canonical signing string. Never cache beyond decision expiration.
3. Obtain the live directory snapshot through `/routing/{routeKey}/fence`, also authenticated. Feed the same provider into `GatewayRouteAdmission` and the existing monotonic epoch verifier. The adapter checks the directory before and **again after** verifying the signature; a mismatch denies routing.
4. Resolve only a trusted cell UUID to the configured internal HTTPS destination. `GatewayRouteAdmission` rejects non-HTTPS URLs, untrusted DNS suffix, credentials, query/fragment and arbitrary path. The production proxy must also enforce mTLS with a pinned cell service identity and deny untrusted redirects.
5. Forward the request **only after** gateway admission, verified P03 OIDC authentication, hospital/facility and clinical RBAC/consent checks. These are independent. M39's `route_read` permission authorizes control-plane routing metadata, not patient data.
6. Deny health or control-plane outages. Do not reuse the previous target, fail over to a default hospital, or override a stale placement epoch. Alert on unusual routing-fence mismatch.
7. Do not expose the management-plane HTTP endpoints to the public Internet; apply network policies and per-service workload credentials.

## Important residual risks and acceptance gates

- The new Java class is an integration seam, **not a deployed gateway plugin**. The actual HTTPS read clients, secret distribution, cell registry, Kong/Envoy routing filter, mTLS and live two-cell isolation tests remain to be implemented.
- Two separate authenticated lookups can race a route switch between final admission and actual forwarding. A real gateway implementation must fence the data-plane connection/stream against an authoritative placement epoch and disable stale upstream sessions on switch. HTTP response checks alone cannot prove atomic traffic switching.
- The M39 signing key currently uses one symmetric HMAC string, without versioned key IDs, KMS-backed rotation or independent gateway key distribution. Key-rotation and compromise/revocation rehearsals are outstanding.
- Health attestations originate from configured observers; clinical and facility acceptance are separate. The health gate cannot certify legal, patient safety or hospital readiness.
- Route keys remain in the existing M39 management plane, which uses restricted PostgreSQL roles, P03 JWT and RLS. No direct browser database access is permitted.

## Verification

```sh
cd services/cell-lifecycle-core
mvn -B -ntp test -Dtest=RoutingDecisionGateTest,GatewayRouteAdmissionTest
cd ../..
python tools/gen_m39_openapi.py
python tests/test_m39_openapi_contract.py
```

CI must also run the existing M39 Java 25/PostgreSQL 18.6 service verification and consolidated-source integration. Synthetic tests prove failure handling but do **not** prove a deployed gateway.
