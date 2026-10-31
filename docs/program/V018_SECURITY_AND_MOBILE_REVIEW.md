# v0.18.0 — Security and Mobile Implementation Review

## Implemented

1. **P03-B0024:** operator-pinned RFC 7638 RSA JWK thumbprint validation; no trust of token-provided keys.
2. **P03-B0025:** exact three-property protected-header policy for RS256 `at+jwt`, trusted key ID; rejects algorithm confusion, `jku`, `crit` and injected key material.
3. **P03-B0026:** byte-level JWS signature verification with active/revoked key metadata and exact externally parsed header byte match. Returns signed **bytes only**, not authenticated claims or access grants.
4. **P03-B0027:** structurally validates separately trusted claims (issuer, audience, azp, subject, session, token ID, time). Not connected to verified bytes and MUST NOT be used for production authentication.
5. **P03-B0028:** PHI-free security-denial audit metadata with keyed HMAC session pseudonym. Not a durable, hospital-approved audit custody service.
6. **P02-B0048:** safe staff root navigation intent allowlist; never authorizes clinical deep links.
7. **P02-B0049:** foreground generation reauthorization; denies stale/offline/revoked views.
8. **P02-B0050:** bilingual generic device notification content, empty data payload.
9. **P02-B0051:** bilingual readonly four-stage registration journey in native staff preview.
10. **P02-B0052:** four labelled, disabled and accessible mobile destinations in staff and patient native source shells.

## Review limitations

- Five Java unit programs and five TypeScript Node tests passed individually; detailed logs in `evidence/batches/v180-*.log`. No independent security audit.
- **Never** treat P03-B0024–B0027 as a JWT verifier: a vetted duplicate-key-rejecting JSON parser, claims-to-signed-byte binding, trusted IdP/JWKS metadata, session revocation and live grants are missing. A malicious caller could supply unrelated parsed claims if these components were miswired.
- React Native/Expo and Next.js compilation, devices, screen readers and PostgreSQL were unavailable; native wiring was checked by TSX syntax and static source tests only.
- All clinical actions, reviewer approval and registration POST remain fail-closed. No approved protocols or medication logic generated.
- Source IDs and 40/261 inventory unchanged; no clinical screen marked implemented. All phases remain uncertified.

## Exit gates and next

Clinical and production release require real PostgreSQL restricted-role RLS and serializable transaction tests, supported JOSE library with trusted provider, compiled web/mobile binaries, device/accessibility checks, human clinical/security sign-off and pilot qualification. Resume P05-B0027 only with those database prerequisites; otherwise continue independent safety and UX work.
