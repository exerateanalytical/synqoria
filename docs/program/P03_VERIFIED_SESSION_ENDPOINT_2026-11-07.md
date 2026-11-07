# P03 verified operations session endpoint — integration contract

This work provides a **server-verified, minimal identity binding** endpoint at:

`GET /api/operations/auth/verified-session`

The handler extracts the existing **HttpOnly** `__Host-synq_ops_access` cookie and verifies its RSA-SHA256 signature using the trusted issuer's HTTPS OIDC discovery/JWKS, exact issuer and audience, valid expiry and grant window, hospital-cell/facility/user UUID and the `m38:sync_read` permission. It exposes **only** `userId`, `scope` and `expiresAt`, never the bearer token or JWKS. A missing token, untrusted JWKS, invalid signature, missing scope or issuer outage rejects (no fallback). Content uses `Cache-Control: no-store`, `Vary: Cookie` and `nosniff`.

## Required deployment configuration
- `SYNQORIA_OPS_OIDC_ISSUER`: same issuer as M38 Spring Resource Server.
- `SYNQORIA_OPS_OIDC_CLIENT_ID`: existing operations OAuth client.
- `SYNQORIA_OPS_OIDC_AUDIENCE`: **required** precise audience claimed by the operations access token. Must be validated against the independently configured M38 audience; do not assume the UI client ID is the API audience.
- `SYNQORIA_OPS_WEB_ORIGIN`, `SYNQORIA_OPS_API_ORIGIN`: existing trusted HTTPS origins.

This route is for authenticated identity metadata, **not permission to perform clinical writes**. It is not an OIDC token introspection endpoint, revocation service, session refresh or KMS-backed device attestation. Fail closed until configuration and real issuer are present; the HTTP API still authorizes every request.

## Claude M38 PR #56 integration
1. Fetch current main, including P03 PR #61 and this route.
2. **Remove the unverified JWT payload decoder** in `apps/web/app/api/offline/session/route.ts`. Delegate to the verified session endpoint or, preferably, call the same server-only `verifiedOperationsSession` helper directly. Preserve the exact expected device-client JSON shape `{userId, scope, expiresAt}`.
3. Ensure the requested M38 permission and audience agree with the M38 Spring service; test revoked/expired/mismatched actors, browser cookies, CSRF/Origin, and cross-hospital isolation.
4. Repeat actual Next/real-browser/service tests; do not mark M38 done merely because this verified P03 source builds.

## Engineering verification
`node --test tests/verified_oidc_session.test.mjs tests/operations_verified_session.test.mjs`

`npm ci && npm run build --workspace @synqoria/ux-core && npm run build --workspace @synqoria/web`

No production issuer configuration, revocation/introspection, or hospital deployment acceptance is implied.
