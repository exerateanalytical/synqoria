# SYNQORIA v0.17.0 — Security and mobile privacy implementation review

## Scope and provenance

Built cumulatively from verified v0.16.0 (SHA-256 `176bc554378049a8c059142bc810f6e1172d35f257601581225ef3250c9a6aba`; embedded manifest 410/410 matched, 411 ZIP entries). Baseline seed 19/19 matched. The authoritative 40 modules and 261 canonical screens are unchanged. This run does not claim to complete any canonical clinical screen.

## Six independently tested P03 increments

1. **P03-B0018** `OidcRs256Primitive`: actual JDK `SHA256withRSA` verification with 2048–4096-bit RSA and exponent 65537, exact signature length; tampered input/signature, weak RSA and wrong key type refused. **8 Java assertions.** This is not an OIDC verifier.
2. **P03-B0019** `OidcCompactEnvelope`: canonical base64url framing, three segments, bounded header/payload/signature, defensive byte-array copies, rejection of malformed or noncanonical tokens. **10 Java assertions.** JSON and JOSE algorithm policy deliberately remain unimplemented; all bytes remain untrusted.
3. **P03-B0020** `OidcTrustPins`: exact HTTPS issuer, audience and authorized-party comparison; malformed issuer and mixed trust domains refused. **7 Java assertions.** Requires an independently signature-verified, safely parsed claim set.
4. **P03-B0021** `OidcTemporalFence`: issuance, not-before, expiry, bounded one-hour token life and recent authentication constraints. **7 Java assertions.** No trusted clock or real IdP integrated.
5. **P03-B0022** `OidcKeyRotationFence`: pinned key metadata with time windows, duplicate key-ID refusal, revoked and expired key denial. **6 Java assertions.** No JWKS retrieval, certificate chain, HSM or actual key custody.
6. **P03-B0023** `FacilitySessionGrantFence`: exact tenant/facility/actor/session/capability, policy revision, shift and 15-second grant freshness. **8 Java assertions.** A real authoritative grant source remains unavailable.

**Critical composition warning:** These six primitives MUST NOT be assembled into an ad hoc JWT validator or connected to patient data. There is no vetted JSON/JOSE parser, key provenance, live OIDC/JWKS, issuer configuration, MFA integration or verified claim-to-signature binding. The production admission and registration release boundaries remain locked. No clinical authorization is granted by these components.

## Four independently tested P02 increments

7. **P02-B0044** `privateReturnRoute`: shell-specific canonical return-path allowlist rejecting encoded/query/fragment/open-redirect/patient-id navigation. Node test passed.
8. **P02-B0045** `PatientProxyContext`: bounded, volatile proxy UI context with expiry, revocation, background and subject-switch clearing; wired background clearing into patient mobile shell. Node and native source checks passed. Server-side proxy authorization still absent.
9. **P02-B0046** `MobileVisibilityFence`: protected-view generation fence; background event invalidates visibility and resets navigation in both staff and patient React Native shells. Node and native source checks passed. No Android/iOS runtime, background kill or device test.
10. **P02-B0047** `accessibleLockedStatus`: bilingual, nonidentifying, screen-reader alert for denied registration; bound into native staff preview with visible lock. Node and native source checks passed. No device screen-reader test.

## Actual verification and limitations

- 10 individually executed batch commands with exit code 0 and preserved stdout: `evidence/batches/v170-P*.log`.
- New Java assertions: **46**. New Node test cases: **4**. Inherited Java suite passed.
- Full TypeScript/Node suite: **80/80** passing. Source checks: **4 TSX syntax parses** plus web/native static tests; these are not Next/Expo builds.
- Static Chromium specimens: **48/48** checks (10 legacy, 19 bilingual, 19 registration), not compiled application tests.
- Additional Python SQL/schema/contract/source tests: **23/23**, no PostgreSQL execution.
- Release request refused with exit code **3**; successful fail-closed test, not a successful release.
- `npm ping` failed with `EAI_AGAIN`; no dependency installation, lockfile, Next.js build or Expo binary.
- No live PostgreSQL RLS, durable reviewer decisions, OIDC identity verification, independent clinical/security acceptance, mobile device tests or production readiness.

## Security review findings

The new primitives are separately useful and testable but **not composable into a trusted identity admission path** until a supported JOSE library safely parses signed JWTs, rejects duplicate JSON keys and algorithm confusion, pins a trusted JWKS/key source, verifies issuer/audience/authorized party/nonce/MFA/revocation and binds claims to the exact verified signature. Server-side tenant/facility/patient scopes must then be checked against live authoritative IAM and PostgreSQL restrictions. No shortcut is permitted.

## Next exact batch

`P05-B0027` remains **blocked and not counted**: implement and execute atomic reviewer approval/revocation, append-only event, audit and outbox transaction under restricted PostgreSQL roles with RLS and concurrency tests. If infrastructure remains unavailable, next independently unblocked target is `P03-B0024`, supported JOSE/JWK parser and signed-claims integration (subject to dependency availability); continue other safe foundational work as appropriate.
