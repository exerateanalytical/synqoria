# M39 gateway routing admission — integration contract

This change supplies a **gateway-side decision verifier**, not a deployed gateway.
Source: `services/cell-lifecycle-core/src/main/java/org/synqoria/fleet/domain/RoutingDecisionGate.java`.

## Mandatory deployment integration

1. Retrieve a route decision from M39 `GET /api/v1/fleet/routing/{routeKey}` through an authenticated management-plane identity with `m39:route_read`. Do not expose this identity or the signing key to the browser.
2. Parse `routeKey`, `cellId`, `placementEpoch`, `directoryVersion`, epoch-second `issuedAt` and `expiresAt`, and `signature` into `RoutingDecisionGate.Envelope`.
3. Configure `RoutingDecisionGate.Verifier` with the securely provisioned M39 signing key and UTC `Clock`. The key MUST NOT come from a request header, user configuration, or untrusted hostname.
4. Implement `DirectoryFence` using an independently authenticated, authoritative directory provider that returns the **current** minimum placement epoch and directory version for the route; error if unavailable. The source must be consistent enough to reject a replaced target, not a lagging cached replica.
5. Call `admit(requestedRouteKey, envelope)` **before** proxying. On rejection return HTTP 503 or 403 according to edge policy, without leaking internal destination details or attempting a fallback.
6. Resolve the resulting cell ID against a trusted internal cell destination registry. Never derive a backend URL from the route key, caller Host header, or unverified response.
7. Apply the hospital-specific OIDC authorization and patient/clinical access policy independently of this admission gate. Signed routing does **not** authorize access to records.
8. Honor network isolation: no direct browser access to cell databases, management-plane databases, or internal executor endpoints.

## Operational limits

- Local monotonic observations are process-local. They do not survive gateway restart; the authoritative directory floor is therefore mandatory on every admission.
- Current M39 signatures use a single symmetric HMAC key. KMS/key rotation and key identifiers are still outstanding. Do not use the same key in untrusted devices.
- The verifier does not provide physical provisioning, infrastructure changes, health probes, tenant granting, TLS termination, service discovery, or traffic switching. Those require separate integrations and deployment authorization.
- A real gateway is **not connected** by this PR. The Java component and unit tests are engineering source only.

## Verification / acceptance

Run `cd services/cell-lifecycle-core && mvn test -Dtest=RoutingDecisionGateTest` with repository Java 25/Maven toolchain. The new unit tests cover signature tampering, route binding, expiration, overlong validity, future issuance, stale epoch/directory versions, directory failure, and missing key.

After mounting it inside a gateway, run synthetic two-cell integration tests demonstrating:
- Route A never forwards to hospital B.
- Switch epoch invalidates previous decisions.
- Directory outage denies traffic (no fail-open).
- A restarted gateway checks authoritative directory before forwarding.
- An authenticated request still fails if the clinical role/tenant policy denies it.

Do not claim these gateway integration tests, real provisioning or hospital acceptance as completed until actually executed.
