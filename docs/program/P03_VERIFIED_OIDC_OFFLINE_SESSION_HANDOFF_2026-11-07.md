# P03 verified OIDC identity session — M38 integration contract (2026-11-07)

**Scope:** reusable server-only verifier, not a running P03 identity provider or a shipped Next endpoint. The M38 PR #56 currently decodes the operations access cookie payload without verifying the JWT signature; it MUST NOT be trusted for device encryption scope or queue binding.

## Implemented code
- `apps/web/lib/verified-oidc-session.mjs` provides `verifyOperationsJwt`, `fetchIssuerJwks`, and `IdentityDenied`. RSA RS256 only; exact issuer/audience, signature, fixed JWKS key ID and type, user/cell/facility UUID, bounded token and grant lifetimes, nbf/iat, allowed grant syntax, server-only validated key lookup, fail-closed HTTPS OIDC discovery and same-origin JWKS; no redirect following.
- `tests/verified_oidc_session.test.mjs`: generates a real 2048-bit RSA keypair, signs synthetic OIDC tokens, verifies accepted scopes and adversarial claim/signature/header/discovery denial.

## Required wiring (separate PR)
1. Derive issuer and audience from **trusted server configuration** and approved P03 identity contracts. Do not take them from user input or JWT header.
2. Server-side obtain JWKS from `fetchIssuerJwks(issuer)`. Cache per trusted issuer for a bounded key-rotation TTL; fail closed on expired/unavailable keys. Never let a browser access signing keys or the bearer cookie. Test key rollover.
3. In `apps/web/app/api/offline/session/route.ts` (currently owned by Claude PR #56), call `verifyOperationsJwt(token,{issuer,audience,jwks})`; return only verified userId, hospital scope and bounded expiresAt, all `Cache-Control:no-store`. **Do not merge the original unverified session decoder.**
4. Verify the same OIDC issuer/audience and synqoria scope configuration in M38 Spring backend and operations auth; run real HTTPS Next cookie + CSRF + proxy + M38 PostgreSQL E2E.
5. Separate identity validation from clinical authorization and server-side row-level policy. A cryptographically valid JWT is not sufficient permission to change medical records.
6. Exact JWT grant format and token lifetimes must match P03 production profile. The library's 900s maximum access-token lifetime is an explicit conservative design requirement and must be policy-approved before shipping.

## Outstanding
No live identity provider, KMS, session revocation/jti validation or Next route replacement is included. P03 security and hospital deployment require further engineering and acceptance. This library cannot independently prove token revocation; the issuer/introspection layer must enforce revocation for appropriate workflows.
