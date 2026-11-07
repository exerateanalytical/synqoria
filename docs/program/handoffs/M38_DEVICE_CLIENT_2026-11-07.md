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
              /api/offline/proxy/*  (allow-list, Origin+CSRF, bounds)   /api/operations/auth/verified-session (P03; ids+expiry only)
                                   v
            M38 service (Spring, PostgreSQL FORCE RLS) - the only authority
```
Data flow for one change: admission (contract approved, not safety-gated, offline-allowed, epoch current, scope/user match) -> atomic enqueue (sequence + quotas + sealed write) -> batch persisted *before* sending (key + sequence numbers) -> `POST sync/batches` with `Idempotency-Key` -> per-command outcomes applied -> in-flight marker cleared last -> payload purged on a final outcome.

## 2. What was built (paths)
* `packages/offline-device-sdk/src`: `types, validate, store, crypto, queue, admission, backoff, transport, leader, diagnostics, conflict, policy, sync, scheduler, idb, index` (+ `package.json`, `tsconfig.json`; not a workspace member)
* `packages/offline-device-sdk/tests`: `core, sync, interop, security, scheduler, idb` (+ `helpers.mjs` fake server, `fixtures/m38-real-batch-replies.json`) and `live/{rig.mjs,live.test.mjs}` (real-service rig)
* `apps/web/lib/offline/device.ts`, `apps/web/components/offline/DevicePanel.tsx` (mounted by `StatusBoard.tsx`). The device reads identity from P03's `/api/operations/auth/verified-session` (the local adapter and `/api/offline/session` were removed)
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
Legend: **V** implemented and verified by the named tests/evidence; **P** partial (what is missing is stated); **B** partial and **blocked** on an external dependency or a missing server capability (named); **D** design/documentation only. Evidence kinds: *Node* = SDK tests, *live* = real Spring service + PostgreSQL 18 + HTTPS OIDC issuer, *e2e* = the same through the real Next route handlers (cookie, CSRF, Origin, allow-list), *browser* = executed in a real Chromium 152 (the desktop app's embedded browser) via `packages/offline-device-sdk/browser-check` and the UI stack.
| # | Status | Notes |
|---|---|---|
| 01 | V | inventory above |
| 02 | V | ADR-M38-device-client |
| 03 | B | typed, strictly validating client (`src/transport.ts`, `src/validate.ts`; Node), parity-tested against the OpenAPI (`tests/test_m38_device_sdk_contract.py`); pinned to contract 1.0.0. **Blocked:** M38 publishes no version-negotiation endpoint (proposal below); the client is not generated from the OpenAPI |
| 04 | V | binding verified against the server's own device list (exists, ACTIVE, bound to exactly the signed-in user, live window): `src/binding.ts` (Node + live + browser); identity comes from P03's cryptographically verified `/api/operations/auth/verified-session` (e2e); enrollment reuses `POST /devices` through the proxy, privileged (`m38:device_manage` enforced by the service), scoped, idempotent, audited. Limit inherited from P03: no revocation/introspection of the access token (the service re-verifies on every call and the device probes for revocation) |
| 05 | V | web PWA: AES-256-GCM, non-extractable key, slot-bound ciphertext, lock/unlock, crypto-shred - `src/crypto.ts`, `src/idb.ts` (Node, in-memory IndexedDB double, **browser**: real IndexedDB bytes contain no clinical text, key not exportable, survives reload, wipe empties both stores). Native Expo adapters are an approved *separate* workstream (not in this PR) |
| 06 | V | atomic enqueue, append-only sequence, quotas, retention (`core`) |
| 07 | V | admission matrix (`core`); server re-adjudicates |
| 08 | V | `SyncScheduler` (injected timers): drain, idle polling, nudge on enqueue/`online`, bounded jittered retry, stops on suspension/wipe, never overlaps; wired in the device panel after a verified unlock. Verified with fake timers (`scheduler`); the browser wiring is typed, not run in a browser |
| 09 | V | <= 50, batch keys, partial/retryable outcomes, cursor persistence (`sync`) |
| 10 | V | restart, kill, partition and expiry: Node + live (crash after server applied -> identical batch resent, applied once); **browser**: real page reload keeps the encrypted queue and continues the sequence; a restarted engine resends the same batch key |
| 11 | B | stale/invalid epoch and frozen records refused on the device (`src/admission.ts`) and fenced by the real service (live); **Blocked:** no lease/fence acquisition API exists on the server - nothing to wire (proposal below) |
| 12 | V | exact server codes preserved (`src/conflict.ts`); deterministic resolution request, reviewer != author checked on the device **and** by the service through the proxy (e2e: author 403 `resolver_must_be_independent`, no permission 403, missing CSRF 403, reviewer 200, replay 409); the existing S03 screen renders the real conflict (local vs authoritative version, fields, epoch) in the browser; conflicted entries in the device panel link to it |
| 13 | D | proposal only (section 5) |
| 14 | V | local per-command states + exact server code; list in DevicePanel |
| 15 | V | expiry (401) suspends and locks, revocation wipes, an idle probe detects revocation even with nothing to send (`src/sync.ts`); Node + live (real 401/403) + **browser** (lock refuses reads, wipe empties IndexedDB and forgets the binding) |
| 16 | B | LOCK/WIPE policy and bounded retention verified (Node + browser wipe). **Blocked:** no remote retention-policy API (proposal below); policy values are code constants |
| 17 | V | lease election verified with racing instances; Web Locks adapter wired into the scheduler and verified against a lock-API double (a real browser lock manager was not run) |
| 18 | P | EN/FR key parity enforced by test; **browser** checks on the status screen: 0 unlabeled controls, 0 duplicate ids, 0 contrast failures (WCAG AA computed over all text), skip link and 3 px focus ring on Tab, no horizontal overflow at 375 px in EN and FR, tables become cards on mobile, heading order fixed. Not done: screen-reader testing and a formal audit of the other four screens |
| 19 | V | allow-list sanitiser, leak test over a full run |
| 20 | V | metadata-only export (SDK + button); no payload, key, command id |
| 21 | V | the existing screens are server-backed (no new route); **browser**: status screen with devices/counters and the conflict screen render real service data through Next -> proxy -> service; device panel mounted on the status screen; the other three screens were not screenshotted (they are unchanged M38 screens covered by the e2e GET checks) |
| 22 | V | cookie sign-in -> P03 verified session -> proxy -> permissions enforced by the service, no hardcoded grants: e2e against the real service (7 tests incl. valid token without `m38:sync_read` -> 401, forged/tampered/unsigned/wrong aud/iss/expired/over-long grant -> 401). Browser UI-stack run was done against the previous adapter with the identical `{userId,scope,expiresAt}` shape; it was not repeated against the P03 route |
| 23 | V | `tests/test_m38_device_sdk_contract.py` (drift proven by a negative run) |
| 24 | V | **live run**: the SDK drives the real Spring service over HTTP (JWTs validated against a real HTTPS OIDC issuer) on real PostgreSQL 18 with forced RLS, synthetic records, device-bound accounts (`tests/live`, 10 tests). Not covered: the Next proxy and cookie sign-in in front of the service |
| 25 | V | drop / drop-after-processing / duplicate / http 503 / retry budget (`sync`) against the fake and the recorded real replies |
| 26 | V | live: another hospital's tokens see no devices/heads, get 404 for the device and cursor; a device bound to hospital A refuses a hospital-B session; PostgreSQL RLS is in the path |
| 27 | V | live against the real service: expired token (401), grant longer than 15 minutes (401), missing `m38:sync_submit` (403), wrong user (`device_user_mismatch`), revoked device (`device_revoked` then wipe), maker/checker on conflict resolution, wrong issuer/audience (401). The IdP is a local test issuer, not P03 |
| 28 | V | encrypted storage in a real browser; redacted logs (Node leak test); XSS (no HTML injection, server codes constrained to a vocabulary, React escaping); CSRF + Origin + allow-list + body/query bounds + idempotency through the real Next handlers (e2e: 6 tests); session route verified by 4 identity tests. This is not a penetration test |
| 29 | V* | dedicated workflow (below). *Result for the pushed SHA is on the PR |
| 30 | V | this note |
Totals: **V 25**, **P 1**, **B (blocked on a missing server API) 3**, **D 1** (30 packages). 30/30 is **not** claimed. Blocked: 03 (version negotiation), 11 (leases), 16 (remote retention policy); design-only: 13 (attachments); partial: 18 (accessibility audit).


## 6b. Continuation (integration lead's directive on PR #56)
Reconciled with `main` (merge commit; no conflicts; M39/M40 paths untouched). Changes since the first verified head:
* **P03 integration (supersedes the next bullet):** merged main incl. PR #61/#63; removed `/api/offline/session`, `lib/offline/identity.ts` and `tests/m38_identity.test.mjs`; the device now calls `GET /api/operations/auth/verified-session` (required env `SYNQORIA_OPS_OIDC_AUDIENCE`; 503 `identity_not_configured` => device stays blocked, 401 => signed out). The proxy e2e shadow app copies the P03 route and libraries verbatim. CI runs P03's own two test files alongside the frontend tests.
* *(historical)* **Session route failed closed.** `GET /api/offline/session` no longer decodes claims: it answers **503 `identity_verification_not_integrated`** unless `SYNQORIA_OFFLINE_SESSION_VERIFIER=jwks` and an audience are configured, and then only returns a session after `lib/offline/identity.ts` verifies the RS256 signature against the issuer's JWKS (algorithm allow-list, exact issuer, audience, exp/nbf, subject, cell/facility, grant <= 15 min; JWKS over https, same origin as the issuer, bounded, cached 5 min). Any failure -> no session. The device panel shows a blocked banner and keeps enrollment, unlock and sync disabled. The verifier is an *adapter pending the P03 contract / a vetted JOSE library*; replacing it does not change callers. Not enabled by default anywhere.
* **Device enrollment** reuses the existing service: the proxy forwards `POST devices`; the service enforces `m38:device_manage`, scope, idempotency and the audit trail. UI: "Enroll a device (administrators)" on the existing status screen, available only with a verified session, with an explicit scope confirmation.
* **Binding verification** (`src/binding.ts`) replaces the previous "cursor + ACTIVE" check, which could not prove the bound user.
* **Idle revocation probe** (`PROBE_INTERVAL_MS`): a device with nothing to send still discovers revocation.
* **Proxy defect found by the e2e test and fixed:** Spring answers an invalid/expired bearer with an *empty* 401 body, which the proxy turned into 503 `offline_unavailable`; the device would have retried forever instead of suspending. The proxy now maps empty 401/403 to `identity_denied`/`permission_denied`.
* **UI defects found in the browser and fixed:** the "Language" label wrapped mid-word, stat cards showed list bullets, disabled-button reasons said "Select a record first" when no device was bound, heading levels skipped h2.

### Browser evidence (executed, not CI)
Chromium 152.0.7977 (the desktop app's embedded browser), `http://localhost` secure context, SDK compiled with `tsc` (no bundler) by `browser-check/serve.mjs`:
| Check | Result |
|---|---|
| WebCrypto + IndexedDB + Web Locks present | pass |
| AES key non-extractable and persisted as a CryptoKey | pass |
| IndexedDB bytes contain no clinical text / record key / identifier | pass |
| failing transaction rolled back; queue readable after "restart" with the persisted key; locked store refuses reads; WIPE empties both databases | pass (8/8) |
| dropped connection -> SUBMITTED; restarted engine (after lease expiry) resends the **identical batch key**; applied; payload purged | pass (3/3) |
| real page reload: encrypted queue intact, sequence continues, still ciphertext | pass (3/3) |
| two tabs: the second cannot run while the first holds the sync Web Lock | pass |
| two tabs x 150 concurrent store transactions on one counter | 300 exactly (no lost update) |
| UI stack (real service over HTTPS + Next route handlers + OIDC issuer): cookie -> verified session -> status screen with real devices; bind via server verification; unlock; refresh approved operations; sync (idle); lock; wipe; conflict screen with real data | pass |
Re-run with `node packages/offline-device-sdk/browser-check/serve.mjs` and open `http://localhost:8321/?phase=all` (and `write`/`read`, `hold`/`try`, `counter`). **These browser runs are manual evidence and are not part of CI** (no browser runner is available as a repository dependency).

### Missing server capabilities (backend contract proposals; nothing was invented or simulated)
1. **Version negotiation:** `GET /api/v1/offline/version` -> `{contract:"1.0.0", min:"1.0.0"}`; clients refuse unknown majors. Needs a new M38 minor version.
2. **Ownership leases/fencing for devices:** `POST /leases` (record, epoch) -> lease id + expiry, `POST /leases/{id}/renewal|release`, commands carry the lease id; the server fences expired leases. Needs schema (leases table, trigger), RLS, tests.
3. **Remote retention/lock policy:** `GET /device-policy` (per scope: queue/terminal retention, logout mode, session limits), versioned and audited; the SDK already takes these values as configuration.
4. **Attachments:** see section 5.
5. **Token revocation/introspection:** P03 states its endpoint is not an introspection service; a revocation check belongs to P03.

### Operational notes (errors and recovery)
* Banner "identity cannot be verified" -> the verifier is not integrated or the issuer is unreachable; nothing is stored or sent; resolve the identity configuration, then reload.
* `device_user_mismatch` / `device_not_active` at binding -> the device is bound to someone else or revoked; ask an administrator to enroll a new device.
* `session_expired` (401) -> the device locks itself; sign in again, then *Unlock*; queued changes are kept.
* `device_revoked` -> the device wipes itself and cannot resume; its already-uploaded commands remain as server evidence.
* `retry_budget_spent` -> 12 failed attempts; check connectivity and resume. `protocol_error` -> the server answered outside the contract; stop and escalate.
* A wiped device loses unsent changes by design; the UI warns before wiping.

## 7. Verification evidence
* `node --test packages/offline-device-sdk/tests/` -> **48 pass, 0 fail, 16 skipped** (core 13, sync 19, interop 7, security 4, scheduler 3, idb 3; the 16 skipped are the 11 live-rig and 5 proxy-e2e tests, which run only with the rig).
* **Proxy end-to-end** (`tests/e2e/launch.mjs`, real Next route handlers + real service + PostgreSQL 18): **6 pass**. `node --test tests/m38_identity.test.mjs` -> **4 pass**.
* **Live rig** (`SYNQORIA_LIVE_RIG=1`, real service jar + PostgreSQL 18 + HTTPS OIDC issuer + openssl/keytool): **11 pass, 0 fail**; the CI jobs `live-rig` and `proxy-e2e` run them on every push.
* `tsc -p packages/offline-device-sdk/tsconfig.json` (TypeScript 5.8.3, strict, `erasableSyntaxOnly`) -> no errors. Web DevicePanel/device.ts/session route type-check under the web tsconfig flags (css-module declaration supplied by Next).
* `python tests/test_m38_device_sdk_contract.py` -> 9 routes, contract 1.0.0; negative run (renamed route and field) fails as intended.
* `node --test tests/m38_offline_frontend.test.mjs` -> 9/9 (updated for the device routes and the 1 MiB bound).
* `mvn test -Dtest=SdkInteropFixtureTest` (Java 25, PostgreSQL 18.6) -> passes; fixture recorded from the real service.
* GitHub Actions for the exact pushed SHA: see the PR.

## 8. Real limitations
Browser evidence is manual (embedded Chromium), not CI; no emulator/real device, service worker/background sync or mobile-shell runs; no attachment transfer; no Expo adapters; the proxy e2e and the UI stack use a local test issuer and a local verifier adapter, not P03; `next build` and web `tsc` fail in CI for reasons that pre-date this branch (duplicate React types in the shared module shells, missing `@synqoria/ux-core@0.57.0` for the M27 lane); remote retention policy absent; no accessibility audit.

## 9. Integration dependencies for the integration lead
1. Approve storage dependencies if native shells are wanted (`expo-sqlite`, `expo-secure-store`) - adapters then implement `Store`/`KeyVault`.
2. Provide a device registration path (administrative UI or API) and decide who may register devices for whom.
3. Token revocation/introspection remains a P03 decision.
4. A browser-level end-to-end rig (Next proxy + cookie sign-in + a real browser engine) to turn packages 10/22 from **P** to **V**; the service-level live rig already exists (`tests/live`).
5. Domain adapters in the owning clinical modules that consume accepted commands (already an open M38 server dependency).
