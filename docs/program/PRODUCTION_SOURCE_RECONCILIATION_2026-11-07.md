# SYNQORIA source integration inventory — 7 November 2026

This repository now contains the merged **source** history of P06/P07 clinical code, P03 trusted staff identity,
the 261 canonical screen routes and document templates, seven hospital operations modules, OPS001 screens,
Claude M24/M26/M28/M30 and ChatGPT M02/M16/M27/M32/M34/M35.

Canonical registry: **40 planned modules / 261 screens**. All module/screen developments must start
by reading existing code and authority registers. `AGENTS.md` now forbids duplicate implementations.

All source heads are exact-SHA recorded in `registers/production-source-ledger-2026-11-07.json`.
Selected conflicts: latest P03 identity, OPS001 enhanced canonical router, PR24 native manifests,
M27 latest custody and operations guards.

## Consolidation rule
**Build once, integrate once, extend in place.** Every module and screen has one canonical ID and a
defined owner. Other paths are compatibility redirects; a disabled UI shell must be wired to
the already implemented service, not rebuilt with different visual conventions.

## Verified now
- Source histories have been combined on `integration/synqoria-production-reconciliation-2026-11-07` and Git parent relationships preserved.
- Existing 261 canonical routes retained.
- M02 (5), M16 (5), M35 (6) mapped into 16 existing canonical screen URLs, old URLs redirect.
- Dedicated domain CI previously green on each source owner branch.

## Unfinished integration acceptance gates
1. Current root dependency lock and native/web package-version compatibility.
2. Unified Next build and monorepo tests with all code histories present.
3. Shared staff OIDC/Keycloak and clinical/financial authority integrations.
4. Security and data integrity acceptance before deployment; hospital functional trials; certification later.

No live patient deployment is authorized by a source merge. No PR or CI success is a clinical certificate.
