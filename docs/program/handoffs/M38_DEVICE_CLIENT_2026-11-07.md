# M38 Offline Device Client and Synchronization Integration - engineering handoff (2026-11-07)

Branch `work/claude-m38-device-client-integration-2026-11-07`, from `main` @ `aa886ccdd1226131532fb9e28fac93f5b43e3c89` (merge of PR #52). Assignment: issue #53.
**Source-engineering only. Not hospital acceptance, deployment approval or certification. 30/30 packages were NOT completed; the honest per-package status is in the matrix below.**

## 0. Pre-flight (package 01): ownership and overlap inventory
| Area | What exists on main | Decision |
|---|---|---|
| M38 server | `services/offline-sync-core`, `contracts/openapi/synqoria-m38-offline-v1.yaml`, `apps/web/{app/workforce,app/api,lib,components}/offline` (5 staff screens + proxy) | **Reused unchanged** except: proxy allow-list + one body bound (see below), one new test class, one component mounted on the existing status screen |
| Device client | none (`grep` for IndexedDB / service worker / secure-store / sqlite / async-storage in `apps/*`, `packages/*`: no hits) | **New** `packages/offline-device-sdk` |
| Mobile shells | `apps/staff-mobile`, `apps/patient-mobile`: Expo 54 / RN 0.81, no storage dependency | Not touched (adding storage = dependency/lock change) |
| Identity | P03 operations sign-in (`lib/operations-auth.ts`, `/api/operations/auth/*`) | Reused; no change |
| 261 canonical screens / registers | owned by the integration lead | Not touched; no new screen routes (device UI is a panel on the existing `/workforce/offline/status`) |
| M39, M40, M24-M30, lockfiles, package versions | other owners | Not touched. `apps/web/tsconfig.json` gained `allowImportingTsExtensions` (compiler flag, no version) so the web app can import the SDK |

## 1. Architecture
```
 +-------------------------- installed PWA (apps/web) ---------------------------+
 |  Status screen (existing)  ->  DevicePanel  ->  lib/offline/device.ts          |
 |                                                   |                            |
 |        packages/offline-device-sdk  (platform-neutral, no runtime deps)        |
 |   admission -> DurableQueue -> SyncEngine -> Transport --> same-origin fetch   |
 |        |            |             |  ^                                          |
 |   ContractCache  EncryptedStore  LeaderElector  Backoff   Diagnostics(redacted) |
 |                      |  (AES-256-GCM, non-extractable key)                      |
 |                IdbStore + IdbKeyVault (IndexedDB)                               |
 +--------------------------------|-----------------------------------------------+
                      HttpOnly session cookie + CSRF header (no token in script)
                                   v
              /api/offline/proxy/*  (allow-list, Origin+CSRF, bounds)   /api/offline/session (ids+expiry only)
                                   v
            M38 service (Spring, PostgreSQL FORCE RLS) - the only authority
```
Data flow for one change: admission (contract approved, not safety-gated, offline-allowed, epoch current, scope/user match) -> atomic enqueue (sequence + quotas + sealed write) -> batch persisted *before* sending (key + sequence numbers) -> `POST sync/batches` with `Idempotency-Key` -> per-command outcomes applied -> in-flight marker cleared last -> payload purged on a final outcome.

## 2. What was built (paths)
* `packages/offline-device-sdk/src`: `types, validate, store, crypto, queue, admission, backoff, transport, leader, diagnostics, conflict, policy, sync, idb, index` (+ `package.json`, `tsconfig.json`; not a workspace member)
* `packages/offline-device-sdk/tests`: `core, sync, interop, security` (+ `helpers.mjs` fake server, `fixtures/m38-real-batch-replies.json`)
* `apps/web/lib/offline/device.ts`, `apps/web/components/offline/DevicePanel.tsx` (mounted by `StatusBoard.tsx`), `apps/web/app/api/offline/session/route.ts`
* Changed: `apps/web/lib/offline/proxy-policy.ts` (+`POST sync/batches`, +`GET devices/{id}/cursor`), `apps/web/app/api/offline/proxy/[...segments]/route.ts` (1 MiB bound for `sync/batches` only), `apps/web/lib/offline/i18n.ts` (+`dv.*`, `enum.local.*`, device errors, EN/FR), `tests/m38_offline_frontend.test.mjs`, `apps/web/tsconfig.json`
* `services/offline-sync-core/src/test/java/.../SdkInteropFixtureTest.java` (test only; no service code changed)
* `tests/test_m38_device_sdk_contract.py`, `.github/workflows/m38-device-client-ci.yml`, `docs/architecture/decisions/ADR-M38-device-client.md`, this note

## 3. Key lifecycle, retention and rollout policy
* **Key:** generated (AES-GCM 256, non-extractable) at first unlock after a server-verified session; kept as a `CryptoKey` in IndexedDB; **LOCK** drops the in-memory handle (data stays sealed; unlocking requires a valid session of the bound user and scope); **WIPE/revocation** destroys the key and erases the store (crypto-shredding). Unsent changes are lost on wipe - the UI says so and asks for confirmation.
* **Retention:** unsent commands are kept until sent or expired (default <= contract lifetime); a final outcome purges the clinical payload immediately and keeps metadata (default 7 days / 500 entries) then deletes it; sequence numbers are never reused. Defaults are code constants; remote policy delivery is not implemented.
* **Rollout:** (1) enable in a disposable environment with synthetic data; (2) register a test device through the administrative path; (3) pilot on one ward with online-only fallback; (4) only operations that a domain owner approved in M38 contracts, none safety-gated; (5) a kill-switch is the existing device revocation (device wipes itself at the next sync). Each stage needs the owners' sign-off - none is given here.

## 4. Threat model (summary)
| Threat | Control | Evidence |
|---|---|---|
| Stolen/unlocked device | sealed storage, non-extractable key, lock/wipe, session expiry suspends and locks | `core`, `sync` tests |
| Cross-hospital / cross-user queue mixing | scope + user checked at admission and before every sync; server RLS | `sync`: isolation test; M38 Java tests |
| Replay / duplicate / reorder | per-command ids, per-batch keys, identical resend, per-device sequence | `sync`: fault tests |
| Token theft by script/XSS | no token in script (HttpOnly cookie), no HTML injection, redacted logs, server vocabulary only in local state | `security` tests |
| CSRF | writes carry the CSRF header; proxy enforces Origin + CSRF | `security`, M38 frontend tests |
| Oversized payloads | 16 KB per command, 50 per batch, 1 MiB request, quotas | `sync`: bounds test |
| Hostile server reply | strict response validation; unknown statuses suspend | `core`, `security` |
| Clinical safety bypass | safety-gated/unknown operations refused locally and by the server; server adjudicates | `core`, interop fixture |
Not modelled: malware on the device, malicious browser extensions, compromised OS, side channels.

## 5. Attachments (package 13) - contract proposal only
M38 publishes **no** attachment endpoint, so none was built or invented. Proposal for a future versioned contract: `POST /attachments` (create upload, returns id + chunk size <= 256 KiB), `PUT /attachments/{id}/chunks/{n}` (idempotent by chunk index, SHA-256 header), `POST /attachments/{id}/completion` (whole-file SHA-256, size), `GET /attachments/{id}` metadata. Requires: a new M38 contract version (1.1.0), RLS-scoped storage, malware scanning, size/type allow-list, retention rules, and an SDK queue entry type that references (never embeds) the file.

## 6. Status of the 30 packages (honest)
Legend: **V** implemented and verified by the tests named; **P** partially implemented or verified; **D** design/documentation only; **-** not done.
| # | Status | Notes |
|---|---|---|
| 01 | V | inventory above |
| 02 | V | ADR-M38-device-client |
| 03 | P | typed, strictly validating client; hand-written, parity-tested against the OpenAPI (not generated); no server version-negotiation endpoint exists, so the client pins `1.0.0` |
| 04 | P | binding verified against the server (`GET devices/{id}/cursor`, ACTIVE) before it is stored; registration itself stays administrative (no self-registration API); UI typed, not rendered in a browser |
| 05 | P | AES-GCM + key lifecycle verified in Node; `IdbStore`/`IdbKeyVault` not exercised in a real browser; no Expo/SecureStore adapter |
| 06 | V | atomic enqueue, append-only sequence, quotas, retention (`core`) |
| 07 | V | admission matrix (`core`); server re-adjudicates |
| 08 | P | connectivity probe, manual sync and bounded jittered backoff verified; no automatic scheduler/timer loop in the UI |
| 09 | V | <= 50, batch keys, partial/retryable outcomes, cursor persistence (`sync`) |
| 10 | P | restart/kill/partition/expiry verified with the in-memory store; not with a real browser process |
| 11 | P | stale/invalid epoch and frozen records refused locally and answered by the server fixture; no lease acquisition API exists on the server to wire |
| 12 | P | exact server codes preserved; deterministic resolution request builder with reviewer != author; the resolution screen is the existing server-backed S03, not re-wired to the local queue |
| 13 | D | proposal only (section 5) |
| 14 | V | local per-command states + exact server code; list in DevicePanel |
| 15 | P | suspend/lock/wipe on expiry and revocation verified in the SDK; UI not browser-verified |
| 16 | P | LOCK/WIPE policy and retention verified; policy is not remotely configurable |
| 17 | P | lease election verified with racing instances; `webLockLeader` adapter exists but is not wired or tested |
| 18 | P | EN/FR parity enforced by test, existing design tokens; no accessibility audit, no screen-reader test |
| 19 | V | allow-list sanitiser, leak test over a full run |
| 20 | V | metadata-only export (SDK + button); no payload, key, command id |
| 21 | P | DevicePanel on the existing status screen; the other existing pages were already server-backed |
| 22 | P | uses the existing cookie sign-in; no hard-coded grants; no end-to-end run against a live IdP |
| 23 | V | `tests/test_m38_device_sdk_contract.py` (drift proven by a negative run) |
| 24 | P | **golden replies recorded from the real service on PostgreSQL 18** and replayed through the SDK; drift guarded in Java. **No live HTTP/OIDC end-to-end run** (the service needs an https issuer) |
| 25 | V | drop / drop-after-processing / duplicate / http 503 / retry budget (`sync`) against the fake and the recorded real replies |
| 26 | P | client-side scope/user isolation verified; server-side isolation verified by the M38 Java suite; no combined live test |
| 27 | P | expired session (401), revoked device (403), missing permission (403), user mismatch, maker/checker (SDK + M38 Java tests); not against a live IdP |
| 28 | P | static and direct privacy checks; no penetration test |
| 29 | V* | dedicated workflow (below). *Result for the pushed SHA is on the PR |
| 30 | V | this note |
Totals: **V 12 (incl. 29 pending CI)**, **P 17**, **D 1**, **not done 0**. 30/30 is **not** claimed; no package is marked complete without its tests.

## 7. Verification evidence
* `node --test packages/offline-device-sdk/tests/` -> **41 pass, 0 fail** (core 13, sync 17, interop 7, security 4).
* `tsc -p packages/offline-device-sdk/tsconfig.json` (TypeScript 5.8.3, strict, `erasableSyntaxOnly`) -> no errors. Web DevicePanel/device.ts/session route type-check under the web tsconfig flags (css-module declaration supplied by Next).
* `python tests/test_m38_device_sdk_contract.py` -> 9 routes, contract 1.0.0; negative run (renamed route and field) fails as intended.
* `node --test tests/m38_offline_frontend.test.mjs` -> 9/9 (updated for the device routes and the 1 MiB bound).
* `mvn test -Dtest=SdkInteropFixtureTest` (Java 25, PostgreSQL 18.6) -> passes; fixture recorded from the real service.
* GitHub Actions for the exact pushed SHA: see the PR.

## 8. Real limitations
No real browser/emulator/device run (IndexedDB adapters, service worker, background sync and mobile shells are untested); no automatic sync scheduler; no attachment transfer; no Expo adapters; no live OIDC + service + browser exercise; no `next build` evidence unless the informational CI lane reports it; remote retention policy absent; Web Locks adapter unwired.

## 9. Integration dependencies for the integration lead
1. Approve storage dependencies if native shells are wanted (`expo-sqlite`, `expo-secure-store`) - adapters then implement `Store`/`KeyVault`.
2. Provide a device registration path (administrative UI or API) and decide who may register devices for whom.
3. Decide the session-introspection source of truth (this branch adds a minimal, non-verifying `GET /api/offline/session`; a verified one belongs to P03).
4. A live end-to-end rig (https issuer, service, browser) to turn packages 22/24/26/27 from **P** to **V**.
5. Domain adapters in the owning clinical modules that consume accepted commands (already an open M38 server dependency).
