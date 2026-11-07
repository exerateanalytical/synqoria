# ADR-M38-DC: Offline device client - PWA on the existing web app, platform-neutral SDK

Status: proposed (engineering candidate, 2026-11-07). Supersedes nothing; extends ADR-M38-offline-sync (the server side).

## Context
The repository contains three UI stacks: the Next.js 16 / React 19 web app (`apps/web`), and two Expo 54 / React Native 0.81 shells (`apps/staff-mobile`, `apps/patient-mobile`). The staff shell has no storage dependency (no SQLite, no secure-store, no async-storage) and adding one changes `package.json` and the lockfile, which this work must not do. The web app already owns the hospital sign-in session (P03/OIDC, HttpOnly cookie), the same-origin proxy with CSRF/Origin enforcement, EN/FR dictionaries and the design system.

## Decision
1. **The first device client is the installed PWA inside the existing web app.** It reuses the sign-in, the proxy, the dictionaries and the screens. No second framework is introduced.
2. **All device logic lives in a platform-neutral TypeScript SDK** (`packages/offline-device-sdk`) that depends only on four interfaces: `Store` (transactional key/value), `KeyVault` (non-extractable key persistence), `fetch`, and a clock/RNG. It has zero runtime dependencies and is not a workspace member, so no lockfile change.
3. **Persistence:** IndexedDB (`IdbStore`) for the PWA. Everything is sealed with AES-256-GCM (WebCrypto) before it reaches the store; the key is non-extractable and held as a `CryptoKey` in its own IndexedDB database. React Native / Expo adapters (SQLite + SecureStore) are *possible* behind the same interfaces but are **not built**: they need new dependencies that belong to the integration lead.
4. **Authority:** the server adjudicates. The SDK admits only commands whose approved contract says offline is allowed and not safety-gated, and then stores them; it never applies clinical state locally. Unknown, unapproved and safety-gated operations are refused on the device. The server re-checks everything.
5. **Identity:** the device never holds a token. Requests ride the HttpOnly cookie through the existing proxy. A tiny session route returns only user id, hospital scope and expiry so the device can refuse to mix users or hospital scopes in one queue.
6. **Browser reachability:** the proxy now admits `POST sync/batches` and `GET devices/{id}/cursor` (the PWA *is* the device), still as the signed-in user whose token the server binds to the device. A full 50-command batch needs a 1 MiB body bound on that one route (other routes stay at 32 KiB).

## Alternatives considered
* **React Native first:** better background execution and secure hardware storage, but needs unapproved dependencies and a second sync implementation to keep in step. Deferred behind the same SDK.
* **Service-worker background sync only:** unreliable on iOS and opaque to testing; the SDK's explicit engine is testable and can be called from a service worker later.
* **Local SQL (sqlite-wasm):** heavier; a transactional key/value store is enough for an append-only queue.

## Consequences
* The same SDK can be reused by `apps/staff-mobile` once storage adapters are approved.
* Browser storage can be wiped by the user or the platform; the server (not the device) remains the system of record, and the UI says so.
* Background sync while the app is closed is **not** provided.
