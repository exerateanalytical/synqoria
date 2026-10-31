# SYNQORIA v0.15.0 — Reviewer boundary and staff mobile navigation review

This checkpoint contains **10 independently invoked, testable engineering increments**, not ten accepted clinical screens.

## Implemented source

| Batch | Deliverable | Evidence level |
|---|---|---|
| P05-B0016 | `VerifiedReviewerSession`: scoped tenant, facility, distinct reviewer, signed-in freshness, capability, revocation and policy epoch | Java unit only; no IAM/JWKS |
| P05-B0017 | `ReviewerSeparationFence`: independent reviewer and exact candidate revision | Java unit only |
| P05-B0018 | `ReviewRevocationFence`: time-bounded evidence, decision revision, revocation and candidate revision | Java unit only; not DB-backed |
| P05-B0019 | `ReviewAuditBinding`: domain-separated SHA-256 audit correlation fingerprint without demographics | Java unit only; not persisted/signed |
| P05-B0020 | `ReviewApprovalReleaseFence`: local readiness flags can never enable registration writes | Java unit only |
| P05-B0021 | `RegistrationReviewPreflight`: composes reviewer session, candidate revision and decision freshness without releasing mutations | Java unit only |
| P02-B0037 | `registration-duplicate-status`: bilingual status without candidate identities/counts; always blocked | TypeScript unit only |
| P02-B0038 | `registration-context-fence`: destroys volatile draft on logout, role/tenant/facility/session changes | TypeScript unit only |
| P02-B0039 | `registration-accessible-actions`: bilingual visible disabled actions, 48px target and native staff preview | TypeScript unit + TSX source parsing only |
| P01-B0031 | `V008__registration_review_event_quarantine.sql`: composite tenant keys, event versions, forced default-deny RLS | SQL source static; **unapplied** |

## Verification and limitations

The ten logs `evidence/batches/v150-P*.log` record individual commands, captured output and zero exit codes. Inherited checks: `npm run check`, `bash tools/check_java_security.sh`, `npm run check:source`, and draft SQL test all succeeded. Chromium source specimens succeeded separately (10 + 19 + 19 checks). These do **not** constitute Next.js/Expo builds or tests on real devices.

`npm ping` failed with `EAI_AGAIN` (captured in `v150-npm-registry.log`); native dependencies are not installed, so no web or mobile build claim is made. PostgreSQL (`psql`/`pg_ctl`) is unavailable. The review decision and event migrations have not been executed against an RLS-enabled database, and no independent reviewer IAM, approval ledger, identity provider or signed clinical release exists.

**Important:** The `VerifiedReviewerSession` type is a server-internal contract; an external request can never supply it and gain trust. This checkpoint provides no runtime authentication verifier or write endpoint. `RegistrationReviewPreflight` has no release-success state. `ReviewApprovalReleaseFence` never returns authorization to write. The test-only `RegistrationJdbc` seam remains unexposed. HMAC test proofs are not production approvals. `V008` intentionally has **zero permissive RLS policies**.

The staff preview remains a read-only/disabled UI surface. The canonical `M01-S02` screen has not met its implementation or clinical acceptance contract. All **261** canonical screens remain unimplemented.

## Next dependencies

1. Provision PostgreSQL plus a restricted non-owner role to execute RLS denials, review/audit transactions, cross-tenant scope tests, rollback and recovery scenarios.
2. Wire real OIDC/JWKS, workforce grant validity, reviewer/registrar separation and approval revocation with clinical safety owner sign-off.
3. Restore dependency installation to compile and test Next.js and both Expo apps; use actual Android/iOS devices, accessibility testing, long French text and 200% scaling.
4. Keep registration POST and all clinical writes locked until the full set of evidence gates has been independently accepted.
